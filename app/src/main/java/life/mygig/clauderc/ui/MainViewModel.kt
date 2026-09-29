package life.mygig.clauderc.ui

import android.app.Application
import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import life.mygig.clauderc.api.ApiException
import life.mygig.clauderc.api.Codes
import life.mygig.clauderc.api.LauncherApi
import life.mygig.clauderc.api.Latest
import life.mygig.clauderc.api.Updates
import life.mygig.clauderc.api.LoginUrl
import life.mygig.clauderc.api.NewResult
import life.mygig.clauderc.api.OwnersData
import life.mygig.clauderc.api.McpData
import life.mygig.clauderc.api.McpServer
import life.mygig.clauderc.api.PluginsData
import life.mygig.clauderc.api.ChatData
import life.mygig.clauderc.api.PinStatus
import life.mygig.clauderc.api.Repo
import life.mygig.clauderc.api.ServiceDef
import life.mygig.clauderc.api.RunResult
import life.mygig.clauderc.api.Session
import life.mygig.clauderc.api.StatusData
import life.mygig.clauderc.api.TailResult
import life.mygig.clauderc.data.AppSettings
import life.mygig.clauderc.data.SettingsStore
import life.mygig.clauderc.data.ThemeMode
import life.mygig.clauderc.ssh.HostKeyInfo
import life.mygig.clauderc.ssh.SshKeyManager
import life.mygig.clauderc.ssh.SshRunner

enum class Tab { STATUS, NEW, PROJECTS, SESSIONS, COMMAND }
enum class LoginKind { CLAUDE, GITHUB, AWS, GITLAB, DOCKER, YOUTUBE }

/** What a snackbar's button does. */
sealed interface Fix {
    data class Login(val kind: LoginKind) : Fix
    data class GoTo(val tab: Tab) : Fix
    data class StartAnyway(val project: String) : Fix
    data object OpenSettings : Fix
    data object OpenClaude : Fix
    data class Retry(val block: () -> Unit) : Fix
}

data class UiMessage(val text: String, val actionLabel: String? = null, val fix: Fix? = null)

private const val APK_MIME = "application/vnd.android.package-archive"

/** What a Connections tile opens. */
sealed interface Detail {
    /** A service from the + catalog (Cloudflare, Vercel, MXroute, …). */
    data class Service(val id: String) : Detail
    /** Claude, GitHub, AWS, GitLab, Docker, YouTube. */
    data class Login(val kind: LoginKind) : Detail
    data class Mcp(val server: McpServer) : Detail
    data class Keystore(val name: String) : Detail
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val store = SettingsStore(app)
    val keys = SshKeyManager(app)
    private val json = Json { ignoreUnknownKeys = true }
    private val api = LauncherApi(config = { store.current().toServerConfig() }, identity = { keys.identity() })

    val settings: StateFlow<AppSettings?> =
        store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** App lock: false until the user unlocks; set back to false after time in the background. */
    private val _unlocked = MutableStateFlow(false)
    val unlocked = _unlocked.asStateFlow()
    fun lockApp() { _unlocked.value = false }
    fun markUnlocked() { _unlocked.value = true }

    // Every running action has an entry, so one finishing doesn't clear another's spinner.
    private val actionIds = AtomicLong()
    private val running = MutableStateFlow<Map<Long, String>>(emptyMap())
    val busy: StateFlow<String?> =
        running.map { it.values.lastOrNull() }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val _status = MutableStateFlow<StatusData?>(null)
    val status = _status.asStateFlow()
    private val _statusRefreshing = MutableStateFlow(false)
    val statusRefreshing = _statusRefreshing.asStateFlow()

    private val _repos = MutableStateFlow<List<Repo>>(emptyList())
    val repos = _repos.asStateFlow()
    private val _reposRefreshing = MutableStateFlow(false)
    val reposRefreshing = _reposRefreshing.asStateFlow()

    private val _owners = MutableStateFlow<OwnersData?>(null)
    val owners = _owners.asStateFlow()
    private val _ownersError = MutableStateFlow<String?>(null)
    val ownersError = _ownersError.asStateFlow()

    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions = _sessions.asStateFlow()
    private val _sessionsRefreshing = MutableStateFlow(false)
    val sessionsRefreshing = _sessionsRefreshing.asStateFlow()

    private val _newResult = MutableStateFlow<NewResult?>(null)
    val newResult = _newResult.asStateFlow()

    private val _tail = MutableStateFlow<TailResult?>(null)
    val tail = _tail.asStateFlow()

    private val _login = MutableStateFlow<LoginKind?>(null)
    val login = _login.asStateFlow()
    private val _loginUrl = MutableStateFlow<LoginUrl?>(null)
    val loginUrl = _loginUrl.asStateFlow()
    /** An error from a login call, shown inside the open login dialog. */
    private val _loginError = MutableStateFlow<String?>(null)
    val loginError = _loginError.asStateFlow()
    private var ssoPoller: Job? = null

    private val _publicKey = MutableStateFlow("")
    val publicKey = _publicKey.asStateFlow()

    private val _tab = MutableStateFlow(Tab.SESSIONS)
    val tab = _tab.asStateFlow()
    private val _showSettings = MutableStateFlow(false)
    val showSettings = _showSettings.asStateFlow()

    private val clonePollers = mutableMapOf<String, Job>()

    /** Newest server scripts and app build on GitHub; checked at start and hourly. */
    private val _latest = MutableStateFlow<Latest?>(null)
    val latest = _latest.asStateFlow()

    init {
        viewModelScope.launch {
            _publicKey.value = try {
                withContext(Dispatchers.Default) { keys.publicKey() }
            } catch (e: Exception) {
                say("This phone's SSH key couldn't be created (${e.javaClass.simpleName}: ${e.message}). Tap Regenerate in Settings.", "Settings", Fix.OpenSettings)
                ""
            }
            store.reposCache()?.let { cached ->
                runCatching { json.decodeFromString<List<Repo>>(cached) }.getOrNull()?.let { _repos.value = it }
            }
            store.ownersCache()?.let { cached ->
                runCatching { json.decodeFromString<OwnersData>(cached) }.getOrNull()?.let { _owners.value = it }
            }
            if (store.current().isConfigured) refreshStatus()
        }
        viewModelScope.launch {
            while (true) {
                checkUpdates()
                delay(60 * 60 * 1000L)
            }
        }
        viewModelScope.launch {
            delay(3_000)   // let the first status call land
            while (true) {
                refreshMcp()
                delay(5 * 60 * 1000L)
            }
        }
    }

    /** Newest app build and server scripts on GitHub. Offline or rate-limited: keep the last answer. */
    suspend fun checkUpdates() {
        withContext(Dispatchers.IO) { runCatching { Updates.fetch() }.getOrNull() }?.let { _latest.value = it }
    }

    /** Pull-to-refresh on Status: the server's status and the update check together. */
    fun refreshAll() {
        refreshStatus()
        refreshMcp()
        viewModelScope.launch { checkUpdates() }
    }

    /** Run a command: what ran and what it printed, newest last (this app session only). */
    private val _runHistory = MutableStateFlow<List<Pair<String, RunResult>>>(emptyList())
    val runHistory = _runHistory.asStateFlow()
    private val _showRun = MutableStateFlow(false)
    val showRun = _showRun.asStateFlow()
    fun showRun(show: Boolean) { _showRun.value = show }

    fun runCommand(command: String, sudoPassword: String) = action("Running on the server…") {
        val r = api.run(command, sudoPassword)
        _runHistory.value = (_runHistory.value + (command.trim() to r)).takeLast(20)
    }

    // ---- Connections: MCP servers, plugins, tile details ---------------------------

    private val _mcp = MutableStateFlow<McpData?>(null)
    val mcp = _mcp.asStateFlow()

    /** MCP servers from the server's cache; [check] runs a fresh health check (~10 s). */
    fun refreshMcp(check: Boolean = false) {
        if ((_status.value?.scriptApi ?: 0) < Updates.CONNECTIONS_API) return
        if (check) {
            action("Checking MCP servers…") { _mcp.value = api.mcpRefresh() }
        } else {
            viewModelScope.launch {
                runCatching { api.mcp() }.getOrNull()?.let { m ->
                    _mcp.value = m
                    // The server started a background check: pick up its result shortly.
                    if (m.refreshing) { delay(20_000); runCatching { api.mcp() }.getOrNull()?.let { _mcp.value = it } }
                }
            }
        }
    }

    private val _plugins = MutableStateFlow<PluginsData?>(null)
    val plugins = _plugins.asStateFlow()
    private val _pluginsLoading = MutableStateFlow(false)
    val pluginsLoading = _pluginsLoading.asStateFlow()

    fun loadPlugins() {
        if (_pluginsLoading.value || (_status.value?.scriptApi ?: 0) < Updates.CONNECTIONS_API) return
        viewModelScope.launch {
            _pluginsLoading.value = true
            try { _plugins.value = api.plugins() } catch (e: ApiException) { report(e) } finally { _pluginsLoading.value = false }
        }
    }

    // ---- In-app chat ------------------------------------------------------------------
    // The PIN is kept only in memory while the chat is unlocked, and forgotten the
    // moment the app goes to the background (lockChat, from MainActivity.onStop).

    private val _chatSession = MutableStateFlow<String?>(null)
    val chatSession = _chatSession.asStateFlow()
    private val _chatPinNeeded = MutableStateFlow<PinStatus?>(null)
    val chatPinNeeded = _chatPinNeeded.asStateFlow()
    private val _chatError = MutableStateFlow<String?>(null)
    val chatError = _chatError.asStateFlow()
    private val _chat = MutableStateFlow<ChatData?>(null)
    val chat = _chat.asStateFlow()
    private var chatPin: String? = null
    /** Messages you sent that the conversation file doesn't show yet (Claude queues them while it works). */
    private val _chatPending = MutableStateFlow<List<String>>(emptyList())
    val chatPending = _chatPending.asStateFlow()
    private var chatPoller: Job? = null

    fun openChat(session: String) {
        _chatSession.value = session
        _chat.value = null
        _chatError.value = null
        if (chatPin != null) { startChatPolling(); return }
        viewModelScope.launch {
            _chatPinNeeded.value = runCatching { api.chatPinStatus() }.getOrElse { e ->
                _chatError.value = (e as? ApiException)?.let { friendly(it).first } ?: e.message
                PinStatus(set = true)
            }
        }
    }

    fun closeChat() {
        chatPoller?.cancel()
        _chatPending.value = emptyList()
        _chatSession.value = null
        _chat.value = null
        _chatPinNeeded.value = null
    }

    /** App went to the background: forget the PIN; the chat asks again on return. */
    fun lockChat() {
        chatPin = null
        chatPoller?.cancel()
        if (_chatSession.value != null) { _chat.value = null; _chatPinNeeded.value = PinStatus(set = true) }
    }

    fun setChatPin(newPin: String) = viewModelScope.launch {
        _chatError.value = null
        try {
            api.chatPinSet(newPin)
            unlockChat(newPin)
        } catch (e: ApiException) { _chatError.value = friendly(e).first }
    }

    fun unlockChat(pin: String) = viewModelScope.launch {
        val session = _chatSession.value ?: return@launch
        _chatError.value = null
        try {
            api.chatOpen(session, pin)
            chatPin = pin
            _chatPinNeeded.value = null
            startChatPolling()
        } catch (e: ApiException) { _chatError.value = friendly(e).first }
    }

    private fun startChatPolling() {
        chatPoller?.cancel()
        chatPoller = viewModelScope.launch {
            while (true) {
                val session = _chatSession.value ?: break
                val pin = chatPin ?: break
                try {
                    setChat(api.chatHistory(session, pin))
                    _chatError.value = null
                } catch (e: ApiException) {
                    if (e.code in setOf("wrong_pin", "chat_locked", "pin_not_set")) { lockChat(); _chatError.value = friendly(e).first; break }
                    _chatError.value = friendly(e).first
                }
                delay(if (_chat.value?.busy == true) 2_000 else 4_000)
            }
        }
    }

    fun sendChat(text: String, onSent: () -> Unit) {
        val session = _chatSession.value ?: return
        val pin = chatPin ?: return
        action("Sending…") {
            api.chatSend(session, pin, text)
            onSent()
            _chatPending.value = _chatPending.value + text.trim()
            delay(800)
            setChat(api.chatHistory(session, pin))
        }
    }

    /** New history; drop pending messages the conversation now shows. */
    private fun setChat(c: ChatData) {
        _chat.value = c
        val shown = c.messages.filter { it.role == "user" }.map { it.text.trim() }.toSet()
        _chatPending.value = _chatPending.value.filterNot { it in shown }
    }

    fun interruptChat() {
        val session = _chatSession.value ?: return
        val pin = chatPin ?: return
        action("Stopping…") { api.chatInterrupt(session, pin) }
    }

    /** Answer a question in the chat's session (keys, like the log's). */
    fun chatKey(key: String) {
        val session = _chatSession.value ?: return
        action("Sending $key…") { api.keys(session, key); chatPin?.let { _chat.value = api.chatHistory(session, it) } }
    }

    /** The tile detail page that's open, if any. */
    private val _detail = MutableStateFlow<Detail?>(null)
    val detail = _detail.asStateFlow()
    fun openDetail(d: Detail?) { _detail.value = d }

    fun disconnect(id: String, name: String) = action("Disconnecting $name…") {
        api.disconnect(id)
        _detail.value = null
        say("$name disconnected. Restart sessions to drop it.")
        refreshStatus()
    }

    fun removeMcp(name: String) = action("Removing $name…") {
        val r = api.claudeCmd("mcp remove $name -s user")
        if (r.exitCode != 0) throw ApiException(Codes.INTERNAL, r.output.ifBlank { "claude mcp remove failed" })
        _detail.value = null
        say("$name removed. Restart sessions to drop it.")
        _mcp.value = api.mcpRefresh()
    }

    /** A restart that was refused because Claude is still working: ask before forcing it. */
    private val _busyRestart = MutableStateFlow<String?>(null)
    val busyRestart = _busyRestart.asStateFlow()
    fun dismissBusyRestart() { _busyRestart.value = null }

    /** Safe restart: resumes the same conversation; refuses while Claude works unless [force]. */
    fun restartSession(name: String, force: Boolean = false) {
        _busyRestart.value = null
        action("Restarting $name…", onError = { e ->
            if (e.code == Codes.SESSION_BUSY) _busyRestart.value = name else report(e)
        }) {
            val r = api.restart(name, force)
            val resumed = r["resumed"]?.jsonPrimitive?.booleanOrNull == true
            say(if (resumed) "$name restarted with the same conversation" else "$name restarted (new conversation)")
            showIfWaiting(r)
            refreshSessions()
        }
    }

    /** A restarted session stopped on a question (e.g. approve a new MCP server): open it with the answer keys. */
    private fun showIfWaiting(r: JsonObject): Boolean {
        if (r["waiting"]?.jsonPrimitive?.booleanOrNull != true) return false
        val session = r["session"]?.jsonPrimitive?.contentOrNull ?: return false
        tailTarget = session
        _tail.value = TailResult(session, 120, r["text"]?.jsonPrimitive?.contentOrNull.orEmpty())
        _tailUpdatedAt.value = System.currentTimeMillis()
        say("$session is waiting for an answer")
        return true
    }

    /** Safe-restarts every session one by one; skips those where Claude is still working. */
    fun restartAll() = action("Restarting all sessions…") {
        val names = api.sessions().sessions.map { it.name }
        val busy = mutableListOf<String>()
        var done = 0
        var waitingShown = false
        for (n in names) {
            try {
                val r = api.restart(n)
                done++
                if (!waitingShown) waitingShown = showIfWaiting(r)
            } catch (e: ApiException) {
                if (e.code == Codes.SESSION_BUSY) busy += n else throw e
            }
        }
        if (!waitingShown) {
            say("Restarted $done session" + (if (done == 1) "" else "s") +
                if (busy.isEmpty()) "" else ". Still working, skipped: ${busy.joinToString(", ")}")
        }
        refreshSessions()
    }

    /** Command Center: what ran and what it printed. */
    private val _ccResult = MutableStateFlow<Pair<String, RunResult>?>(null)
    val ccResult = _ccResult.asStateFlow()

    fun claudeCommand(args: String) = action("Running claude ${args.trim()}…") {
        _ccResult.value = args.trim() to api.claudeCmd(args)
        // Plugin and marketplace changes: show the new list.
        if (args.trim().startsWith("plugin")) {
            runCatching { api.plugins() }.getOrNull()?.let { _plugins.value = it }
        }
    }

    fun clearCcResult() { _ccResult.value = null }

    /** The + dialog: services that aren't connected yet. */
    private val _showAdd = MutableStateFlow(false)
    val showAdd = _showAdd.asStateFlow()
    fun showAddService(show: Boolean) { _showAdd.value = show }

    /**
     * One tap from the + dialog: install the CLI if it's missing and we can,
     * then open the login form.
     */
    fun addService(kind: LoginKind) {
        _showAdd.value = false
        val st = _status.value
        val svc = when (kind) {
            LoginKind.GITLAB -> st?.services?.get("gitlab")
            LoginKind.DOCKER -> st?.services?.get("docker")
            else -> null
        }
        when {
            svc == null || svc.installed -> showLogin(kind)
            kind == LoginKind.GITLAB && (st?.scriptApi ?: 0) >= Updates.INSTALL_CLI_API -> installGlab()
            else -> showSetup(kind)
        }
    }

    /** A service whose CLI isn't on the server yet: the setup guide for it. */
    private val _setup = MutableStateFlow<LoginKind?>(null)
    val setup = _setup.asStateFlow()
    fun showSetup(kind: LoginKind?) { _setup.value = kind }

    fun installGlab() = installCli("glab") { _setup.value = null; _login.value = LoginKind.GITLAB }

    /** Installs [name] on the server, then runs [then] (usually: open the login form). */
    fun installCli(name: String, sudoPassword: String = "", then: () -> Unit = {}) = action("Installing $name on the server…") {
        val r = api.installCli(name, sudoPassword)
        say(r["note"]?.jsonPrimitive?.contentOrNull ?: "$name installed")
        refreshStatus()
        then()
    }

    /** The Android signing keys dialog. */
    private val _showKeystores = MutableStateFlow(false)
    val showKeystores = _showKeystores.asStateFlow()
    fun showKeystores(show: Boolean) { _showAdd.value = false; _showKeystores.value = show }

    fun saveKeystore(name: String, alias: String, storePassword: String, keyPassword: String, file: ByteArray) =
        action("Checking and saving the $name signing key…") {
            val b64 = android.util.Base64.encodeToString(file, android.util.Base64.NO_WRAP)
            api.loginKeystore(name, alias, storePassword, keyPassword.ifEmpty { storePassword }, b64)
            say("$name signing key saved. Restart sessions to use it.")
            refreshStatus()
        }

    fun removeKeystore(name: String) = action("Removing the $name signing key…") {
        api.removeKeystore(name)
        say("$name signing key removed.")
        refreshStatus()
    }

    /** The custom API keys dialog. */
    private val _showCustom = MutableStateFlow(false)
    val showCustom = _showCustom.asStateFlow()
    fun showCustomKeys(show: Boolean) { _showAdd.value = false; _showCustom.value = show }

    fun setSecret(name: String, value: String) = action("Saving $name…") {
        api.setSecret(name, value)
        say("$name saved. Restart sessions to use it.")
        refreshStatus()
    }

    fun removeSecret(name: String) = action("Removing $name…") {
        api.removeSecret(name)
        say("$name removed. Restart sessions to drop it.")
        refreshStatus()
    }

    /** The connect form for a token service (see Catalog), by id. */
    private val _tokenService = MutableStateFlow<String?>(null)
    val tokenService = _tokenService.asStateFlow()
    private val _tokenError = MutableStateFlow<String?>(null)
    val tokenError = _tokenError.asStateFlow()
    fun showTokenService(id: String?) { _tokenError.value = null; _tokenService.value = id }

    /** One tap from +: install the CLI if the server can and it's missing, then open the form. */
    fun addTokenService(def: ServiceDef) {
        _showAdd.value = false
        val st = _status.value
        val missing = st?.services?.get(def.id)?.installed == false
        if (def.install != null && missing && (st?.scriptApi ?: 0) >= Updates.TOKEN_SERVICES_API) {
            installCli(def.install) { showTokenService(def.id) }
        } else {
            showTokenService(def.id)
        }
    }

    fun tokenLogin(def: ServiceDef, values: List<String>) {
        _tokenError.value = null
        action("Checking with ${def.name}…", onError = { e ->
            if (_tokenService.value == def.id) _tokenError.value = friendly(e).first else report(e)
        }) {
            val r = api.loginToken(def.id, values)
            if (_tokenService.value == def.id) _tokenService.value = null
            say("${def.name} connected" + (r.user?.let { ": $it" } ?: "") + ". Restart running sessions to use it.")
            refreshStatus()
        }
    }

    /**
     * Downloads the new APK with Android's download manager, then opens the
     * installer. (The file lands in the app's own folder, not Downloads, so
     * the user would never find it by hand.)
     */
    fun downloadAndInstall(url: String): Job = viewModelScope.launch {
        val ctx = getApplication<Application>()
        val dm = ctx.getSystemService(DownloadManager::class.java)
        val name = url.substringAfterLast('/')
        // Only ever keep the build being downloaded.
        ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?.listFiles { f -> f.name.endsWith(".apk") }?.forEach { it.delete() }
        val id = try {
            dm.enqueue(
                DownloadManager.Request(Uri.parse(url))
                    .setTitle(name)
                    .setMimeType(APK_MIME)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setDestinationInExternalFilesDir(ctx, Environment.DIRECTORY_DOWNLOADS, name),
            )
        } catch (e: Exception) {
            say("Couldn't start the download: ${e.message}")
            return@launch
        }
        say("Downloading $name…")
        while (true) {
            delay(1_000)
            val status = dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
                if (c.moveToFirst()) c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) else null
            }
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val uri = dm.getUriForDownloadedFile(id) ?: break
                    try {
                        ctx.startActivity(
                            Intent(Intent.ACTION_VIEW).setDataAndType(uri, APK_MIME)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    } catch (e: Exception) {
                        say("Downloaded, but the installer didn't open: ${e.message}")
                    }
                    return@launch
                }
                DownloadManager.STATUS_FAILED, null -> break
            }
        }
        say("The download failed.", "Retry", Fix.Retry { downloadAndInstall(url) })
    }

    fun updateServerScripts(commit: String) = action("Updating the server scripts…") {
        api.selfUpdate(commit)
        say("Server scripts updated")
        refreshStatus()
    }

    fun selectTab(t: Tab) { _tab.value = t }
    fun openSettings(show: Boolean) { _showSettings.value = show }

    fun say(text: String, label: String? = null, fix: Fix? = null) {
        _messages.trySend(UiMessage(text, label, fix))
    }

    /** Runs a server call with the spinner on and maps any failure to a friendly message. */
    private fun action(label: String, onError: ((ApiException) -> Unit)? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            val id = actionIds.incrementAndGet()
            running.value = running.value + (id to label)
            try {
                block()
            } catch (e: ApiException) {
                if (onError != null) onError(e) else report(e)
            } finally {
                running.value = running.value - id
            }
        }
    }

    private fun report(e: ApiException, retry: (() -> Unit)? = null) {
        val (text, label, fix) = friendly(e)
        if (fix == null && retry != null && e.code in setOf(Codes.NETWORK, Codes.TIMEOUT, Codes.BUSY)) {
            say(text, "Retry", Fix.Retry(retry))
        } else {
            say(text, label, fix)
        }
    }

    // ---- Status ---------------------------------------------------------------

    fun refreshStatus() {
        if (_statusRefreshing.value) return
        viewModelScope.launch {
            _statusRefreshing.value = true
            try {
                _status.value = api.status()
            } catch (e: ApiException) {
                report(e) { refreshStatus() }
            } finally {
                _statusRefreshing.value = false
            }
        }
    }

    fun testConnection() = action("Testing connection…") {
        val s = api.status()
        _status.value = s
        say("Connected to ${s.hostname.ifBlank { "the server" }}")
    }

    // ---- Projects -------------------------------------------------------------

    fun refreshRepos(force: Boolean) {
        if (_reposRefreshing.value) return
        viewModelScope.launch {
            _reposRefreshing.value = true
            try {
                val r = api.repos(force).repos
                _repos.value = r
                store.setReposCache(json.encodeToString(r))
            } catch (e: ApiException) {
                report(e) { refreshRepos(force) }
            } finally {
                _reposRefreshing.value = false
            }
        }
    }

    fun loadOwners() {
        viewModelScope.launch {
            _ownersError.value = null
            try {
                val o = api.owners()
                _owners.value = o
                store.setOwnersCache(json.encodeToString(o))
            } catch (e: ApiException) {
                _ownersError.value = friendly(e).first
            }
        }
    }

    fun openRepo(repo: Repo, start: Boolean) = action(
        if (repo.local) "Updating ${repo.name}…" else "Cloning ${repo.name}…",
        onError = { e ->
            // "Start anyway" only makes sense if they asked to start.
            if (e.code == Codes.FOLDER_DIRTY && !start) {
                say("The folder has uncommitted changes, so it wasn't updated.")
            } else {
                report(e)
            }
            refreshRepos(false)
        },
    ) {
        val r = api.open(repo.fullName, start)
        val what = when (r.action) {
            "cloned" -> "Cloned"
            "pulled" -> "Pulled latest for"
            "not_updated" -> "Couldn't fast-forward (check it manually):"
            "cloning" -> "Still cloning"
            else -> "Ready:"
        }
        if (r.pending) {
            say("$what ${repo.name} in the background. You'll get a message when it's done.")
            watchClone(repo.fullName, repo.name)
        } else {
            say(
                "$what ${repo.name}" + (r.session?.let { " — Claude started" } ?: ""),
                if (r.session != null) "Open in Claude" else null,
                if (r.session != null) Fix.OpenClaude else null,
            )
        }
        refreshRepos(false)
    }

    /** Follow a clone that outlived the SSH call until it finishes or fails. */
    private fun watchClone(fullName: String, name: String) {
        clonePollers[fullName]?.cancel()
        clonePollers[fullName] = viewModelScope.launch {
            repeat(180) {
                delay(5_000)
                val c = runCatching { api.cloneStatus(fullName) }.getOrNull() ?: return@repeat
                when (c.state) {
                    "done" -> {
                        say(
                            "Cloned $name" + (c.session?.let { " — Claude started" } ?: ""),
                            if (c.session != null) "Open in Claude" else null,
                            if (c.session != null) Fix.OpenClaude else null,
                        )
                        refreshRepos(false); refreshSessions()
                        return@launch
                    }
                    "failed", "none" -> {
                        say("Clone of $name failed" + (c.message?.let { ": $it" } ?: "."))
                        refreshRepos(false)
                        return@launch
                    }
                }
            }
        }
    }

    fun startProject(project: String) = action("Starting $project…") {
        val r = api.start(project)
        say(
            if (r.alreadyRunning) "$project is already running" else "Claude started for $project",
            "Open in Claude",
            Fix.OpenClaude,
        )
        refreshSessions(); refreshRepos(false)
    }

    /** [target] is a project or tmux session name; the server accepts either. */
    fun stopProject(target: String) = action("Stopping $target…") {
        val r = api.stop(target)
        say(if (r.stopped) "Stopped $target" else "$target wasn't running")
        refreshSessions(); refreshRepos(false)
    }

    private var tailTarget: String? = null
    private val _tailUpdatedAt = MutableStateFlow<Long?>(null)
    val tailUpdatedAt = _tailUpdatedAt.asStateFlow()
    private val _tailRefreshing = MutableStateFlow(false)
    val tailRefreshing = _tailRefreshing.asStateFlow()
    private var tailJob: Job? = null

    /** Answers a prompt in the session shown in Tail, then shows the new screen. */
    fun sendKey(key: String) {
        val target = tailTarget ?: return
        action("Sending $key…") {
            val r = api.keys(target, key)
            if (tailTarget == target) {
                _tail.value = r
                _tailUpdatedAt.value = System.currentTimeMillis()
            }
        }
    }

    fun loadTail(target: String) = action("Reading log…") {
        tailTarget = target
        _tail.value = api.tail(target, 120)
        _tailUpdatedAt.value = System.currentTimeMillis()
    }

    /**
     * Re-reads the open log in place: progress shows in the log window itself,
     * not the app-wide bar behind it. Errors only show if [manual].
     */
    fun refreshTail(manual: Boolean = true) {
        val target = tailTarget ?: return
        if (_tailRefreshing.value) return
        tailJob = viewModelScope.launch {
            _tailRefreshing.value = true
            try {
                val r = api.tail(target, 120)
                if (tailTarget == target && _tail.value != null) {
                    _tail.value = r
                    _tailUpdatedAt.value = System.currentTimeMillis()
                }
            } catch (e: ApiException) {
                if (manual) report(e)
            } finally {
                _tailRefreshing.value = false
            }
        }
    }

    fun closeTail() {
        tailJob?.cancel()
        _tail.value = null
        _tailUpdatedAt.value = null
    }

    // ---- New project ------------------------------------------------------------

    fun createProject(name: String, owner: String, private: Boolean, start: Boolean) = action("Creating $name…") {
        _newResult.value = null
        val r = api.newProject(name, owner, private, start)
        _newResult.value = r
        say("Created ${r.repo}" + if (r.session != null) " and started Claude" else "")
        refreshRepos(true)
        if (r.session != null) refreshSessions()
    }

    fun clearNewResult() { _newResult.value = null }

    // ---- Sessions ---------------------------------------------------------------

    fun refreshSessions() {
        if (_sessionsRefreshing.value) return
        viewModelScope.launch {
            _sessionsRefreshing.value = true
            try {
                _sessions.value = api.sessions().sessions
            } catch (e: ApiException) {
                report(e) { refreshSessions() }
            } finally {
                _sessionsRefreshing.value = false
            }
        }
    }

    // ---- Logins -----------------------------------------------------------------

    fun showLogin(kind: LoginKind?) {
        if (_login.value == LoginKind.CLAUDE && _loginUrl.value != null && kind != LoginKind.CLAUDE) {
            viewModelScope.launch { runCatching { api.loginClaudeCancel() } }
        }
        ssoPoller?.cancel()
        _loginUrl.value = null
        _loginError.value = null
        _login.value = kind
    }

    /** Like [action], but errors show inside the login dialog while it's open. */
    private fun loginAction(kind: LoginKind, label: String, block: suspend () -> Unit) {
        _loginError.value = null
        action(label, onError = { e ->
            if (_login.value == kind) {
                _loginError.value = friendly(e).first + (e.pane?.takeIf { it.isNotBlank() }?.let { "\n\n$it" } ?: "")
            } else {
                report(e)
            }
        }, block = block)
    }

    fun claudeLoginStart() = loginAction(LoginKind.CLAUDE, "Starting Claude login…") {
        val u = api.loginClaudeStart()
        if (_login.value == LoginKind.CLAUDE) _loginUrl.value = u
    }

    fun claudeLoginCode(code: String) = loginAction(LoginKind.CLAUDE, "Checking code…") {
        api.loginClaudeCode(code)
        loginDone(LoginKind.CLAUDE, "Claude is logged in")
    }

    fun githubLogin(token: String) = loginAction(LoginKind.GITHUB, "Saving GitHub token…") {
        val r = api.loginGithub(token)
        loginDone(LoginKind.GITHUB, "GitHub logged in as ${r.user}")
        loadOwners(); refreshRepos(true)
    }

    fun awsKeysLogin(id: String, secret: String, region: String) = loginAction(LoginKind.AWS, "Saving AWS keys…") {
        val r = api.loginAwsKeys(id, secret, region)
        loginDone(LoginKind.AWS, "AWS logged in" + (r.account?.let { " (account $it)" } ?: ""))
    }

    fun awsSsoStart() = loginAction(LoginKind.AWS, "Starting AWS SSO…") {
        val u = api.loginAwsSsoStart()
        if (_login.value != LoginKind.AWS) return@loginAction
        _loginUrl.value = u
        // The user approves in the browser; poll status until AWS shows up.
        // Device codes last about 10 minutes.
        ssoPoller?.cancel()
        ssoPoller = viewModelScope.launch {
            repeat(150) {
                delay(4_000)
                if (_login.value != LoginKind.AWS) return@launch
                val s = runCatching { api.status() }.getOrNull() ?: return@repeat
                _status.value = s
                if (s.aws.loggedIn) {
                    loginDone(LoginKind.AWS, "AWS SSO login complete")
                    return@launch
                }
            }
            if (_login.value == LoginKind.AWS) {
                _loginUrl.value = null
                _loginError.value = "The SSO code expired before it was approved. Start the SSO login again."
            }
        }
    }

    fun gitlabLogin(token: String, host: String) = loginAction(LoginKind.GITLAB, "Saving GitLab token…") {
        val r = api.loginGitlab(token, host)
        loginDone(LoginKind.GITLAB, "GitLab logged in as ${r.user}")
    }

    fun dockerLogin(registry: String, user: String, token: String) = loginAction(LoginKind.DOCKER, "Logging in to the registry…") {
        api.loginDocker(registry, user, token)
        loginDone(LoginKind.DOCKER, "Logged in to ${registry.ifBlank { "docker.io" }}")
    }

    /** YouTube: start Google's device sign-in, then poll until you approve it on google.com/device. */
    fun youtubeStart(clientId: String, clientSecret: String) = loginAction(LoginKind.YOUTUBE, "Starting Google sign-in…") {
        val st = api.youtubeStart(clientId, clientSecret)
        if (_login.value != LoginKind.YOUTUBE) return@loginAction
        _loginUrl.value = LoginUrl(st.url, st.code)
        ssoPoller?.cancel()
        ssoPoller = viewModelScope.launch {
            val until = System.currentTimeMillis() + st.expiresIn * 1000L
            while (System.currentTimeMillis() < until && _login.value == LoginKind.YOUTUBE) {
                delay(st.interval.coerceAtLeast(5) * 1000L)
                val r = try {
                    api.youtubePoll()
                } catch (e: ApiException) {
                    if (e.code == Codes.NETWORK || e.code == Codes.TIMEOUT) continue
                    _loginUrl.value = null
                    _loginError.value = friendly(e).first
                    return@launch
                }
                if (r["logged_in"]?.jsonPrimitive?.booleanOrNull == true) {
                    loginDone(LoginKind.YOUTUBE, "YouTube connected: ${r["user"]?.jsonPrimitive?.contentOrNull}. Restart sessions to use it.")
                    return@launch
                }
            }
            if (_login.value == LoginKind.YOUTUBE && _loginUrl.value != null) {
                _loginUrl.value = null
                _loginError.value = "The code expired before it was approved. Start the sign-in again."
            }
        }
    }

    private fun loginDone(kind: LoginKind, text: String) {
        if (_login.value == kind) {
            _login.value = null
            _loginUrl.value = null
            _loginError.value = null
        }
        say(text)
        refreshStatus()
    }

    // ---- Settings ---------------------------------------------------------------

    private val _pendingHostKey = MutableStateFlow<HostKeyInfo?>(null)
    val pendingHostKey = _pendingHostKey.asStateFlow()

    fun saveServerAndProbe(host: String, port: Int, user: String) = action("Contacting $host…") {
        val before = store.current()
        val h = host.trim()
        val u = user.trim()
        store.saveServer(h, port, u)
        if (before.host != h || before.port != port || before.user != u) clearServerState()
        val pinnedType = store.current().hostKeyType.ifBlank { null }
        val info = withContext(Dispatchers.IO) {
            try {
                SshRunner.probeHostKey(h, port, u, preferType = pinnedType)
            } catch (e: Exception) {
                throw ApiException(Codes.NETWORK, e.message ?: "Could not reach the server")
            }
        }
        val s = store.current()
        if (s.hostKeyBlob.isNotEmpty() && s.hostKeyBlob == info.blob) {
            say("Host key matches the pinned one")
            refreshStatus()
        } else {
            _pendingHostKey.value = info
        }
    }

    /** Forget everything shown from the previous server. */
    private fun clearServerState() {
        _status.value = null
        _repos.value = emptyList()
        _owners.value = null
        _ownersError.value = null
        _sessions.value = emptyList()
        _newResult.value = null
        _tail.value = null
        tailTarget = null
        clonePollers.values.forEach { it.cancel() }
        clonePollers.clear()
    }

    fun acceptHostKey(accept: Boolean) {
        val info = _pendingHostKey.value ?: return
        _pendingHostKey.value = null
        if (!accept) return
        viewModelScope.launch {
            store.pinHostKey(info.type, info.blob, info.fingerprint)
            say("Server trusted. Checking connection…")
            refreshStatus()
        }
    }

    fun forgetHostKey() = viewModelScope.launch { store.forgetHostKey() }

    // ---- Servers ------------------------------------------------------------------

    /** Opens the setup screen for a new server. Back (removing the blank one) cancels. */
    fun addServer() = viewModelScope.launch {
        store.addServer()
        clearServerState()
        _showSettings.value = false
    }

    fun switchServer(id: String) = viewModelScope.launch {
        if (id == store.current().activeId) return@launch
        store.switchServer(id)
        clearServerState()
        if (store.current().isConfigured) refreshStatus()
    }

    fun removeServer(id: String) = viewModelScope.launch {
        val wasActive = id == store.current().activeId
        store.removeServer(id)
        if (wasActive) {
            clearServerState()
            if (store.current().isConfigured) refreshStatus()
        }
    }

    /** True after a new key was made this session: Settings then shows the install command. */
    private val _keyRegenerated = MutableStateFlow(false)
    val keyRegenerated = _keyRegenerated.asStateFlow()

    fun regenerateKey() = viewModelScope.launch {
        try {
            _publicKey.value = withContext(Dispatchers.Default) { keys.regenerate() }
            _keyRegenerated.value = true
            say("New key created. Run the install command on each server.")
        } catch (e: Exception) {
            say("Couldn't create a new key: ${e.message}")
        }
    }

    fun setAppLock(on: Boolean) = viewModelScope.launch { store.setAppLock(on) }
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { store.setTheme(mode) }
}
