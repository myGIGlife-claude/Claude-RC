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
import kotlinx.coroutines.flow.collectLatest
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
import life.mygig.clauderc.data.Server
import life.mygig.clauderc.api.Codes
import life.mygig.clauderc.api.LauncherApi
import life.mygig.clauderc.api.Latest
import life.mygig.clauderc.api.Updates
import life.mygig.clauderc.api.NewResult
import life.mygig.clauderc.api.OwnersData
import life.mygig.clauderc.api.McpServer
import life.mygig.clauderc.api.Repo
import life.mygig.clauderc.notify.Push
import life.mygig.clauderc.notify.SessionWatcher
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
import life.mygig.clauderc.ui.vm.ChatController
import life.mygig.clauderc.ui.vm.ConnectionsController
import life.mygig.clauderc.ui.vm.DriveController
import life.mygig.clauderc.ui.vm.HostsController
import life.mygig.clauderc.ui.vm.KnowledgeController
import life.mygig.clauderc.ui.vm.LoginsController
import life.mygig.clauderc.ui.vm.GeneralChatsController
import life.mygig.clauderc.ui.vm.PushController
import life.mygig.clauderc.ui.vm.ServicesController
import life.mygig.clauderc.ui.vm.TeamController
import life.mygig.clauderc.ui.vm.VmTools

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

/**
 * The app's one view model: status, projects, sessions, terminal, servers and settings live here;
 * the feature groups are in ui/vm (each gets [VmTools], never this class).
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    val store = SettingsStore(app)
    val keys = SshKeyManager(app)
    private val json = Json { ignoreUnknownKeys = true }
    private val api = LauncherApi(config = { store.current().toServerConfig() }, identity = { keys.identity() })
    private val tools = VmTools(viewModelScope, { label, onError, block -> action(label, onError, block) }, { text, label, fix -> say(text, label, fix) }, { e -> report(e) })

    /** Instant push alerts (server hooks → Firebase). */
    val push = PushController(app, api, tools) { edit -> _sessions.value = edit(_sessions.value) }

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

    // ---- Feature groups (ui/vm) -------------------------------------------------------

    /** Google Drive backup. */
    val drive = DriveController(api, tools)
    /** In-app chat: PIN, polling, pending messages, drafts, files. */
    val chats: ChatController = ChatController(app, api, tools) { team.clearChat(); webHosts.clearChatHosts() }
    /** General chats (not tied to a repo): the Sessions screen's General chat tab. */
    val general = GeneralChatsController(api, tools, { _status.value?.scriptApi }, { chats.openChat(it) }) { refreshSessions() }
    /** Team (extra Claude accounts), cluster settings and the open chat's workers. */
    val team: TeamController = TeamController(api, store, tools, { apiFor(it) }) { chats.chatSession.value }
    /** Hosts: web servers chats can work on (docs/hosts-design.md). */
    val webHosts: HostsController = HostsController(app, api, tools) { chats.chatSession.value }
    /** Developer knowledge: the monthly refresh. */
    val knowledge = KnowledgeController(api, tools)
    /** Login dialogs (Claude, GitHub, AWS, GitLab, Docker, YouTube). */
    val logins = LoginsController(api, tools, { _status.value = it }, { refreshStatus() }) { loadOwners(); refreshRepos(true) }
    /** MCP servers, plugins, tile details and the Claude command runner. */
    val connections = ConnectionsController(api, tools, { _status.value?.scriptApi ?: 0 }, { refreshStatus() })
    /** The + dialog: CLI installs, signing keys, Apple key, custom keys, token services. */
    val services = ServicesController(api, tools, { _status.value }, { refreshStatus() }, logins)

    private fun apiFor(s: Server) = LauncherApi(config = { s.toServerConfig() }, identity = { keys.identity() })

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

    private val _foreground = MutableStateFlow(true)
    val foreground = _foreground.asStateFlow()

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
            foreground.collectLatest { isForeground ->
                if (isForeground) while (true) {
                    checkUpdates()
                    delay(60 * 60 * 1000L)
                }
            }
        }
        viewModelScope.launch {
            if (store.current().notify && SessionWatcher.allowed(getApplication())) { SessionWatcher.schedule(getApplication()); push.setupPush() }
        }
        viewModelScope.launch {
            foreground.collectLatest { isForeground ->
                if (isForeground) {
                    delay(3_000)   // let the first status call land
                    while (true) {
                        connections.refreshMcp()
                        delay(5 * 60 * 1000L)
                    }
                }
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
        connections.refreshMcp()
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

    /** Claude's full checkup in a session of its own, opened in the chat. */
    fun startCheckup() = action("Starting the checkup…") {
        val s = api.doctorStart().session
        refreshSessions()
        chats.openChat(s)
    }

    /** Session notifications: on only after Android's permission (the Settings screen asks). */
    fun setNotify(on: Boolean) = viewModelScope.launch {
        store.setNotify(on)
        val ctx = getApplication<Application>()
        if (on) { SessionWatcher.schedule(ctx); push.setupPush() } else SessionWatcher.cancel(ctx)
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
        // General chats have their own tab (stop/open there); this restarts the Code chat list.
        val names = api.sessions().sessions.filterNot { it.general }.map { it.name }
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
        if (chats.chatSession.value != null) {
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
    fun setForeground(on: Boolean) {
        _foreground.value = on
        if (on && !push.pushReady.value) viewModelScope.launch { if (store.current().notify && !Push.active(getApplication<Application>())) push.setupPush() }   // retry after offline / locked starts
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
        logins.cancelPoller()
        tailJob?.cancel()
        _runHistory.value = emptyList()
        team.clearServerState()
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
        connections.clearServerState()
        general.clearServerState()
        chats.clearServerState()   // chat log, open chat, queued messages, drafts, PIN (session names repeat across servers)
    }

    fun acceptHostKey(accept: Boolean) {
        val info = _pendingHostKey.value ?: return
        _pendingHostKey.value = null
        if (!accept) return
        viewModelScope.launch {
            store.pinHostKey(info.type, info.blob, info.fingerprint)
            say("Server trusted. Checking connection…")
            refreshStatus()
            team.detectNewAccount()
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
