package life.mygig.clauderc.ui.vm

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import life.mygig.clauderc.api.ApiException
import life.mygig.clauderc.api.LauncherApi
import life.mygig.clauderc.api.ServerSetup
import life.mygig.clauderc.api.StatusData
import life.mygig.clauderc.data.Server
import life.mygig.clauderc.ssh.HostKeyInfo
import life.mygig.clauderc.ssh.SshCancelled
import life.mygig.clauderc.ssh.SshFailure
import life.mygig.clauderc.ssh.SshKeyManager
import life.mygig.clauderc.ssh.SshRunner
import life.mygig.clauderc.ui.friendly

enum class AddServerStep { FORM, PROBING, CONFIRM_KEY, RUNNING, DONE, FAILED }

/**
 * What the "Connect with a password" screen shows. Never holds a password: the screen keeps those in
 * memory only (not saveable) and hands them to [AddServerController.run] once.
 */
data class AddServerUi(
    val open: Boolean = false,
    val step: AddServerStep = AddServerStep.FORM,
    val host: String = "",
    val port: Int = 22,
    val user: String = "",
    val hostKey: HostKeyInfo? = null,
    /** The server's output, as it arrives. */
    val log: String = "",
    val message: String? = null,
    val notes: List<String> = emptyList(),
    val triesLeft: Int = ServerSetup.MAX_PASSWORD_TRIES,
    /** The probed key is the one already trusted in this run (after a wrong password): still confirmed by a tap. */
    val sameAsTrusted: Boolean = false,
)

/**
 * "Connect with a password": probe the host key, let the user confirm it, run [ServerSetup] over one
 * password login, check the phone key answers `status`, and only then add the server ([onAdded]).
 * A failure anywhere adds nothing.
 */
class AddServerController(
    private val keys: SshKeyManager,
    private val tools: VmTools,
    private val onAdded: suspend (Server, StatusData) -> Unit,
) {
    private val _ui = MutableStateFlow(AddServerUi())
    val ui = _ui.asStateFlow()
    private var job: Job? = null
    private val cancelled = AtomicBoolean(false)
    /** The key the user trusted in this run, so a retry after a wrong password needn't ask again. */
    private var confirmed: HostKeyInfo? = null

    fun open() { _ui.value = AddServerUi(open = true); confirmed = null }

    fun close() {
        stop()
        _ui.value = AddServerUi()
        confirmed = null
    }

    /** Step 1: learn the host key (nothing is saved). */
    fun connect(host: String, port: String, user: String) {
        val h = host.trim(); val u = user.trim()
        if (_ui.value.triesLeft <= 0) return
        ServerSetup.validate(h, port.trim(), u)?.let { m -> _ui.update { it.copy(message = m) }; return }
        val p = port.trim().toInt()
        if (h != _ui.value.host || p != _ui.value.port) confirmed = null
        _ui.update { it.copy(step = AddServerStep.PROBING, host = h, port = p, user = u, message = null, log = "", notes = emptyList()) }
        job = tools.scope.launch {
            try {
                val info = withContext(Dispatchers.IO) { SshRunner.probeHostKey(h, p, u) }
                _ui.update { it.copy(step = AddServerStep.CONFIRM_KEY, hostKey = info, sameAsTrusted = info == confirmed) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(step = AddServerStep.FORM, message = "Can't reach $h:$p. Check the address and port. (${e.message ?: "no answer"})") }
            }
        }
    }

    fun rejectKey() = _ui.update { it.copy(step = AddServerStep.FORM, hostKey = null, message = "Not trusted: nothing was changed.") }

    /** Steps 2-5, after the user trusted the key. The passwords live only in this call. */
    fun run(password: String, sudoPassword: String) {
        val s = _ui.value
        val key = s.hostKey ?: return
        if (s.step != AddServerStep.CONFIRM_KEY || s.triesLeft <= 0) return
        confirmed = key
        cancelled.set(false)
        _ui.update { it.copy(step = AddServerStep.RUNNING, log = "", message = null, notes = emptyList()) }
        job = tools.scope.launch {
            try {
                val pub = withContext(Dispatchers.Default) { keys.publicKey() }
                val script = ServerSetup.script(pub, sudoPassword)
                val r = withContext(Dispatchers.IO) {
                    SshRunner.execWithPassword(
                        s.host, s.port, s.user, password, key, ServerSetup.COMMAND, script, ServerSetup.TIMEOUT_MS,
                        onOutput = { chunk -> _ui.update { u -> u.copy(log = (u.log + chunk).takeLast(LOG_MAX)) } },
                        isCancelled = { cancelled.get() },
                    )
                }
                if (cancelled.get()) throw SshCancelled()
                val report = ServerSetup.parse(r.output)
                if (!report.installed) {
                    fail("The setup didn't finish, so the server wasn't added. The output above says why.", ServerSetup.notes(report, null).dropLast(1))
                    return@launch
                }
                // The phone key, not the password, must work before the server is added.
                val candidate = Server("", s.host, s.port, s.user, key.type, key.blob, key.fingerprint)
                val status = LauncherApi(config = { candidate.toServerConfig() }, identity = { keys.identity() }).status()
                onAdded(candidate, status)
                _ui.update {
                    it.copy(
                        step = AddServerStep.DONE,
                        message = "${status.hostname.ifBlank { s.host }} is added and is now the current server.",
                        notes = ServerSetup.notes(report, status.claude.loggedIn),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SshCancelled) {
                fail("Stopped. The server wasn't added (anything already installed there stays).")
            } catch (e: SshFailure) {
                if (e.kind == SshFailure.Kind.AUTH_FAILED) {
                    val left = _ui.value.triesLeft - 1
                    _ui.update {
                        it.copy(
                            step = AddServerStep.FORM, triesLeft = left, hostKey = null,
                            message = if (left > 0) "Wrong user name or password. $left ${if (left == 1) "try" else "tries"} left."
                            else "Wrong user name or password. That was the last try: close this and check the details.",
                        )
                    }
                } else {
                    fail(failureText(e))
                }
            } catch (e: ApiException) {
                fail("The scripts were installed, but the phone key didn't answer: ${friendly(e).first} The server wasn't added.")
            } catch (e: Exception) {
                fail("The setup stopped: ${e.message ?: e.javaClass.simpleName}. The server wasn't added.")
            }
        }
    }

    /** While running, the SSH read stops within a quarter second and the run ends as "Stopped"; nothing is added. */
    fun cancel() {
        cancelled.set(true)
        if (_ui.value.step == AddServerStep.PROBING) { job?.cancel(); _ui.update { it.copy(step = AddServerStep.FORM) } }
    }

    /** Back to the form after a failure (the tries left stay counted). */
    fun backToForm() = _ui.update { it.copy(step = AddServerStep.FORM, hostKey = null) }

    private fun stop() {
        cancelled.set(true)   // the SSH read stops within a quarter second
        job?.cancel()
    }

    private fun fail(message: String, notes: List<String> = emptyList()) =
        _ui.update { it.copy(step = AddServerStep.FAILED, message = message, notes = notes) }

    private companion object {
        const val LOG_MAX = 64 * 1024

        fun failureText(e: SshFailure) = when (e.kind) {
            SshFailure.Kind.HOST_KEY_CHANGED -> e.message + " Someone may be intercepting the connection."
            SshFailure.Kind.TIMEOUT -> "The server took too long. The server wasn't added."
            else -> (e.message ?: "The connection failed.") + " The server wasn't added."
        }
    }
}
