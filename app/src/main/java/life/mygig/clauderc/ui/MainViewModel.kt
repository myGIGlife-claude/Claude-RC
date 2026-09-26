package life.mygig.clauderc.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import life.mygig.clauderc.api.ApiException
import life.mygig.clauderc.api.Codes
import life.mygig.clauderc.api.LauncherApi
import life.mygig.clauderc.api.LoginUrl
import life.mygig.clauderc.api.NewResult
import life.mygig.clauderc.api.OwnersData
import life.mygig.clauderc.api.Repo
import life.mygig.clauderc.api.Session
import life.mygig.clauderc.api.StatusData
import life.mygig.clauderc.api.TailResult
import life.mygig.clauderc.data.AppSettings
import life.mygig.clauderc.data.SettingsStore
import life.mygig.clauderc.data.ThemeMode
import life.mygig.clauderc.ssh.HostKeyInfo
import life.mygig.clauderc.ssh.SshKeyManager
import life.mygig.clauderc.ssh.SshRunner

enum class Tab { STATUS, NEW, PROJECTS, SESSIONS }
enum class LoginKind { CLAUDE, GITHUB, AWS }

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

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val store = SettingsStore(app)
    val keys = SshKeyManager(app)
    private val json = Json { ignoreUnknownKeys = true }
    private val api = LauncherApi(config = { store.current().toServerConfig() }, identity = { keys.identity() })

    val settings: StateFlow<AppSettings?> =
        store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _busy = MutableStateFlow<String?>(null)
    val busy = _busy.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val _status = MutableStateFlow<StatusData?>(null)
    val status = _status.asStateFlow()
    private val _statusCheckedAt = MutableStateFlow<Long?>(null)
    val statusCheckedAt = _statusCheckedAt.asStateFlow()
    private val _statusRefreshing = MutableStateFlow(false)
    val statusRefreshing = _statusRefreshing.asStateFlow()

    private val _repos = MutableStateFlow<List<Repo>>(emptyList())
    val repos = _repos.asStateFlow()
    private val _reposRefreshing = MutableStateFlow(false)
    val reposRefreshing = _reposRefreshing.asStateFlow()

    private val _owners = MutableStateFlow<OwnersData?>(null)
    val owners = _owners.asStateFlow()

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

    private val _publicKey = MutableStateFlow("")
    val publicKey = _publicKey.asStateFlow()

    private val _tab = MutableStateFlow(Tab.STATUS)
    val tab = _tab.asStateFlow()
    private val _showSettings = MutableStateFlow(false)
    val showSettings = _showSettings.asStateFlow()

    init {
        viewModelScope.launch {
            _publicKey.value = withContext(Dispatchers.Default) { keys.publicKey() }
            store.reposCache()?.let { cached ->
                runCatching { json.decodeFromString<List<Repo>>(cached) }.getOrNull()?.let { _repos.value = it }
            }
            store.ownersCache()?.let { cached ->
                runCatching { json.decodeFromString<OwnersData>(cached) }.getOrNull()?.let { _owners.value = it }
            }
            if (store.current().isConfigured) refreshStatus()
        }
    }

    fun selectTab(t: Tab) { _tab.value = t }
    fun openSettings(show: Boolean) { _showSettings.value = show }

    fun say(text: String, label: String? = null, fix: Fix? = null) {
        _messages.trySend(UiMessage(text, label, fix))
    }

    /** Runs a server call with the spinner on and maps any failure to a friendly message. */
    private fun action(label: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = label
            try {
                block()
            } catch (e: ApiException) {
                report(e)
            } finally {
                _busy.value = null
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
        viewModelScope.launch {
            _statusRefreshing.value = true
            try {
                _status.value = api.status()
                _statusCheckedAt.value = System.currentTimeMillis()
            } catch (e: ApiException) {
                report(e) { refreshStatus() }
            } finally {
                _statusRefreshing.value = false
            }
        }
    }

    // ---- Projects -------------------------------------------------------------

    fun refreshRepos(force: Boolean) {
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
            try {
                val o = api.owners()
                _owners.value = o
                store.setOwnersCache(json.encodeToString(o))
            } catch (e: ApiException) {
                report(e) { loadOwners() }
            }
        }
    }

    fun openRepo(repo: Repo, start: Boolean) = action(if (repo.local) "Updating ${repo.name}…" else "Cloning ${repo.name}…") {
        try {
            val r = api.open(repo.fullName, start)
            val what = when (r.action) {
                "cloned" -> "Cloned"
                "pulled" -> "Pulled latest for"
                "cloning" -> "Still cloning"
                else -> "Ready:"
            }
            val started = r.session?.let { " — Claude started" } ?: ""
            if (r.pending) {
                say("$what ${repo.name} in the background. Check Projects in a minute.")
            } else {
                say(
                    "$what ${repo.name}$started",
                    if (r.session != null) "Open in Claude" else null,
                    if (r.session != null) Fix.OpenClaude else null,
                )
            }
        } finally {
            refreshRepos(false)
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

    fun stopProject(project: String) = action("Stopping $project…") {
        val r = api.stop(project)
        say(if (r.stopped) "Stopped $project" else "$project wasn't running")
        refreshSessions(); refreshRepos(false)
    }

    private var tailProject: String? = null

    fun loadTail(project: String) = action("Reading log…") {
        tailProject = project
        _tail.value = api.tail(project, 80)
    }

    fun refreshTail() { tailProject?.let { loadTail(it) } }

    fun closeTail() { _tail.value = null }

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
        if (kind == null && _login.value == LoginKind.CLAUDE && _loginUrl.value != null) {
            viewModelScope.launch { runCatching { api.loginClaudeCancel() } }
        }
        _loginUrl.value = null
        _login.value = kind
    }

    fun claudeLoginStart() = action("Starting Claude login…") {
        _loginUrl.value = api.loginClaudeStart()
    }

    fun claudeLoginCode(code: String) = action("Checking code…") {
        api.loginClaudeCode(code)
        loginDone("Claude is logged in")
    }

    fun githubLogin(token: String) = action("Saving GitHub token…") {
        val r = api.loginGithub(token)
        loginDone("GitHub logged in as ${r.user}")
        loadOwners(); refreshRepos(true)
    }

    fun awsKeysLogin(id: String, secret: String, region: String) = action("Saving AWS keys…") {
        val r = api.loginAwsKeys(id, secret, region)
        loginDone("AWS logged in" + (r.account?.let { " (account $it)" } ?: ""))
    }

    fun awsSsoStart() = action("Starting AWS SSO…") {
        _loginUrl.value = api.loginAwsSsoStart()
        // The user approves in the browser; poll status until AWS shows up.
        viewModelScope.launch {
            repeat(40) {
                delay(4_000)
                if (_login.value != LoginKind.AWS) return@launch
                val s = runCatching { api.status() }.getOrNull() ?: return@repeat
                _status.value = s
                _statusCheckedAt.value = System.currentTimeMillis()
                if (s.aws.loggedIn) {
                    loginDone("AWS SSO login complete")
                    return@launch
                }
            }
        }
    }

    private fun loginDone(text: String) {
        _login.value = null
        _loginUrl.value = null
        say(text)
        refreshStatus()
    }

    // ---- Settings ---------------------------------------------------------------

    private val _pendingHostKey = MutableStateFlow<HostKeyInfo?>(null)
    val pendingHostKey = _pendingHostKey.asStateFlow()

    fun saveServerAndProbe(host: String, port: Int, user: String) = action("Contacting $host…") {
        store.saveServer(host.trim(), port, user.trim())
        val info = withContext(Dispatchers.IO) {
            try {
                SshRunner.probeHostKey(host.trim(), port, user.trim())
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

    fun regenerateKey() = viewModelScope.launch {
        _publicKey.value = withContext(Dispatchers.Default) { keys.regenerate() }
        say("New key created. Install it on the server again.")
    }

    fun setAppLock(on: Boolean) = viewModelScope.launch { store.setAppLock(on) }
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { store.setTheme(mode) }
}
