package life.mygig.clauderc.ui.vm

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import life.mygig.clauderc.api.ApiException
import life.mygig.clauderc.api.Codes
import life.mygig.clauderc.api.LauncherApi
import life.mygig.clauderc.api.LoginUrl
import life.mygig.clauderc.api.StatusData
import life.mygig.clauderc.ui.LoginKind
import life.mygig.clauderc.ui.friendly

/**
 * The login dialogs: Claude, GitHub, AWS (keys or SSO), GitLab, Docker, YouTube.
 * [setStatus] and [refreshStatus] reach the shared server status; [onGithub] reloads what a
 * new GitHub login changes (owners, repos).
 */
class LoginsController(
    private val api: LauncherApi,
    private val tools: VmTools,
    private val setStatus: (StatusData) -> Unit,
    private val refreshStatus: () -> Unit,
    private val onGithub: () -> Unit,
) {
    private val _login = MutableStateFlow<LoginKind?>(null)
    val login = _login.asStateFlow()
    private val _loginUrl = MutableStateFlow<LoginUrl?>(null)
    val loginUrl = _loginUrl.asStateFlow()
    /** An error from a login call, shown inside the open login dialog. */
    private val _loginError = MutableStateFlow<String?>(null)
    val loginError = _loginError.asStateFlow()
    private var ssoPoller: Job? = null

    fun showLogin(kind: LoginKind?) {
        if (_login.value == LoginKind.CLAUDE && _loginUrl.value != null && kind != LoginKind.CLAUDE) {
            tools.scope.launch { runCatching { api.loginClaudeCancel() } }
        }
        ssoPoller?.cancel()
        _loginUrl.value = null
        _loginError.value = null
        _login.value = kind
    }

    /** Opens [kind]'s form as it is, without resetting a sign-in (after installing its CLI). */
    fun openForm(kind: LoginKind) { _login.value = kind }

    /** Another server: a running SSO poll must not land on it. */
    fun cancelPoller() { ssoPoller?.cancel() }

    /** Like [VmTools.action], but errors show inside the login dialog while it's open. */
    private fun loginAction(kind: LoginKind, label: String, block: suspend () -> Unit) {
        _loginError.value = null
        tools.action(label, onError = { e ->
            if (_login.value == kind) {
                _loginError.value = friendly(e).first + (e.pane?.takeIf { it.isNotBlank() }?.let { "\n\n$it" } ?: "")
            } else {
                tools.report(e)
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
        onGithub()
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
        ssoPoller = tools.scope.launch {
            repeat(150) {
                delay(4_000)
                if (_login.value != LoginKind.AWS) return@launch
                val s = runCatching { api.status() }.getOrNull() ?: return@repeat
                setStatus(s)
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
        ssoPoller = tools.scope.launch {
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
        tools.say(text)
        refreshStatus()
    }
}
