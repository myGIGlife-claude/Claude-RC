package life.mygig.clauderc.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import life.mygig.clauderc.api.ApiException
import life.mygig.clauderc.api.Codes
import life.mygig.clauderc.api.LauncherApi
import life.mygig.clauderc.api.LoginUrl
import life.mygig.clauderc.api.MIGRATE_USER_RE
import life.mygig.clauderc.api.MigratePlan
import life.mygig.clauderc.api.MigrateSession
import life.mygig.clauderc.api.MigrateStatus
import life.mygig.clauderc.api.VerifyResult
import life.mygig.clauderc.data.Server
import life.mygig.clauderc.data.SettingsStore
import life.mygig.clauderc.ssh.Ed25519Identity
import life.mygig.clauderc.ssh.SshKeyManager
import java.security.SecureRandom

/** The wizard's steps, in order (docs/migrate-design.md). */
enum class MigrateStep { CHOOSE, CHECK, STOP_SESSIONS, CREATE_USER, TRANSFER, RESTORE, SIGN_IN, SIGN_OUT, REBOOT, VERIFY, DONE }

/** One Claude account to sign in again on the new server: the main one, or a worker. */
data class MigrateSignIn(val name: String, val main: Boolean, val done: Boolean = false)

/** The sign-in page (and code, if any) the user is working through for [name]. */
data class MigrateSignInPrompt(val name: String, val main: Boolean, val login: LoginUrl)

/** What to remove from the old server once the new one works. */
data class MigrateSignOut(
    val claude: Boolean = false,
    val workers: Boolean = false,
    val github: Boolean = false,
    val autostart: Boolean = false,
) {
    fun items(): List<String> = listOfNotNull(
        "claude".takeIf { claude }, "workers".takeIf { workers }, "github".takeIf { github }, "autostart".takeIf { autostart },
    )
}

/** Everything the Migrate screens show. Never holds the passphrase or a sudo password. */
data class MigrateUi(
    /** The wizard is on screen. */
    val open: Boolean = false,
    val step: MigrateStep = MigrateStep.CHOOSE,
    val from: Server? = null,
    val to: Server? = null,
    val userName: String = "claude",
    val userNameValid: Boolean = true,
    val plan: MigratePlan? = null,
    /** Sessions still running on the old server (busy or waiting ones need the user's decision). */
    val sessions: List<MigrateSession> = emptyList(),
    /** root, nopasswd, password_ok, password_needed or none; null until checked on the new server. */
    val sudoMode: String? = null,
    val status: MigrateStatus? = null,
    /** What happened, newest last. */
    val log: List<String> = emptyList(),
    val error: String? = null,
    val busy: Boolean = false,
    val signInTargets: List<MigrateSignIn> = emptyList(),
    val signInPrompt: MigrateSignInPrompt? = null,
    val signOutChoices: MigrateSignOut = MigrateSignOut(),
    val verify: VerifyResult? = null,
    /** Also make a key for logging in from a computer (the private half is kept here until the user saves it). */
    val makeLoginKey: Boolean = true,
    /** The unencrypted OpenSSH private key to save, and the `user@host` it opens; null once saved or when none was made. */
    val loginKeyPem: String? = null,
    val loginKeyFor: String = "",
    /** The entry the new server was reached with before its own user existed (the second, duplicate row in the list). */
    val setupEntry: Server? = null,
)

private const val CODE_STEP = "migrate_step"
private const val CODE_SUDO = "migrate_sudo"
private const val CODE_FAILED = "migrate_failed"
private const val MANIFEST = "manifest.json"

/**
 * The Migrate wizard's logic. One piece of work runs at a time; a failed step leaves [MigrateUi.error] set and
 * [retry] runs the same step again (every step can resume where the servers say it stopped).
 * The backup passphrase and sudo passwords live only in memory here, never in [ui] or the log.
 */
class MigrateFlow(
    private val scope: CoroutineScope,
    private val store: SettingsStore,
    private val keys: SshKeyManager,
    private val apiFor: (Server) -> LauncherApi,
) {
    private val _ui = MutableStateFlow(MigrateUi())
    val ui: StateFlow<MigrateUi> = _ui.asStateFlow()

    private var job: Job? = null
    private var generation = 0
    private var retryBlock: (suspend () -> Unit)? = null

    // Memory only.
    private var passphrase: String? = null
    /** The file the new server received (set once the old server's export + transfer finished). */
    private var sentFile: String? = null
    private var restored = false

    // ---- Machinery ---------------------------------------------------------------

    /** Runs [block] as the one running piece of work; failures land in [MigrateUi.error]. */
    private fun work(block: suspend () -> Unit) {
        if (job?.isActive == true) return
        retryBlock = block
        val mine = ++generation
        _ui.update { it.copy(busy = true, error = null) }
        job = scope.launch {
            try {
                block()
            } catch (e: ApiException) {
                fail(e)
            } finally {
                if (mine == generation) _ui.update { it.copy(busy = false) }
            }
        }
    }

    private fun fail(e: ApiException) {
        val text = friendly(e).first
        _ui.update { it.copy(error = text + (e.pane?.takeIf { p -> p.isNotBlank() }?.let { p -> "\n\n$p" } ?: "")) }
        log("Stopped: $text")
    }

    private fun log(line: String) = _ui.update { it.copy(log = (it.log + line).takeLast(300)) }

    private fun setStep(step: MigrateStep) = _ui.update { it.copy(step = step) }

    private fun needStep(vararg steps: MigrateStep) {
        if (_ui.value.step !in steps) throw ApiException(CODE_STEP, "That isn't the next step. Go back to the current one.")
    }

    private fun servers(): Pair<Server, Server> {
        val s = _ui.value
        val from = s.from
        val to = s.to
        if (from == null || to == null || !from.isConfigured || !to.isConfigured) {
            throw ApiException(Codes.NOT_CONFIGURED, "Pick two servers that are set up in the app.")
        }
        return Pair(from, to)
    }

    fun retry() {
        retryBlock?.let { work(it) }
    }

    // ---- Open, close, choose -----------------------------------------------------

    fun open() {
        if (_ui.value.step == MigrateStep.DONE) reset(open = true) else _ui.update { it.copy(open = true) }
    }

    /** Leaves the screen: stops every running call here (a job already running on a server carries on). */
    fun close() {
        generation++
        job?.cancel()
        job = null
        _ui.update { it.copy(open = false, busy = false) }
    }

    /** Back to an empty wizard. */
    fun reset(open: Boolean = true) {
        generation++
        job?.cancel()
        job = null
        retryBlock = null
        passphrase = null
        sentFile = null
        restored = false
        _ui.value = MigrateUi(open = open)
    }

    fun setServers(fromId: String, toId: String) {
        scope.launch {
            if (_ui.value.step != MigrateStep.CHOOSE || job?.isActive == true) return@launch
            val list = store.current().servers
            _ui.update {
                it.copy(
                    from = list.firstOrNull { s -> s.id == fromId },
                    to = list.firstOrNull { s -> s.id == toId },
                    plan = null,
                    error = null,
                )
            }
        }
    }

    fun setUser(name: String) {
        if (_ui.value.step != MigrateStep.CHOOSE) return
        _ui.update { it.copy(userName = name, userNameValid = MIGRATE_USER_RE.matches(name), error = null) }
    }

    /** Back from the check to the choice. */
    fun edit() {
        if (job?.isActive == true) return
        if (_ui.value.step == MigrateStep.CHECK) _ui.update { it.copy(step = MigrateStep.CHOOSE, plan = null, error = null) }
    }

    fun setSignOutChoices(c: MigrateSignOut) = _ui.update { it.copy(signOutChoices = c) }

    fun setLoginKey(on: Boolean) = _ui.update { it.copy(makeLoginKey = on) }

    fun loginKeySaved() = _ui.update { it.copy(loginKeyPem = null) }

    /** Removes the setup entry of the new server from the list (not the one of the new user). */
    fun removeSetupEntry() {
        val s = _ui.value
        val setup = s.setupEntry ?: return
        if (setup.id == s.to?.id) return
        scope.launch {
            store.removeServer(setup.id)
            _ui.update { it.copy(setupEntry = null) }
            log("Removed ${setup.user}@${setup.host} from the server list.")
        }
    }

    // ---- 2. Check ----------------------------------------------------------------

    fun start() = work { checkPlan() }

    private suspend fun checkPlan() {
        needStep(MigrateStep.CHOOSE, MigrateStep.CHECK)
        val cur = _ui.value
        val list = store.current().servers
        // The entries may have been edited since they were picked.
        val from = cur.from?.let { f -> list.firstOrNull { it.id == f.id } }
        val to = cur.to?.let { t -> list.firstOrNull { it.id == t.id } }
        _ui.update { it.copy(from = from, to = to) }
        val (f, t) = servers()
        if (f.id == t.id || (f.host == t.host && f.port == t.port && f.user == t.user)) {
            throw ApiException(Codes.INVALID_NAME, "From and To must be two different servers.")
        }
        if (!MIGRATE_USER_RE.matches(cur.userName)) {
            throw ApiException(Codes.INVALID_NAME, "User names are lowercase letters, digits, '_' and '-', up to 31, starting with a letter.")
        }
        _ui.update { it.copy(step = MigrateStep.CHECK, plan = null) }
        log("Checking ${f.host}…")
        val plan = apiFor(f).migratePlan()
        _ui.update { it.copy(plan = plan) }
        log("Checked: ${plan.sessions.size} session(s), ${plan.repos.size} folder(s), about ${plan.estimateMb} MB.")
    }

    /** The user has seen the plan: on to stopping sessions. */
    fun confirmPlan() = work {
        needStep(MigrateStep.CHECK)
        if (_ui.value.plan == null) throw ApiException(CODE_STEP, "Check the old server first.")
        setStep(MigrateStep.STOP_SESSIONS)
        retryBlock = { checkSessions(stopIdle = true) }
        checkSessions(stopIdle = true)
    }

    // ---- 3. Stop sessions --------------------------------------------------------

    fun stopIdle() = work { checkSessions(stopIdle = true) }

    fun refreshSessions() = work { checkSessions(stopIdle = false) }

    fun stopSession(name: String) = work {
        needStep(MigrateStep.STOP_SESSIONS)
        val (f, _) = servers()
        apiFor(f).stop(name)
        log("Stopped $name")
        checkSessions(stopIdle = false)
    }

    private suspend fun fetchSessions(api: LauncherApi): List<MigrateSession> =
        api.sessions().sessions.map { MigrateSession(it.name, it.busy, it.waiting) }

    /** Stops idle sessions (if asked), then goes on only when nothing is left running on the old server. */
    private suspend fun checkSessions(stopIdle: Boolean) {
        needStep(MigrateStep.STOP_SESSIONS)
        val (f, _) = servers()
        val api = apiFor(f)
        var running = fetchSessions(api)
        if (stopIdle) {
            val idle = running.filter { !it.busy && !it.waiting }
            for (s in idle) {
                api.stop(s.name)
                log("Stopped ${s.name}")
            }
            if (idle.isNotEmpty()) running = fetchSessions(api)
        }
        _ui.update { it.copy(sessions = running) }
        if (running.isNotEmpty()) {
            log("${running.size} session(s) still running on ${f.host}.")
            return
        }
        afterSessions()
    }

    private suspend fun afterSessions() {
        val (_, t) = servers()
        if (t.user == _ui.value.userName) {
            log("${t.user} already exists on ${t.host}: nothing to create.")
            setStep(MigrateStep.TRANSFER)
            retryBlock = { transfer() }
        } else {
            setStep(MigrateStep.CREATE_USER)
            retryBlock = { checkSudo("") }
            try { checkSudo("") } catch (e: ApiException) { log("Couldn't check sudo yet: ${friendly(e).first}") }
        }
    }

    // ---- 4. Sudo, create the user ------------------------------------------------

    private suspend fun checkSudo(password: String): String {
        val (_, t) = servers()
        val mode = apiFor(t).migrateSudoCheck(password).mode
        _ui.update { it.copy(sudoMode = mode) }
        return mode
    }

    /** Public: tests [password] (blank = test without) and sets [MigrateUi.sudoMode]. */
    fun sudoCheck(password: String = "") = work {
        val mode = checkSudo(password)
        log(
            when (mode) {
                "root" -> "Signed in as root on the new server: no sudo needed."
                "nopasswd" -> "sudo works without a password on the new server."
                "password_ok" -> "The sudo password works."
                "password_needed" -> "sudo needs a password on the new server."
                else -> "This user can't use sudo on the new server."
            },
        )
        if (password.isNotBlank() && mode == "password_needed") throw ApiException(CODE_SUDO, "That sudo password wasn't accepted.")
    }

    /** The password to give a privileged action on the new server ("" when none is needed); throws when it can't work. */
    private suspend fun sudoFor(password: String): String {
        var mode = _ui.value.sudoMode ?: checkSudo("")
        if (mode == "password_needed" || mode == "password_ok") {
            if (password.isBlank()) throw ApiException(CODE_SUDO, "Enter the sudo password for the new server.")
            mode = checkSudo(password)   // test it before anything depends on it
            if (mode == "password_needed") throw ApiException(CODE_SUDO, "That sudo password wasn't accepted.")
        }
        return when (mode) {
            "root", "nopasswd" -> ""
            "password_ok" -> password
            else -> throw ApiException(
                CODE_SUDO,
                "This user can't use sudo on the new server. Add it to the sudo group there, or sign in as root, then try again.",
            )
        }
    }

    private suspend fun probe(s: Server): Boolean = try {
        apiFor(s).status()
        true
    } catch (e: ApiException) {
        if (e.code == Codes.HOST_KEY_CHANGED || e.code == Codes.KEY_ERROR) throw e
        false
    }

    fun createUser(sudoPassword: String) = work {
        needStep(MigrateStep.CREATE_USER, MigrateStep.TRANSFER)
        val (_, t) = servers()
        val name = _ui.value.userName
        if (t.user == name) {
            setStep(MigrateStep.TRANSFER)
            retryBlock = { transfer() }
            return@work
        }
        // An entry from an earlier try (or one the user added) is reused, never duplicated.
        val existing = store.current().servers.firstOrNull {
            it.host == t.host && it.port == t.port && it.user == name && it.hostKeyBlob == t.hostKeyBlob && it.isConfigured
        }
        val candidate = existing ?: t.copy(user = name, previousFingerprint = "", inCluster = null)
        if (probe(candidate)) {
            log("$name already answers on ${t.host}.")
        } else {
            val pw = sudoFor(sudoPassword)
            val phoneKey = withContext(Dispatchers.Default) { keys.publicKey() }
            // A new key pair for logging in from a computer: the public half goes to the server, the private half is
            // shown to the user to save (never stored by the app).
            var loginPub: String? = null
            var pem: String? = null
            if (_ui.value.makeLoginKey) {
                val seed = ByteArray(32).also { SecureRandom().nextBytes(it) }
                try {
                    val id = Ed25519Identity(seed)
                    loginPub = id.authorizedKey("clauderc-login")
                    pem = String(id.privatePem())
                } finally {
                    seed.fill(0)
                }
            }
            log("Creating $name on ${t.host} (installs tools and Claude Code; this takes a few minutes)…")
            apiFor(t).migrateCreateUser(name, pw, phoneKey, loginPub)
            log("Created $name.")
            // Shown only now: the key opens nothing until the server has it.
            pem?.let { k -> _ui.update { it.copy(loginKeyPem = k, loginKeyFor = "$name@${t.host}") } }
        }
        val entry: Server = existing ?: store.addServerEntry(candidate)
        apiFor(entry).status()   // the new entry must answer before it is used
        _ui.update { it.copy(to = entry, sudoMode = null, setupEntry = t.takeIf { s -> s.id != entry.id }) }
        log("${entry.host} as $name is in the server list.")
        setStep(MigrateStep.TRANSFER)
        retryBlock = { transfer() }
    }

    // ---- 5. Transfer, 6. Restore -------------------------------------------------

    fun transferNow() = work { transfer() }

    /** Polls `migrate-status` on [api] every 2 s until the [jobName] job is done; throws if it failed. */
    private suspend fun poll(
        api: LauncherApi,
        who: String,
        jobName: String,
        staleStarted: JsonElement?,
        onStatus: suspend (MigrateStatus) -> Unit = {},
    ): MigrateStatus {
        val t0 = System.currentTimeMillis()
        var failures = 0
        var lastMessage = ""
        while (true) {
            val st = try {
                api.migrateStatus()
            } catch (e: ApiException) {
                failures++
                if ((e.code != Codes.NETWORK && e.code != Codes.TIMEOUT) || failures > 5) throw e
                delay(2_000)
                continue
            }
            failures = 0
            val age = System.currentTimeMillis() - t0
            // Right after a start the file may still show the previous job: don't believe it for a moment.
            val stale = st.job != jobName || (staleStarted != null && st.started == staleStarted && age < 20_000)
            if (stale) {
                if (age > 90_000) throw ApiException(CODE_FAILED, "$who isn't reporting the job. Try again.")
            } else {
                _ui.update { it.copy(status = st) }
                if (st.message.isNotBlank() && st.message != lastMessage) {
                    lastMessage = st.message
                    log("$who: ${st.message}")
                }
                if (st.phase == "failed") {
                    throw ApiException(CODE_FAILED, st.error?.takeIf { it.isNotBlank() } ?: st.message.ifBlank { "It failed on $who." })
                }
                if (st.phase == "done") return st
                onStatus(st)
            }
            delay(2_000)
        }
    }

    private suspend fun fetchPassphrase(from: LauncherApi): String? = try {
        from.migratePassphrase().passphrase.takeIf { it.isNotBlank() }
    } catch (e: ApiException) {
        null   // not ready yet
    }

    private suspend fun transfer() {
        needStep(MigrateStep.TRANSFER, MigrateStep.RESTORE)
        val (f, t) = servers()
        if (t.user != _ui.value.userName) throw ApiException(CODE_STEP, "Create the user on the new server first.")
        val fromApi = apiFor(f)
        if (sentFile == null) {
            setStep(MigrateStep.TRANSFER)
            val before = fromApi.migrateStatus()
            val running = before.job == "send" && (before.phase == "export" || before.phase == "transfer")
            if (running) {
                log("A transfer from ${f.host} is already running: following it.")
            } else {
                passphrase = null
                log("Preparing the transfer…")
                val key = fromApi.migrateKeygen().publicKey
                apiFor(t).migrateAuthorize(key, f.host)
                fromApi.migrateSend(t.host, t.port, t.user, t.hostKeyType, t.hostKeyBlob)
                log("Backup started on ${f.host}.")
            }
            val done = poll(fromApi, f.host, "send", if (running) null else before.started) { st ->
                if (passphrase == null && st.phase != "failed") passphrase = fetchPassphrase(fromApi)
            }
            if (done.file.isBlank()) throw ApiException(CODE_FAILED, "The new server didn't say which file it received.")
            sentFile = done.file
            log("The backup reached ${t.host}.")
        }
        restore()
    }

    private suspend fun restore() {
        val file = sentFile ?: throw ApiException(CODE_STEP, "Transfer first.")
        val (f, t) = servers()
        setStep(MigrateStep.RESTORE)
        retryBlock = { restore() }
        val toApi = apiFor(t)
        val before = toApi.migrateStatus()
        val sameFile = before.file == file
        val running = before.job == "restore" && before.phase == "restore" && (sameFile || before.file.isBlank())
        val alreadyDone = before.job == "restore" && before.phase == "done" && sameFile
        if (!alreadyDone) {
            if (running) {
                log("A restore on ${t.host} is already running: following it.")
            } else {
                // The old server still has the passphrase; ask again so it always matches this backup.
                val pass = fetchPassphrase(apiFor(f)) ?: passphrase
                    ?: throw ApiException(CODE_FAILED, "The backup passphrase isn't available. Start the transfer again.")
                passphrase = pass
                toApi.migrateRestore(file, pass)
                log("Restore started on ${t.host}.")
            }
            poll(toApi, t.host, "restore", if (running) null else before.started)
        }
        passphrase = null
        restored = true
        log("Everything is restored on ${t.host}.")
        prepareSignIn()
    }

    // ---- 7. Sign in on the new server --------------------------------------------

    private suspend fun prepareSignIn() {
        val (_, t) = servers()
        retryBlock = { prepareSignIn() }
        val api = apiFor(t)
        val logins = _ui.value.plan?.logins
        val mainDone = try { api.status().claude.loggedIn } catch (e: ApiException) { false }
        val workersDone = try {
            api.workers().workers.filter { it.signedIn }.map { it.name }.toSet()
        } catch (e: ApiException) {
            emptySet()
        }
        val targets = buildList {
            if (logins?.claude == true) add(MigrateSignIn("Claude", true, mainDone))
            logins?.workers?.forEach { add(MigrateSignIn(it, false, it in workersDone)) }
        }
        _ui.update { it.copy(step = MigrateStep.SIGN_IN, signInTargets = targets, signInPrompt = null) }
        log(if (targets.isEmpty()) "No Claude sign-ins to move." else "Sign in to Claude again on ${t.host} (${targets.size} account(s)).")
    }

    fun signInStart(name: String, main: Boolean) = work { startSignIn(name, main) }

    private suspend fun startSignIn(name: String, main: Boolean) {
        needStep(MigrateStep.SIGN_IN)
        val (_, t) = servers()
        val api = apiFor(t)
        val login = if (main) api.loginClaudeStart() else api.workerLoginStart(name)
        _ui.update { it.copy(signInPrompt = MigrateSignInPrompt(name, main, login)) }
    }

    fun signInCode(name: String, main: Boolean, code: String) = work {
        needStep(MigrateStep.SIGN_IN)
        retryBlock = { startSignIn(name, main) }   // a retry gets a fresh sign-in page
        val (_, t) = servers()
        val api = apiFor(t)
        if (main) api.loginClaudeCode(code) else api.workerLoginCode(name, code)
        _ui.update { s ->
            s.copy(
                signInTargets = s.signInTargets.map { x -> if (x.name == name && x.main == main) x.copy(done = true) else x },
                signInPrompt = null,
            )
        }
        log("Signed in $name on ${t.host}.")
    }

    fun signInCancel() {
        val prompt = _ui.value.signInPrompt ?: return
        _ui.update { it.copy(signInPrompt = null, error = null) }
        if (!prompt.main) return
        val t = _ui.value.to ?: return
        scope.launch { try { apiFor(t).loginClaudeCancel() } catch (_: ApiException) { } }
    }

    /** On to signing out of the old server; unfinished sign-ins only with [skipPending]. */
    fun signInFinish(skipPending: Boolean) = work {
        needStep(MigrateStep.SIGN_IN)
        val pending = _ui.value.signInTargets.filter { !it.done }
        if (pending.isNotEmpty() && !skipPending) {
            throw ApiException(CODE_STEP, "Still to sign in: ${pending.joinToString(", ") { it.name }}.")
        }
        if (pending.isNotEmpty()) log("Skipped sign-in for ${pending.joinToString(", ") { it.name }}: do it later in the app.")
        _ui.update { it.copy(step = MigrateStep.SIGN_OUT, signInPrompt = null) }
        retryBlock = null
    }

    // ---- 8. Sign out on the old server -------------------------------------------

    /** Removes the ticked [items] from the old server (none = skip), then on to the reboot. */
    fun signOut(items: List<String>) = work {
        needStep(MigrateStep.SIGN_OUT)
        val (f, _) = servers()
        if (items.isNotEmpty()) {
            apiFor(f).migrateSignoutOld(items)
            log("Signed out of ${f.host}: ${items.joinToString(", ")}.")
        } else {
            log("Left ${f.host} as it is.")
        }
        setStep(MigrateStep.REBOOT)
        retryBlock = null
        try { checkSudo("") } catch (e: ApiException) { log("Couldn't check sudo yet: ${friendly(e).first}") }
    }

    // ---- 9. Reboot ---------------------------------------------------------------

    fun reboot(sudoPassword: String) = work {
        needStep(MigrateStep.REBOOT)
        val (_, t) = servers()
        val pw = sudoFor(sudoPassword)
        log("Rebooting ${t.host}…")
        try {
            apiFor(t).migrateReboot(pw)
        } catch (e: ApiException) {
            // The connection may drop as the server goes down.
            if (e.code != Codes.NETWORK && e.code != Codes.TIMEOUT) throw e
        }
        retryBlock = { waitForServer() }   // the command was sent: a retry only waits
        waitForServer()
    }

    private suspend fun waitForServer() {
        val (_, t) = servers()
        val api = apiFor(t)
        delay(10_000)   // let it go down first
        val deadline = System.currentTimeMillis() + 5 * 60_000L
        while (true) {
            try {
                api.status()
                break
            } catch (e: ApiException) {
                if (e.code == Codes.HOST_KEY_CHANGED || e.code == Codes.KEY_ERROR) throw e
                if (System.currentTimeMillis() > deadline) {
                    throw ApiException(Codes.TIMEOUT, "${t.host} didn't come back within 5 minutes.")
                }
            }
            delay(5_000)
        }
        log("${t.host} is back.")
        setStep(MigrateStep.VERIFY)
        retryBlock = { verify() }
        verify()
    }

    fun skipReboot() = work {
        needStep(MigrateStep.REBOOT)
        log("No reboot.")
        setStep(MigrateStep.VERIFY)
        retryBlock = { verify() }
        verify()
    }

    // ---- 10. Verify --------------------------------------------------------------

    fun verifyNow() = work { verify() }

    /** After the restore: signs GitHub in on the new server with [token], then checks again. */
    fun githubSignIn(token: String) = work {
        needStep(MigrateStep.VERIFY, MigrateStep.DONE)
        val (_, t) = servers()
        apiFor(t).loginGithub(token)
        log("Signed in to GitHub on ${t.host}.")
        verify()
    }

    /** Clones the repos the restore couldn't, then checks again. */
    fun cloneMissing() = work {
        needStep(MigrateStep.VERIFY, MigrateStep.DONE)
        val (_, t) = servers()
        if (!restored) throw ApiException(CODE_STEP, "There is no restore to clone from (this wizard didn't run one).")
        log("Cloning the missing repos on ${t.host}…")
        apiFor(t).migrateClone(MANIFEST)
        log("Cloned.")
        verify()
    }

    private suspend fun verify() {
        needStep(MigrateStep.VERIFY, MigrateStep.DONE)
        val (_, t) = servers()
        val result = apiFor(t).migrateVerify(if (restored) MANIFEST else null)
        _ui.update { it.copy(verify = result) }
        if (result.ok) {
            log("Everything checks out on ${t.host}.")
            _ui.update { it.copy(step = MigrateStep.DONE) }
            passphrase = null
            retryBlock = null
        } else {
            val missing = result.items.filter { !it.ok }
            log("Not finished: ${missing.joinToString("; ") { it.id }}.")
        }
    }
}
