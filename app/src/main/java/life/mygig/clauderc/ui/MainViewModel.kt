package life.mygig.clauderc.ui

import android.app.Application
import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
import life.mygig.clauderc.api.ChatTask
import life.mygig.clauderc.api.ChatWorker
import life.mygig.clauderc.api.ChatHost
import life.mygig.clauderc.api.Host
import life.mygig.clauderc.api.HostHardenResult
import life.mygig.clauderc.api.HostKey
import life.mygig.clauderc.api.HostProbe
import life.mygig.clauderc.api.ClusterAccount
import life.mygig.clauderc.api.ClusterConfig
import life.mygig.clauderc.data.Server
import life.mygig.clauderc.api.AskQuestion
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
import life.mygig.clauderc.api.ChatCommand
import life.mygig.clauderc.api.ChatData
import life.mygig.clauderc.api.PinStatus
import life.mygig.clauderc.api.ChatLogEntry
import life.mygig.clauderc.api.Repo
import life.mygig.clauderc.notify.Push
import life.mygig.clauderc.notify.SessionWatcher
import life.mygig.clauderc.api.ServiceDef
import life.mygig.clauderc.api.RunResult
import life.mygig.clauderc.api.Session
import life.mygig.clauderc.api.StatusData
import life.mygig.clauderc.api.TailResult
import life.mygig.clauderc.api.Worker
import life.mygig.clauderc.api.WorkerRun
import life.mygig.clauderc.data.AppSettings
import life.mygig.clauderc.data.ChatPinVault
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
    object Apple : Detail
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val store = SettingsStore(app)
    val keys = SshKeyManager(app)
    private val json = Json { ignoreUnknownKeys = true }
    private val api = LauncherApi(config = { store.current().toServerConfig() }, identity = { keys.identity() })
    private val _pushReady = MutableStateFlow(Push.active(app))
    /** This phone is registered for instant alerts (the server has push set up). */
    val pushReady = _pushReady.asStateFlow()

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
    private val _driveStatus = MutableStateFlow<life.mygig.clauderc.api.DriveStatus?>(null)
    val driveStatus = _driveStatus.asStateFlow()
    private val _driveError = MutableStateFlow<String?>(null)
    val driveError = _driveError.asStateFlow()
    private val _driveLogin = MutableStateFlow<life.mygig.clauderc.api.DriveLoginStart?>(null)
    val driveLogin = _driveLogin.asStateFlow()
    private val _driveBackups = MutableStateFlow<List<life.mygig.clauderc.api.DriveBackupEntry>>(emptyList())
    val driveBackups = _driveBackups.asStateFlow()
    private val _driveProgress = MutableStateFlow<life.mygig.clauderc.api.DriveBackupStatus?>(null)
    val driveProgress = _driveProgress.asStateFlow()
    private val _driveRestoreCommand = MutableStateFlow<String?>(null)
    val driveRestoreCommand = _driveRestoreCommand.asStateFlow()
    fun clearDriveRestore() { _driveRestoreCommand.value = null }
    private var drivePoller: Job? = null

    fun driveRefresh() = driveAction("Loading Drive backup…") { _driveStatus.value = api.driveStatus(); if (_driveStatus.value?.connected == true) _driveBackups.value = api.driveList() }
    fun driveLoginStart() = driveAction("Starting Drive sign-in…") {
        val start = api.driveLoginStart(); _driveLogin.value = start
        drivePoller?.cancel(); drivePoller = viewModelScope.launch {
            val until = System.currentTimeMillis() + start.expiresIn * 1000L
            while (System.currentTimeMillis() < until && _driveLogin.value != null) {
                delay(start.interval.coerceAtLeast(5) * 1000L)
                try { if (api.driveLoginPoll()["connected"]?.jsonPrimitive?.booleanOrNull == true) { _driveLogin.value = null; driveRefresh(); return@launch } }
                catch (e: ApiException) { if (e.code != Codes.NETWORK && e.code != Codes.TIMEOUT) { _driveError.value = friendly(e).first; _driveLogin.value = null; return@launch } }
            }
            if (_driveLogin.value != null) { _driveLogin.value = null; _driveError.value = "Drive sign-in code expired. Start again." }
        }
    }
    fun driveSetPassphrase(value: String) = driveAction("Saving backup passphrase…") { api.drivePassphrase(value); _driveStatus.value = api.driveStatus() }
    fun driveBackup() = driveAction("Starting Drive backup…") {
        api.driveBackup(); drivePoller?.cancel(); drivePoller = viewModelScope.launch {
            try {
                do { _driveProgress.value = api.driveBackupStatus(); if (_driveProgress.value?.state == "running") delay(2_000) } while (_driveProgress.value?.state == "running")
                if (_driveProgress.value?.state == "failed") _driveError.value = _driveProgress.value?.message else { _driveError.value = null; _driveBackups.value = api.driveList(); _driveStatus.value = api.driveStatus() }
            } catch (e: ApiException) { _driveError.value = friendly(e).first }
        }
    }
    fun driveSchedule(on: Boolean) = driveAction("Updating Drive schedule…") { api.driveSchedule(on); _driveStatus.value = api.driveStatus() }
    fun driveDelete(id: String) = driveAction("Deleting Drive backup…") { api.driveDelete(id); _driveBackups.value = api.driveList() }
    fun driveRestore(id: String) = driveAction("Preparing Drive restore…") { _driveRestoreCommand.value = api.driveRestore(id)["command"]?.jsonPrimitive?.contentOrNull ?: "Restore command was not returned." }
    fun driveLogout() = driveAction("Disconnecting Drive…") { api.driveLogout(); _driveLogin.value = null; _driveStatus.value = api.driveStatus(); _driveBackups.value = emptyList() }
    private fun driveAction(label: String, block: suspend () -> Unit) = action(label, onError = { _driveError.value = friendly(it).first }) { _driveError.value = null; block() }
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
        // An update's APK is only needed until it's installed (which restarts the app).
        viewModelScope.launch(Dispatchers.IO) { deleteApks() }
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
            if (store.current().notify && SessionWatcher.allowed(getApplication())) { SessionWatcher.schedule(getApplication()); setupPush() }
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
        if ((_status.value?.scriptApi ?: 0) < Updates.MIN_SCRIPT_API) return
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
        if (_pluginsLoading.value || (_status.value?.scriptApi ?: 0) < Updates.MIN_SCRIPT_API) return
        viewModelScope.launch {
            _pluginsLoading.value = true
            try { _plugins.value = api.plugins() } catch (e: ApiException) { report(e) } finally { _pluginsLoading.value = false }
        }
    }

    // ---- In-app chat ------------------------------------------------------------------
    // The PIN is kept only in memory while the chat is unlocked, and forgotten the
    // moment the app goes to the background (lockChat, from MainActivity.onStop),
    // unless you chose "don't ask again for" a while: then it's kept encrypted in
    // ChatPinVault until that runs out or the phone restarts.

    private val _chatSession = MutableStateFlow<String?>(null)
    val chatSession = _chatSession.asStateFlow()
    private val _chatPinNeeded = MutableStateFlow<PinStatus?>(null)
    val chatPinNeeded = _chatPinNeeded.asStateFlow()
    private val _chatError = MutableStateFlow<String?>(null)
    val chatError = _chatError.asStateFlow()
    private val _chat = MutableStateFlow<ChatData?>(null)
    val chat = _chat.asStateFlow()
    private var chatPin: String? = null
    private val pinVault = ChatPinVault(app)
    /** Messages you sent that the conversation file doesn't show yet (Claude queues them while it works). */
    private val _chatCommands = MutableStateFlow<List<ChatCommand>>(emptyList())
    /** Slash commands from your skills, commands and plugins, for the suggestions above the chat box. */
    val chatCommands = _chatCommands.asStateFlow()

    private val _chatPending = MutableStateFlow<List<String>>(emptyList())
    val chatPending = _chatPending.asStateFlow()
    /** Pending messages of chats you left: they come back when you reopen that chat (until the conversation shows them). */
    private val queuedBySession = mutableMapOf<String, List<String>>()

    /** Half-typed chat messages, kept per session so leaving the chat or the app doesn't lose them. */
    private val draftPrefs by lazy { getApplication<Application>().getSharedPreferences("chat_drafts", android.content.Context.MODE_PRIVATE) }
    fun draft(session: String): String = draftPrefs.getString(session, "").orEmpty()
    fun setDraft(session: String, text: String) = draftPrefs.edit().apply { if (text.isEmpty()) remove(session) else putString(session, text) }.apply()
    private var chatPoller: Job? = null

    /** Claude's full checkup in a session of its own, opened in the chat. */
    fun startCheckup() = action("Starting the checkup…") {
        val s = api.doctorStart().session
        refreshSessions()
        openChat(s)
    }

    fun openChat(session: String) {
        _chatSession.value = session
        _chatPending.value = queuedBySession[session].orEmpty()
        _chat.value = null
        _chatCommands.value = emptyList()
        _chatError.value = null
        if (chatPin == null) chatPin = pinVault.load()
        if (chatPin != null) { startChatPolling(); return }
        viewModelScope.launch {
            _chatPinNeeded.value = runCatching { api.chatPinStatus() }.getOrElse { e ->
                _chatError.value = (e as? ApiException)?.let { friendly(it).first } ?: e.message
                PinStatus(set = true)
            }
        }
    }

    /** Files Claude sent, kept in memory while the chat is open (about 24 MB). */
    private val fileCache = object : android.util.LruCache<String, ByteArray>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray) = value.size
    }

    suspend fun chatFileBytes(path: String): ByteArray {
        fileCache.get(path)?.let { return it }
        val session = _chatSession.value ?: throw IllegalStateException("The chat is closed")
        val pin = chatPin ?: throw IllegalStateException("The chat is locked")
        val f = try { api.chatFile(session, pin, path) } catch (e: ApiException) { throw IllegalStateException(friendly(e).first) }
        // Megabytes of text: decode off the main thread.
        return withContext(Dispatchers.Default) { android.util.Base64.decode(f.data, android.util.Base64.DEFAULT) }.also { fileCache.put(path, it) }
    }

    fun closeChat() {
        fileCache.evictAll()
        chatPoller?.cancel()
        _chatSession.value?.let { queuedBySession[it] = _chatPending.value }
        _chatPending.value = emptyList()
        _chatSession.value = null
        _chat.value = null
        _chatPinNeeded.value = null
        _chatWorkers.value = null
        _chatTasks.value = emptyList()
        _chatHosts.value = null
    }

    /** App went to the background: forget the PIN; the chat asks again on return. */
    /** A system screen the chat opened (file picker, Save, voice): leaving for it isn't "leaving the app". */
    private var externalUntil = 0L
    fun externalScreen() { externalUntil = SystemClock.elapsedRealtime() + 120_000 }

    fun lockChat() {
        chatPoller?.cancel()
        if (SystemClock.elapsedRealtime() < externalUntil) return
        if (chatPin != null && pinVault.load() != null) return
        chatPin = null
        if (_chatSession.value != null) { _chat.value = null; _chatPinNeeded.value = PinStatus(set = true) }
    }

    /** The server refused the PIN: drop it and ask again, even inside the picker grace window. */
    private fun forgetChatPin() {
        pinVault.clear()
        chatPin = null
        if (_chatSession.value != null) { _chat.value = null; _chatPinNeeded.value = PinStatus(set = true) }
    }

    /** Back in front while still unlocked: carry on polling. */
    fun resumeChat() {
        externalUntil = 0L   // the grace window covers one trip out (picker, Save), not later ones
        if (chatPin != null && _chatSession.value != null && _chatPinNeeded.value == null) startChatPolling()
    }

    fun setChatPin(newPin: String) = viewModelScope.launch {
        _chatError.value = null
        try {
            api.chatPinSet(newPin)
            unlockChat(newPin)
        } catch (e: ApiException) { _chatError.value = friendly(e).first }
    }

    /** [keepMinutes] > 0: don't ask again for that long, even after leaving the app. */
    fun unlockChat(pin: String, keepMinutes: Int = 0) = viewModelScope.launch {
        val session = _chatSession.value ?: return@launch
        _chatError.value = null
        try {
            api.chatOpen(session, pin)
            chatPin = pin
            if (keepMinutes > 0) pinVault.save(pin, keepMinutes) else pinVault.clear()
            _chatPinNeeded.value = null
            startChatPolling()
        } catch (e: ApiException) { _chatError.value = friendly(e).first }
    }

    private fun startChatPolling() {
        chatPoller?.cancel()
        val s0 = _chatSession.value; val p0 = chatPin
        if (s0 != null && p0 != null) viewModelScope.launch {
            runCatching { api.chatCommands(s0, p0).commands }.getOrNull()?.let { if (_chatSession.value == s0) _chatCommands.value = it }
        }
        chatPoller = viewModelScope.launch {
            while (true) {
                val session = _chatSession.value ?: break
                val pin = chatPin ?: break
                try {
                    setChat(session, api.chatHistory(session, pin))
                    _chatError.value = null
                } catch (e: ApiException) {
                    if (e.code in setOf("wrong_pin", "chat_locked", "pin_not_set")) { forgetChatPin(); _chatError.value = friendly(e).first; break }
                    _chatError.value = friendly(e).first
                }
                delay(if (_chat.value?.busy == true) 2_000 else 4_000)
            }
        }
    }

    /**
     * Answers Claude's question screen key by key: a number picks (and moves on), a multi-choice
     * list is toggled then Down to its Submit row, "something else" types the text.
     */
    fun answerAsk(qs: List<AskQuestion>, picks: List<Set<Int>>, others: List<String>) {
        val session = _chatSession.value ?: return
        val pin = chatPin ?: return
        action("Sending your answers…") {
            val keys = mutableListOf<String>()
            suspend fun flush() {
                keys.chunked(5).forEach { api.keys(session, *it.toTypedArray()) }
                keys.clear()
            }
            qs.forEachIndexed { qi, q ->
                val n = q.options.size
                val other = others[qi].trim()
                when {
                    q.multiSelect -> {
                        picks[qi].sorted().forEach { keys += (it + 1).toString() }
                        repeat(n + 1) { keys += "Down" }
                        keys += "Enter"
                    }
                    other.isNotEmpty() -> {
                        keys += (n + 1).toString()
                        flush()
                        api.chatSend(session, pin, other)
                        delay(800)
                    }
                    else -> keys += ((picks[qi].firstOrNull() ?: 0) + 1).toString()
                }
            }
            flush()
            delay(800)
            var h = api.chatHistory(session, pin)
            // A "Review your answers" screen after several questions: the first entry is Submit.
            if (h.screen?.contains("Submit answers") == true) {
                api.keys(session, "Enter")
                delay(500)
                h = api.chatHistory(session, pin)
            }
            setChat(session, h)
        }
    }

    /** A slash command like /model: it never shows as a message, so it isn't tracked as pending. */
    fun chatCommand(text: String) {
        val session = _chatSession.value ?: return
        val pin = chatPin ?: return
        action("Sending $text…") {
            api.chatSend(session, pin, text)
            delay(1500)
            setChat(session, api.chatHistory(session, pin))
        }
    }

    /** Clears the box and shows the message as pending at once; the SSH round trip takes seconds. A failure puts the text back. */
    fun sendChat(text: String, onSent: () -> Unit, onFail: () -> Unit = {}) {
        val session = _chatSession.value ?: return
        val pin = chatPin ?: return
        val t = text.trim()
        val tracked = !t.startsWith("/")   // a slash command never shows up as a message, so it would stay "queued" for good
        onSent()
        if (tracked) _chatPending.value = _chatPending.value + t
        action("Sending…", onError = { e ->
            if (tracked) _chatPending.value = _chatPending.value.toMutableList().also { it.remove(t) }
            onFail()
            report(e)
        }) {
            api.chatSend(session, pin, text)
            delay(800)
            setChat(session, api.chatHistory(session, pin))
        }
    }

    /** Drops a message stuck as "queued" (it only hides it here; nothing is unsent). */
    fun dismissPending(text: String) { _chatPending.value = _chatPending.value.toMutableList().also { it.remove(text) } }

    /** New history; drop pending messages the conversation now shows. */
    private fun setChat(session: String, c: ChatData) {
        if (session != _chatSession.value) return   // a late reply from the chat we just left
        _chat.value = c
        // Compare loosely: Claude Code may change spacing, and the server cuts long messages at 8000 characters.
        fun norm(t: String) = t.trim().replace(Regex("\\s+"), " ").take(7000)
        val shown = c.messages.filter { it.role == "user" }.map { norm(it.text) }.toSet()
        _chatPending.value = _chatPending.value.filterNot { norm(it) in shown }
    }

    /** Send a file from the phone into the chat's project folder; [onDone] gets its path. */
    fun uploadToChat(name: String, bytes: ByteArray, onDone: (String) -> Unit) {
        val session = _chatSession.value ?: return
        val pin = chatPin ?: return
        action("Sending $name…") {
            val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            onDone(api.upload(session, pin, name, b64).path)
        }
    }

    private val _chatLog = MutableStateFlow<List<ChatLogEntry>?>(null)
    val chatLog = _chatLog.asStateFlow()
    fun loadChatLog() = action("Loading the chat log…") { _chatLog.value = api.chatLog() }
    fun clearChatLog() { _chatLog.value = null }

    /** Session notifications: on only after Android's permission (the Settings screen asks). */
    fun setNotify(on: Boolean) = viewModelScope.launch {
        store.setNotify(on)
        val ctx = getApplication<Application>()
        if (on) { SessionWatcher.schedule(ctx); setupPush() } else SessionWatcher.cancel(ctx)
    }

    // ---- Instant push alerts (server hooks → Firebase) ---------------------------------

    /** Quietly (re)registers this phone; fails without a word when offline or the server has no push yet. */
    private fun setupPush() = viewModelScope.launch {
        val ctx = getApplication<Application>()
        _pushReady.value = runCatching { Push.setup(ctx, api) }.getOrDefault(Push.active(ctx))
    }

    /** Saves the pasted Firebase key on the server (it registers the app itself), then registers this phone. */
    fun pushSetup(key: String) = action("Setting up push alerts…") {
        val c = api.pushSetup(key)
        if (c.configured) { setupPush(); say("Push alerts are set up for project ${c.projectId}. Tap \"Send a test push\" to try it, then Restart sessions so they pick up the hooks.") }
    }

    fun pushTest() = action("Sending a test push…") {
        val sent = api.pushTest().sent
        say(if (sent > 0) "Push sent to $sent phone(s): it should arrive in a moment." else "Nothing was sent. Push isn't fully set up on the server, or this phone isn't registered yet.")
    }

    /** A session's "finished" alerts on or off (questions always alert). */
    fun setSessionPush(name: String, on: Boolean) {
        _sessions.value = _sessions.value.map { if (it.name == name) it.copy(pushDone = on) else it }
        viewModelScope.launch {
            try {
                api.pushSession(name, on)
            } catch (e: ApiException) {
                _sessions.value = _sessions.value.map { if (it.name == name) it.copy(pushDone = !on) else it }
                report(e)
            }
        }
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
    fun openDetail(d: Detail?) {
        if (_mcpAuth.value != null) { _mcpAuth.value = null; viewModelScope.launch { runCatching { api.mcpAuthCancel() } } }
        _detail.value = d
    }

    /** An MCP sign-in in progress: server name to the sign-in URL. */
    private val _mcpAuth = MutableStateFlow<Pair<String, String>?>(null)
    val mcpAuth = _mcpAuth.asStateFlow()

    fun mcpAuthStart(name: String) = action("Starting sign-in for $name…") {
        _mcpAuth.value = name to api.mcpAuthStart(name).url
    }

    fun mcpAuthFinish(callbackUrl: String) = action("Finishing sign-in…", onError = { e -> _mcpAuth.value = null; report(e) }) {
        _mcp.value = api.mcpAuthFinish(callbackUrl)
        _mcpAuth.value = null
        say("Signed in. Restart sessions to use it.")
    }

    // ---- Team (extra Claude accounts) ---------------------------------------------

    /** One server's accounts in the cluster view; [accounts] is null when it couldn't be reached ([error] says why). */
    data class ClusterGroup(val serverId: String, val host: String, val active: Boolean, val accounts: List<ClusterAccount>?, val error: String = "")
    /** A just-added server signed in to another Claude account: ask whether it joins the cluster. */
    data class ClusterOffer(val serverId: String, val host: String, val email: String)

    private val _cluster = MutableStateFlow<List<ClusterGroup>?>(null)
    val cluster = _cluster.asStateFlow()
    private val _clusterOffer = MutableStateFlow<ClusterOffer?>(null)
    val clusterOffer = _clusterOffer.asStateFlow()
    /** Server-wide cluster settings; null until loaded, or when the server is too old to have them. */
    private val _clusterConfig = MutableStateFlow<ClusterConfig?>(null)
    val clusterConfig = _clusterConfig.asStateFlow()

    private fun apiFor(s: Server) = LauncherApi(config = { s.toServerConfig() }, identity = { keys.identity() })

    private suspend fun clusterOf(s: Server, active: Boolean): ClusterGroup = try {
        ClusterGroup(s.id, s.host, active, (if (active) api else apiFor(s)).cluster().accounts)
    } catch (e: ApiException) {
        ClusterGroup(s.id, s.host, active, null, if (e.code == Codes.FORBIDDEN) "Server scripts need an update" else e.message.orEmpty())
    }

    /** The active server's accounts plus those of the servers already in the cluster, looked up together. */
    fun loadCluster() = action("Loading accounts…") {
        _cluster.value = clusterGroups()
        _clusterConfig.value = try { api.clusterConfig() } catch (_: ApiException) { null }
    }
    fun setClusterConfig(key: String, value: String) = action("Saving…") { _clusterConfig.value = api.clusterConfigSet(key, value) }

    private suspend fun clusterGroups(): List<ClusterGroup> = coroutineScope {
        val cur = store.current()
        val others = cur.servers.filter { it.id != cur.activeId && it.isConfigured && it.inCluster == true }
        val first = async { clusterOf(cur.servers.first { it.id == cur.activeId }, true) }
        (listOf(first) + others.map { s -> async { clusterOf(s, false) } }).awaitAll()
    }

    fun removeFromCluster(serverId: String) = viewModelScope.launch {
        store.setInCluster(serverId, false)
        _cluster.value = _cluster.value?.filter { it.serverId != serverId }
    }

    fun answerClusterOffer(join: Boolean) {
        val o = _clusterOffer.value ?: return
        _clusterOffer.value = null
        viewModelScope.launch { store.setInCluster(o.serverId, join, alsoOthers = join) }
    }

    /**
     * After a new server is trusted: if it's signed in to a different Claude account than the servers
     * we already have, offer the cluster. Same account, or nothing to compare: ask nothing.
     */
    private suspend fun detectNewAccount() {
        val cur = store.current()
        val me = cur.servers.firstOrNull { it.id == cur.activeId } ?: return
        if (me.inCluster != null) return
        val others = cur.servers.filter { it.id != me.id && it.isConfigured }
        if (others.isEmpty()) return
        val mine = try { api.cluster().accounts.firstOrNull { it.name == "main" }?.email } catch (_: ApiException) { null }
        if (mine == null) return
        val known = coroutineScope {
            others.map { s -> async { try { apiFor(s).cluster().accounts.mapNotNull { it.email } } catch (_: ApiException) { emptyList() } } }.awaitAll().flatten()
        }
        if (mine in known) store.setInCluster(me.id, false)   // same account: nothing to add
        else _clusterOffer.value = ClusterOffer(me.id, me.host, mine)
    }

    /** The open chat's workers (attached or not) and its open work; null until loaded. */
    private val _chatWorkers = MutableStateFlow<List<ChatWorker>?>(null)
    val chatWorkers = _chatWorkers.asStateFlow()
    private val _chatTasks = MutableStateFlow<List<ChatTask>>(emptyList())
    val chatTasks = _chatTasks.asStateFlow()
    private suspend fun refreshChatWorkers(session: String) {
        val d = api.clusterSession(session)
        _chatWorkers.value = d.workers
        _chatTasks.value = d.tasks
    }
    fun loadChatWorkers() {
        val s = _chatSession.value ?: return
        action("Loading workers…") { refreshChatWorkers(s) }
    }
    fun attachWorker(name: String, on: Boolean) {
        val s = _chatSession.value ?: return
        action("Saving…") { api.clusterAttach(s, name, on); refreshChatWorkers(s) }
    }
    /** Role or mode for this chat only; blank goes back to the worker's own. */
    fun assignWorker(name: String, field: String, value: String) {
        val s = _chatSession.value ?: return
        action("Saving…") { api.clusterAssign(s, name, field, value); refreshChatWorkers(s) }
    }

    private val _workerLogin = MutableStateFlow<Pair<String, LoginUrl>?>(null)
    val workerLogin = _workerLogin.asStateFlow()
    /** The Gemini worker whose API key is being asked for. */
    private val _workerKey = MutableStateFlow<String?>(null)
    val workerKey = _workerKey.asStateFlow()
    fun closeWorkerKey() { _workerKey.value = null }
    fun saveWorkerKey(name: String, key: String) = action("Saving the key for $name…") {
        api.workerSetKey(name, key)
        _workerKey.value = null
        _cluster.value = clusterGroups()
    }
    private val _workerRuns = MutableStateFlow<Pair<String, List<WorkerRun>>?>(null)
    val workerRuns = _workerRuns.asStateFlow()

    fun addWorker(name: String, role: String, kind: String = "claude") = action("Adding $name…") {
        api.workerAdd(name, role, kind)
        _cluster.value = clusterGroups()
        if (kind == "gemini") _workerKey.value = name else _workerLogin.value = name to api.workerLoginStart(name)   // straight on to signing in
    }
    fun setWorker(name: String, field: String, value: String) = action("Saving…") {
        api.workerSet(name, field, value)
        _cluster.value = clusterGroups()
    }
    fun removeWorker(name: String) = action("Removing $name…") {
        api.workerRemove(name)
        _cluster.value = clusterGroups()
    }
    fun workerLoginStart(name: String, kind: String = "claude") = action("Starting sign-in for $name…") {
        if (kind == "gemini") { _workerKey.value = name; return@action }
        _workerLogin.value = name to api.workerLoginStart(name)
    }
    fun workerLoginCode(name: String, code: String) = action("Checking code…") {
        api.workerLoginCode(name, code)
        _workerLogin.value = null
        _cluster.value = clusterGroups()
    }
    // ponytail: the server's login helper session just times out; add a cancel action if stale ones annoy.
    fun closeWorkerLogin() { _workerLogin.value = null }
    fun showWorkerRuns(name: String) = action("Loading runs…") { _workerRuns.value = name to api.workerRuns(name).runs }
    fun closeWorkerRuns() { _workerRuns.value = null }

    // Hosts: web servers chats can work on (docs/hosts-design.md).
    private val _hosts = MutableStateFlow<List<Host>?>(null)
    val hosts = _hosts.asStateFlow()
    /** The Add host dialog: open, the probed host key to confirm, and the server's last error shown in it. */
    private val _hostAddOpen = MutableStateFlow(false)
    val hostAddOpen = _hostAddOpen.asStateFlow()
    private val _hostProbe = MutableStateFlow<HostProbe?>(null)
    val hostProbe = _hostProbe.asStateFlow()
    /** The server's own words for a host problem: its auth_failed means the HOST refused our key, not this phone's key (friendly() says the latter). */
    private fun hostMessage(e: ApiException) = if (e.code == Codes.AUTH_FAILED) e.message else friendly(e).first
    private val _hostError = MutableStateFlow<String?>(null)
    val hostError = _hostError.asStateFlow()
    /** "Generate a key": the host name it was made for and its public half. */
    private val _hostKey = MutableStateFlow<Pair<String, HostKey>?>(null)
    val hostKey = _hostKey.asStateFlow()
    fun loadHosts() = action("Loading servers…") { _hosts.value = api.hostList().hosts }
    /** What the Add server dialog keeps on the phone while you go and fetch things elsewhere: no secrets, only the plain fields and the PUBLIC key. */
    data class HostDraft(val name: String = "", val address: String = "", val port: String = "22", val user: String = "", val auth: String = "key")
    private val hostDraftPrefs by lazy { getApplication<Application>().getSharedPreferences("host_draft", android.content.Context.MODE_PRIVATE) }
    fun hostDraft() = hostDraftPrefs.run {
        HostDraft(getString("name", "").orEmpty(), getString("address", "").orEmpty(), getString("port", "22").orEmpty(), getString("user", "").orEmpty(), getString("auth", "key").orEmpty())
    }
    fun saveHostDraft(d: HostDraft) = hostDraftPrefs.edit().putString("name", d.name).putString("address", d.address).putString("port", d.port)
        .putString("user", d.user).putString("auth", d.auth).apply()
    private fun rememberHostKey(name: String, key: HostKey) {
        _hostKey.value = name to key
        hostDraftPrefs.edit().putString("key_name", name).putString("key_pub", key.publicKey).putString("key_fp", key.fingerprint).apply()
    }
    fun openHostAdd() {
        _hostProbe.value = null; _hostError.value = null; _hostAddOpen.value = true
        // The key made earlier is still waiting on the server (kept 24 h): show it again.
        _hostKey.value = hostDraftPrefs.getString("key_name", null)?.let { n -> n to HostKey(hostDraftPrefs.getString("key_pub", "").orEmpty(), hostDraftPrefs.getString("key_fp", "").orEmpty()) }
    }
    /** [keepDraft]: tapping outside the dialog keeps what was typed; Cancel and a finished add clear it. */
    fun closeHostAdd(keepDraft: Boolean = false) {
        _hostAddOpen.value = false; _hostProbe.value = null; _hostError.value = null; _hostKey.value = null
        if (!keepDraft) hostDraftPrefs.edit().clear().apply()
    }
    fun clearHostKey() { _hostKey.value = null }
    fun generateHostKey(name: String) {
        _hostKey.value = null
        _hostError.value = null
        action("Generating a key…", onError = { _hostError.value = hostMessage(it) }) { rememberHostKey(name, api.hostKeygen(name)) }
    }
    fun importHostKey(name: String, keyBase64: String) {
        _hostKey.value = null
        _hostError.value = null
        action("Importing the key…", onError = { _hostError.value = hostMessage(it) }) { rememberHostKey(name, api.hostKeyImport(name, keyBase64)) }
    }
    /** Address or port changed after Check: the shown key no longer applies. */
    fun clearHostProbe() { _hostProbe.value = null }
    fun probeHost(address: String, port: Int) {
        _hostProbe.value = null
        _hostError.value = null
        action("Checking $address…", onError = { _hostError.value = hostMessage(it) }) { _hostProbe.value = api.hostProbe(address, port) }
    }
    fun addHost(name: String, address: String, port: Int, user: String, fingerprint: String, auth: String, secret: String) {
        _hostError.value = null
        action("Adding $name…", onError = { _hostError.value = hostMessage(it) }) {
            api.hostAdd(name, address, port, user, fingerprint, auth, secret)
            closeHostAdd()
            openHostSetup(name, port)
            say("$name added.")
            _hosts.value = api.hostList().hosts
        }
    }
    /** The "Set up this server" step: the host (name, SSH port) it is open for, the last result and the server's error shown in it. */
    private val _hostSetup = MutableStateFlow<Pair<String, Int>?>(null)
    val hostSetup = _hostSetup.asStateFlow()
    private val _hostHarden = MutableStateFlow<HostHardenResult?>(null)
    val hostHarden = _hostHarden.asStateFlow()
    private val _hostSetupError = MutableStateFlow<String?>(null)
    val hostSetupError = _hostSetupError.asStateFlow()
    fun openHostSetup(name: String, sshPort: Int) { _hostHarden.value = null; _hostSetupError.value = null; _hostSetup.value = name to sshPort }
    fun closeHostSetup() { _hostSetup.value = null; _hostHarden.value = null; _hostSetupError.value = null }
    fun hardenHost(name: String, ports: List<String>, steps: List<String>) {
        _hostHarden.value = null
        _hostSetupError.value = null
        action("Setting up $name…", onError = {
            _hostSetupError.value = when (it.code) {
                "needs_sudo" -> "This user needs passwordless sudo (or use root)"
                else -> hostMessage(it)
            }
        }) { _hostHarden.value = api.hostHarden(name, ports, steps) }
    }
    /** "Add my key": the host the dialog is open for, the server's answer and any error, all shown inside the dialog. */
    private val _hostAuthTarget = MutableStateFlow<String?>(null)
    val hostAuthTarget = _hostAuthTarget.asStateFlow()
    private val _hostAuthError = MutableStateFlow<String?>(null)
    val hostAuthError = _hostAuthError.asStateFlow()
    private val _hostAuthDone = MutableStateFlow<String?>(null)
    val hostAuthDone = _hostAuthDone.asStateFlow()
    fun openHostAuthorize(name: String) { _hostAuthError.value = null; _hostAuthDone.value = null; _hostAuthTarget.value = name }
    fun closeHostAuthorize() { _hostAuthTarget.value = null; _hostAuthError.value = null; _hostAuthDone.value = null }
    fun authorizeHostKey(name: String, publicKey: String) {
        _hostAuthError.value = null
        _hostAuthDone.value = null
        action("Adding the key to $name…", onError = { _hostAuthError.value = hostMessage(it) }) {
            val added = api.hostAuthorize(name, publicKey)["added"]?.jsonPrimitive?.booleanOrNull == true
            _hostAuthDone.value = if (added) "Added. You can now sign in to $name with that key." else "That key was already on $name."
        }
    }
    fun testHost(name: String) = action("Testing $name…") { api.hostTest(name); say("$name: connection works.") }
    fun removeHost(name: String) = action("Removing $name…") {
        api.hostRemove(name)
        _hosts.value = api.hostList().hosts
    }

    /** The open chat's hosts (attached or not); null until loaded. */
    private val _chatHosts = MutableStateFlow<List<ChatHost>?>(null)
    val chatHosts = _chatHosts.asStateFlow()
    fun loadChatHosts() {
        val s = _chatSession.value ?: return
        action("Loading servers…") { _chatHosts.value = api.hostSession(s).hosts }
    }
    fun attachHost(name: String, on: Boolean) {
        val s = _chatSession.value ?: return
        action("Saving…") { api.hostAttach(s, name, on); _chatHosts.value = api.hostSession(s).hosts }
    }


    fun disconnect(id: String, name: String) = action("Disconnecting $name…") {
        api.disconnect(id)
        _detail.value = null
        say("$name disconnected. Restart sessions to drop it.")
        refreshStatus()
    }

    /** A plugin's MCP server goes away with the plugin: turn the plugin off. */
    fun disablePlugin(id: String) = action("Disabling $id…") {
        val r = api.claudeCmd("plugin disable $id")
        if (r.exitCode != 0) throw ApiException(Codes.INTERNAL, r.output.ifBlank { "claude plugin disable failed" })
        _detail.value = null
        say("$id disabled. Restart sessions to drop it; turn it back on in the Claude tab.")
        runCatching { api.plugins() }.getOrNull()?.let { _plugins.value = it }
        _mcp.value = api.mcpRefresh()
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

    /** A plugin the `skills` CLI manages: the server re-runs `npx skills add <source>` (can take minutes). */
    fun updateSkills(source: String) = action("Updating $source…") {
        api.skillsUpdate(source)
        say("Updated $source")
        runCatching { api.plugins() }.getOrNull()?.let { _plugins.value = it }
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
            kind == LoginKind.GITLAB -> installGlab()
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

    private val _showApple = MutableStateFlow(false)
    val showApple = _showApple.asStateFlow()
    fun showApple(show: Boolean) { _showAdd.value = false; _showApple.value = show }

    fun saveApple(keyId: String, issuerId: String, teamId: String, file: ByteArray) =
        action("Checking and saving the Apple developer key…") {
            val b64 = android.util.Base64.encodeToString(file, android.util.Base64.NO_WRAP)
            api.loginApple(keyId, issuerId, teamId, b64)
            say("Apple developer key saved. Restart sessions to use it.")
            refreshStatus()
        }

    fun removeApple() = action("Removing the Apple developer key…") {
        api.removeApple()
        say("Apple developer key removed.")
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

    fun setSecretFile(name: String, file: ByteArray) = action("Saving $name…") {
        val b64 = android.util.Base64.encodeToString(file, android.util.Base64.NO_WRAP)
        api.setSecretFile(name, b64)
        say("$name saved (it holds the file's path). Restart sessions to use it.")
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
        if (def.install != null && missing) {
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
    private fun deleteApks() = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        ?.listFiles { f -> f.name.endsWith(".apk") }?.forEach { it.delete() }

    private var downloadJob: Job? = null

    /** One download at a time: a second tap would delete the file the first is writing. */
    fun downloadAndInstall(url: String): Job {
        downloadJob?.takeIf { it.isActive }?.let { return it }
        return downloadApk(url).also { downloadJob = it }
    }

    private fun downloadApk(url: String): Job = viewModelScope.launch {
        val ctx = getApplication<Application>()
        val dm = ctx.getSystemService(DownloadManager::class.java)
        val name = url.substringAfterLast('/')
        // Only ever keep the build being downloaded.
        deleteApks()
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

    /** What the open chat shows as a banner: the app's snackbar sits behind the full-screen chat. */
    private val _chatNotice = MutableStateFlow<String?>(null)
    val chatNotice = _chatNotice.asStateFlow()
    private var noticeJob: Job? = null

    fun say(text: String, label: String? = null, fix: Fix? = null) {
        if (_chatSession.value != null) {
            _chatNotice.value = text
            noticeJob?.cancel()
            noticeJob = viewModelScope.launch { delay(7_000); _chatNotice.value = null }
        } else {
            _messages.trySend(UiMessage(text, label, fix))
        }
    }

    fun clearChatNotice() { _chatNotice.value = null }

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

    /** False while the app is in the background: screens pause their refresh loops (battery, data). */
    private val _foreground = MutableStateFlow(true)
    val foreground = _foreground.asStateFlow()
    fun setForeground(on: Boolean) {
        _foreground.value = on
        if (on && !_pushReady.value) viewModelScope.launch { if (store.current().notify) setupPush() }   // retry after offline / locked starts
    }

    private var statusJob: Job? = null

    fun refreshStatus() {
        if (_statusRefreshing.value) return
        statusJob = viewModelScope.launch {
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

    fun deleteRepo(repo: Repo) = action("Deleting ${repo.fullName}…") {
        api.repoDelete(repo.fullName)
        say("${repo.fullName} deleted on GitHub." + if (repo.local) " Its folder on the server is still there." else "")
        refreshRepos(true)
    }

    fun renameRepo(repo: Repo, name: String) = action("Renaming ${repo.name}…") {
        api.repoRename(repo.fullName, name)
        say("Renamed to ${repo.owner}/$name." + if (repo.local) " The folder on the server keeps its old name." else "")
        refreshRepos(true)
    }

    fun setRepoPrivate(repo: Repo, private: Boolean) = action("Making ${repo.name} ${if (private) "private" else "public"}…") {
        api.repoVisibility(repo.fullName, private)
        say("${repo.fullName} is now ${if (private) "private" else "public"}.")
        refreshRepos(true)
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

    fun loadTail(target: String) = action("Opening terminal…") {
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
        statusJob?.cancel(); _statusRefreshing.value = false   // an in-flight call must not land on the new server
        ssoPoller?.cancel()
        tailJob?.cancel()
        _runHistory.value = emptyList()
        _ccResult.value = null
        _chatLog.value = null
        _cluster.value = null
        _clusterConfig.value = null
        _clusterOffer.value = null
        _workerLogin.value = null
        _workerRuns.value = null
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
        // Another server: nothing from the old one may stay on screen or in use.
        _mcp.value = null
        _plugins.value = null
        _mcpAuth.value = null
        _detail.value = null
        closeChat()
        queuedBySession.clear()   // session names repeat across servers
        draftPrefs.edit().clear().apply()
        chatPin = null
        pinVault.clear()
    }

    fun acceptHostKey(accept: Boolean) {
        val info = _pendingHostKey.value ?: return
        _pendingHostKey.value = null
        if (!accept) return
        viewModelScope.launch {
            store.pinHostKey(info.type, info.blob, info.fingerprint)
            say("Server trusted. Checking connection…")
            refreshStatus()
            detectNewAccount()
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

    // ---- Migrate (the logic is in Migrate.kt) ---------------------------------------

    private val migrateFlow = MigrateFlow(viewModelScope, store, keys) { apiFor(it) }
    val migrate: StateFlow<MigrateUi> = migrateFlow.ui

    fun migrateOpen() = migrateFlow.open()
    /** Leaves the wizard's screen and stops its running calls; the state stays so it can be resumed. */
    fun migrateClose() = migrateFlow.close()
    /** Throws the wizard's state away and starts again at the choice. */
    fun migrateReset() = migrateFlow.reset()
    fun migrateSetServers(fromId: String, toId: String) = migrateFlow.setServers(fromId, toId)
    fun migrateSetUser(name: String) = migrateFlow.setUser(name)
    /** Runs the same step again after an error. */
    fun migrateRetry() = migrateFlow.retry()
    fun migrateSetSignOut(choices: MigrateSignOut) = migrateFlow.setSignOutChoices(choices)

    /** Step CHECK: reads the plan from the old server. */
    fun migrateStart() = migrateFlow.start()
    /** Back from CHECK to CHOOSE. */
    fun migrateEdit() = migrateFlow.edit()
    /** The plan was seen: on to STOP_SESSIONS (stops idle sessions at once). */
    fun migrateConfirmPlan() = migrateFlow.confirmPlan()

    fun migrateRefreshSessions() = migrateFlow.refreshSessions()
    fun migrateStopIdle() = migrateFlow.stopIdle()
    fun migrateStopSession(name: String) = migrateFlow.stopSession(name)

    /** Tests [password] on the new server (blank = test without); sets sudoMode. */
    fun migrateCheckSudo(password: String = "") = migrateFlow.sudoCheck(password)
    fun migrateCreateUser(sudoPassword: String) = migrateFlow.createUser(sudoPassword)
    fun migrateSetLoginKey(on: Boolean) = migrateFlow.setLoginKey(on)
    /** The login key was saved (or declined): forget it. */
    fun migrateLoginKeySaved() = migrateFlow.loginKeySaved()
    /** Takes the first, setup entry of the new server out of the app's list (the new user's entry stays). */
    fun migrateRemoveSetupEntry() = migrateFlow.removeSetupEntry()
    fun migrateGithubSignIn(token: String) = migrateFlow.githubSignIn(token)
    fun migrateCloneMissing() = migrateFlow.cloneMissing()
    /** Sends the backup, then restores it (resumes whatever is already running). */
    fun migrateTransfer() = migrateFlow.transferNow()

    fun migrateSignInStart(name: String, main: Boolean) = migrateFlow.signInStart(name, main)
    fun migrateSignInCode(name: String, main: Boolean, code: String) = migrateFlow.signInCode(name, main, code)
    fun migrateSignInCancel() = migrateFlow.signInCancel()
    fun migrateSignInFinish(skipPending: Boolean = false) = migrateFlow.signInFinish(skipPending)

    /** [items] from claude, workers, github, autostart; empty skips the step. */
    fun migrateSignOut(items: List<String>) = migrateFlow.signOut(items)
    fun migrateReboot(sudoPassword: String) = migrateFlow.reboot(sudoPassword)
    fun migrateSkipReboot() = migrateFlow.skipReboot()
    fun migrateVerify() = migrateFlow.verifyNow()
}
