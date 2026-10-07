package life.mygig.clauderc.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import life.mygig.clauderc.ssh.Ed25519Identity
import life.mygig.clauderc.ssh.ServerConfig
import life.mygig.clauderc.ssh.SshFailure
import life.mygig.clauderc.ssh.SshRunner

/** An error the app can act on: server error codes plus local connection failures. */
class ApiException(
    val code: String,
    override val message: String,
    val missing: List<String> = emptyList(),
    val path: String? = null,
    /** Terminal text the server captured from a login it was driving. */
    val pane: String? = null,
) : Exception(message)

object Codes {
    const val NOT_LOGGED_IN_CLAUDE = "not_logged_in_claude"
    const val NOT_LOGGED_IN_GITHUB = "not_logged_in_github"
    const val MISSING_SCOPES = "missing_scopes"
    const val NOT_LOGGED_IN_AWS = "not_logged_in_aws"
    const val REPO_EXISTS = "repo_exists"
    const val FOLDER_DIRTY = "folder_dirty"
    const val INVALID_NAME = "invalid_name"
    const val BUSY = "busy"
    const val INTERNAL = "internal"
    const val FORBIDDEN = "forbidden"
    const val SESSION_BUSY = "session_busy"

    // Local, never sent by the server.
    const val HOST_KEY_CHANGED = "host_key_changed"
    const val AUTH_FAILED = "auth_failed"
    const val NETWORK = "network"
    const val TIMEOUT = "timeout"
    const val NOT_CONFIGURED = "not_configured"
    const val BAD_RESPONSE = "bad_response"
    const val KEY_ERROR = "key_error"
}

/** Same rule as the server: no leading '-', so a name is never read as an option. */
val PROJECT_NAME_RE = Regex("^[A-Za-z0-9._][A-Za-z0-9._-]{0,99}$")

/** The server's rule for a `skills` package (owner/repo). */
val SKILLS_SOURCE_RE = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")

/** Same rule as the server's worker names (Team). */
val WORKER_NAME_RE = Regex("^[A-Za-z][A-Za-z0-9_-]{0,29}$")

/** Same rules as the server's host actions (docs/hosts-design.md). */
val HOST_NAME_RE = Regex("^[a-z][a-z0-9-]{0,29}$")
val HOST_ADDRESS_RE = Regex("^[A-Za-z0-9]([A-Za-z0-9.-]{0,251}[A-Za-z0-9])?$")
val HOST_IPV6_RE = Regex("^[0-9A-Fa-f:.]{2,45}$")
val HOST_USER_RE = Regex("^[a-z_][a-z0-9_-]{0,31}$")
val HOST_FINGERPRINT_RE = Regex("^SHA256:[A-Za-z0-9+/]{43}$")
fun hostAddressOk(address: String) = HOST_ADDRESS_RE.matches(address) || (':' in address && HOST_IPV6_RE.matches(address))
/** A port for host-harden to open: `8080` or `51820/udp`, 1-65535; at most [HOST_MAX_PORTS] per call. */
val HOST_PORT_RE = Regex("^[0-9]{1,5}(/(tcp|udp))?$")
const val HOST_MAX_PORTS = 20
val HOST_HARDEN_STEPS = setOf("harden", "optimize", "web")
fun hostPortOk(port: String) = HOST_PORT_RE.matches(port) && port.substringBefore('/').toInt() in 1..65535
/** "8080, 51820/udp" as a list (blank = empty); null if any entry is bad. */
fun parseHostPorts(text: String): List<String>? {
    val ports = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    return if (ports.all { hostPortOk(it) }) ports else null
}

/** Same rules as the server's migrate actions (docs/migrate-design.md). */
val MIGRATE_USER_RE = Regex("^[a-z][a-z0-9_-]{0,30}$")
val MIGRATE_FILE_RE = Regex("^incoming-[0-9]{8}-[0-9]{6}(-[0-9]{1,3})?\\.gpg$")
private val MIGRATE_ITEMS = setOf("claude", "workers", "github", "autostart")
private val MIGRATE_HOST_RE = Regex("^[A-Za-z0-9._:-]{1,253}$")
private val MIGRATE_SSH_USER_RE = Regex("^[A-Za-z0-9._][A-Za-z0-9._-]{0,63}$")
private val MIGRATE_KEY_TYPE_RE = Regex("^[A-Za-z0-9@.-]{1,64}$")
private val MIGRATE_KEY_BLOB_RE = Regex("^[A-Za-z0-9+/=]{1,8192}$")
private val MIGRATE_MANIFEST_RE = Regex("^[A-Za-z0-9._-]{1,100}$")

/** Typed calls to `claude-launcher-api` on the server. */
class LauncherApi(
    private val config: suspend () -> ServerConfig,
    private val identity: () -> Ed25519Identity,
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    suspend fun status(): StatusData = call("status")
    suspend fun owners(): OwnersData = call("owners")
    suspend fun repos(refresh: Boolean): ReposData = call(if (refresh) "repos --refresh" else "repos")
    suspend fun sessions(): SessionsData = call("sessions")

    suspend fun newProject(name: String, owner: String, private: Boolean, start: Boolean): NewResult {
        requireName(name)
        val vis = if (private) "private" else "public"
        // Server side: repo check + create + clone + first Remote Control prompt.
        return call("new $name --owner $owner --visibility $vis" + if (start) " --start" else "", timeoutMs = 180_000)
    }

    suspend fun open(fullName: String, start: Boolean): OpenResult =
        call("open $fullName" + if (start) " --start" else "", timeoutMs = 120_000)

    suspend fun repoDelete(fullName: String): JsonObject = call("repo-edit delete $fullName", timeoutMs = 90_000)
    suspend fun repoRename(fullName: String, name: String): JsonObject { requireName(name); return call("repo-edit rename $fullName $name", timeoutMs = 90_000) }
    suspend fun repoVisibility(fullName: String, private: Boolean): JsonObject =
        call("repo-edit visibility $fullName " + if (private) "private" else "public", timeoutMs = 90_000)

    suspend fun cloneStatus(fullName: String): CloneStatus = call("clone-status $fullName")

    suspend fun start(project: String): StartResult { requireName(project); return call("start $project") }
    suspend fun stop(project: String): StopResult { requireName(project); return call("stop $project") }
    suspend fun tail(project: String, lines: Int = 40): TailResult {
        requireName(project)
        return call("tail $project --lines ${lines.coerceIn(1, 200)}")
    }

    suspend fun loginClaudeStart(): LoginUrl = call("login-claude-start")
    suspend fun loginClaudeCode(code: String): LoginDone = call("login-claude-code", stdin = code.trim(), timeoutMs = 120_000)
    suspend fun loginClaudeCancel(): JsonObject = call("login-claude-cancel")
    suspend fun loginGithub(token: String): LoginDone = call("login-github", stdin = token.trim())
    suspend fun loginAwsKeys(keyId: String, secret: String, region: String): LoginDone =
        call("login-aws-keys", stdin = listOf(keyId.trim(), secret.trim(), region.trim()).joinToString("\n"))
    suspend fun loginAwsSsoStart(): LoginUrl = call("login-aws-sso-start")
    suspend fun loginGitlab(token: String, host: String): LoginDone =
        call("login-gitlab", stdin = listOf(token.trim(), host.trim()).joinToString("\n"))
    suspend fun loginDocker(registry: String, user: String, token: String): LoginDone =
        call("login-docker", stdin = listOf(registry.trim(), user.trim(), token.trim()).joinToString("\n"))
    suspend fun selfUpdate(commit: String): JsonObject {
        if (!Regex("^[0-9a-f]{40}$").matches(commit)) throw ApiException(Codes.INVALID_NAME, "Bad commit")
        return call("self-update $commit", timeoutMs = 240_000)
    }
    /** Runs [command] on the server; the sudo password and command go on stdin only. */
    suspend fun run(command: String, sudoPassword: String, timeoutSec: Int = 120): RunResult =
        call("run", stdin = sudoPassword + "\n" + timeoutSec + "\n" + command, timeoutMs = (timeoutSec + 30) * 1000L)
    /** Sends allowlisted keys (1-9, Enter, Escape, arrows…) to a session; returns the screen after. */
    suspend fun keys(project: String, vararg keys: String): TailResult {
        requireName(project)
        return call("keys $project " + keys.joinToString(" "))
    }
    suspend fun restart(project: String, force: Boolean = false): JsonObject {
        requireName(project)
        return call("restart $project" + if (force) " --force" else "", timeoutMs = 120_000)
    }
    /** `claude <args>` from the Command Center's allowlist; args go on stdin. */
    suspend fun claudeCmd(args: String): RunResult = call("claude-cmd", stdin = args.trim(), timeoutMs = 330_000)
    /** Installs a CLI on the server; [sudoPassword] only for installs that need root (docker). */
    /** Saves a custom API key (value on stdin) that every Claude session and MCP server sees. */
    suspend fun setSecret(name: String, value: String): JsonObject = call("set-secret $name", stdin = value.trim())
    /** Saves a JSON file (base64 on stdin) on the server; the custom key [name] then holds its path. */
    suspend fun setSecretFile(name: String, fileBase64: String): JsonObject = call("set-secret-file $name", stdin = fileBase64)
    /** Saves an Android signing key: alias, passwords and the file (base64) go on stdin. */
    /** Google device sign-in for YouTube: client ID and secret on stdin; returns the code to enter. */
    suspend fun youtubeStart(clientId: String, clientSecret: String): YoutubeStart =
        call("youtube-login-start", stdin = clientId.trim() + "\n" + clientSecret.trim())
    suspend fun youtubePoll(): JsonObject = call("youtube-login-poll")
    suspend fun driveStatus(): DriveStatus = call("drive-status")
    suspend fun driveLoginStart(): DriveLoginStart = call("drive-login-start")
    suspend fun driveLoginPoll(): JsonObject = call("drive-login-poll")
    suspend fun drivePassphrase(value: String): JsonObject = call("drive-passphrase", stdin = value)
    suspend fun driveBackup(): JsonObject = call("drive-backup")
    suspend fun driveBackupStatus(): DriveBackupStatus = call("drive-backup-status")
    suspend fun driveList(): List<DriveBackupEntry> = call("drive-list")
    suspend fun driveDelete(id: String): JsonObject = call("drive-delete $id")
    suspend fun driveSchedule(on: Boolean): JsonObject = call("drive-schedule ${if (on) "on" else "off"}")
    suspend fun driveLogout(): JsonObject = call("drive-logout")
    suspend fun driveRestore(id: String): JsonObject = call("drive-restore $id")
    suspend fun mcp(): McpData = call("mcp")
    suspend fun pushConfig(): PushConfig = call("push-config")
    /** Saves the Firebase service-account key (stdin); the server registers the app and returns its ids. */
    suspend fun pushSetup(key: String): PushConfig = call("push-setup", stdin = key.trim(), timeoutMs = 120_000)
    suspend fun pushRegister(token: String): JsonObject = call("push-register $token")
    suspend fun pushSession(session: String, on: Boolean): JsonObject { requireName(session); return call("push-session $session ${if (on) "on" else "off"}") }
    suspend fun pushTest(): PushTestResult = call("push-test", timeoutMs = 60_000)
    suspend fun doctorStart(): StartResult = call("doctor-start")
    suspend fun mcpRefresh(): McpData = call("mcp-refresh", timeoutMs = 150_000)
    suspend fun mcpAuthStart(name: String): LoginUrl = call("mcp-auth-start", stdin = name, timeoutMs = 240_000)
    suspend fun mcpAuthFinish(callbackUrl: String): McpData = call("mcp-auth-finish", stdin = callbackUrl.trim(), timeoutMs = 180_000)
    suspend fun mcpAuthCancel(): JsonObject = call("mcp-auth-cancel")

    suspend fun cluster(): ClusterData = call("cluster", timeoutMs = 30_000)
    suspend fun clusterConfig(): ClusterConfig = call("cluster-config")
    suspend fun clusterConfigSet(key: String, value: String): ClusterConfig {
        require(key in listOf("max_parallel", "handback", "handback_pct") && value.all { it.isLetterOrDigit() })
        return call("cluster-config-set $key $value")
    }

    // Team: extra Claude accounts the main Claude can delegate to.
    suspend fun workers(): WorkersData = call("worker-list")
    suspend fun workerAdd(name: String, role: String, kind: String = "claude"): JsonObject {
        requireWorker(name)
        require(kind in listOf("claude", "codex", "gemini"))
        return call("worker-add $name $kind", stdin = role.trim().replace('\n', ' '))
    }
    suspend fun workerSet(name: String, field: String, value: String): JsonObject {
        requireWorker(name)
        if (field != "role" && field != "mode") throw ApiException(Codes.INVALID_NAME, "Bad field")
        return call("worker-set $name $field", stdin = value.trim().replace('\n', ' '))
    }
    /** Gemini workers sign in with an API key from Google AI Studio (the key travels on stdin only). */
    suspend fun workerSetKey(name: String, key: String): JsonObject { requireWorker(name); return call("worker-set-key $name", stdin = key.trim(), timeoutMs = 300_000) }
    suspend fun workerRemove(name: String): JsonObject { requireWorker(name); return call("worker-remove $name") }
    suspend fun workerLoginStart(name: String): LoginUrl { requireWorker(name); return call("worker-login-start $name", timeoutMs = 60_000) }
    suspend fun workerLoginCode(name: String, code: String): LoginDone {
        requireWorker(name)
        return call("worker-login-code $name", stdin = code.trim(), timeoutMs = 120_000)
    }
    suspend fun workerRuns(name: String): WorkerRunsData { requireWorker(name); return call("worker-runs $name") }
    // Which workers one chat's project hands work to.
    suspend fun clusterSession(session: String): ChatWorkersData { requireName(session); return call("cluster-session $session") }
    suspend fun clusterAttach(session: String, worker: String, on: Boolean): JsonObject {
        requireName(session); requireWorker(worker)
        return call("cluster-attach $session $worker ${if (on) "on" else "off"}")
    }
    suspend fun clusterAssign(session: String, worker: String, field: String, value: String): JsonObject {
        requireName(session); requireWorker(worker)
        require(field == "role" || field == "mode")
        return call("cluster-assign $session $worker $field", stdin = value.trim().replace('\n', ' '))
    }
    private fun requireWorker(name: String) {
        if (!WORKER_NAME_RE.matches(name)) throw ApiException(Codes.INVALID_NAME, "Bad worker name")
    }

    // Hosts: web servers chats can work on. Secrets go on stdin only.
    suspend fun hostList(): HostsData = call("host-list")
    suspend fun hostProbe(address: String, port: Int): HostProbe {
        requireHostAddress(address, port)
        return call("host-probe", stdin = address + "\n" + port, timeoutMs = 30_000)
    }
    /** A key pair for [name] (the same one again until it is added); only the public half comes back. */
    suspend fun hostKeygen(name: String): HostKey { requireHost(name); return call("host-keygen $name") }
    /** The owner's own private key ([keyBase64], one line) as [name]'s pending key, used like [hostKeygen]'s; only the public half comes back. */
    suspend fun hostKeyImport(name: String, keyBase64: String): HostKey {
        requireHost(name)
        if (keyBase64.isEmpty() || keyBase64.any { it == '\n' || it == '\r' }) throw ApiException(Codes.INVALID_NAME, "Bad key")
        return call("host-keyimport $name", stdin = keyBase64)
    }
    /** [auth] is "key" (secret = the private key, base64 on one line), "password" (used once, never stored)
     *  or "generated" (the key from [hostKeygen]; secret is a placeholder). */
    suspend fun hostAdd(name: String, address: String, port: Int, user: String, fingerprint: String, auth: String, secret: String): JsonObject {
        requireHost(name)
        requireHostAddress(address, port)
        requireMigrate(HOST_USER_RE, user, "User names are lowercase letters, digits, '_' and '-', up to 32.")
        requireMigrate(HOST_FINGERPRINT_RE, fingerprint, "Bad host key fingerprint")
        if (auth != "key" && auth != "password" && auth != "generated") throw ApiException(Codes.INVALID_NAME, "Bad sign-in method")
        if (secret.isEmpty() || secret.any { it == '\n' || it == '\r' }) throw ApiException(Codes.INVALID_NAME, "Bad key or password")
        return call("host-add $name", stdin = listOf(address, port.toString(), user, fingerprint, auth, secret).joinToString("\n"), timeoutMs = 120_000)
    }
    suspend fun hostTest(name: String): JsonObject { requireHost(name); return call("host-test $name", timeoutMs = 60_000) }
    /** Opens [ports] in the host's firewall and runs [steps] (harden, optimize, web) over its ssh with sudo. */
    suspend fun hostHarden(name: String, ports: List<String>, steps: List<String>): HostHardenResult {
        requireHost(name)
        if (ports.size > HOST_MAX_PORTS || !ports.all { hostPortOk(it) }) throw ApiException(Codes.INVALID_NAME, "Ports are like 8080 or 51820/udp (1-65535), up to $HOST_MAX_PORTS.")
        if (!steps.all { it in HOST_HARDEN_STEPS }) throw ApiException(Codes.INVALID_NAME, "Bad setup step")
        return call("host-harden $name", stdin = ports.joinToString(",") + "\n" + steps.joinToString(","), timeoutMs = 600_000)
    }
    suspend fun hostRemove(name: String): JsonObject { requireHost(name); return call("host-remove $name") }
    suspend fun hostSession(project: String): ChatHostsData { requireName(project); return call("host-session $project") }
    suspend fun hostAttach(project: String, name: String, on: Boolean): JsonObject {
        requireName(project); requireHost(name)
        return call("host-attach $project $name ${if (on) "on" else "off"}")
    }
    private fun requireHost(name: String) {
        if (!HOST_NAME_RE.matches(name)) throw ApiException(Codes.INVALID_NAME, "Host names are lowercase letters, digits and '-', up to 30, starting with a letter.")
    }
    private fun requireHostAddress(address: String, port: Int) {
        if (!hostAddressOk(address)) throw ApiException(Codes.INVALID_NAME, "Bad server address")
        if (port !in 1..65535) throw ApiException(Codes.INVALID_NAME, "Bad port")
    }
    suspend fun plugins(): PluginsData = call("plugins", timeoutMs = 120_000)
    suspend fun skillsUpdate(repo: String): JsonObject {
        if (!SKILLS_SOURCE_RE.matches(repo)) throw ApiException(Codes.INVALID_NAME, "Bad skills source")
        return call("skills-update $repo", timeoutMs = 300_000)
    }
    suspend fun disconnect(service: String): JsonObject = call("disconnect $service")

    // In-app chat: every call carries the PIN on stdin; the server checks it.
    suspend fun chatPinStatus(): PinStatus = call("chat-pin-status")
    suspend fun chatPinSet(newPin: String, oldPin: String = ""): JsonObject = call("chat-pin-set", stdin = newPin + "\n" + oldPin)
    suspend fun chatOpen(session: String, pin: String): JsonObject { requireName(session); return call("chat-open $session", stdin = pin) }
    suspend fun chatHistory(session: String, pin: String): ChatData { requireName(session); return call("chat-history $session", stdin = pin) }
    suspend fun chatSend(session: String, pin: String, text: String): JsonObject {
        requireName(session)
        return call("chat-send $session", stdin = pin + "\n" + text)
    }
    /** Sends a file into the session's project (uploads/<name>); PIN-protected like chat. */
    suspend fun upload(session: String, pin: String, name: String, base64: String): UploadResult {
        requireName(session)
        return call("upload $session", stdin = pin + "\n" + name + "\n" + base64, timeoutMs = 240_000)
    }
    suspend fun chatFile(session: String, pin: String, path: String): ChatFile {
        requireName(session)
        return call("chat-file $session", stdin = pin + "\n" + path, timeoutMs = 120_000)
    }
    suspend fun chatLog(): List<ChatLogEntry> = call("chat-log")
    suspend fun chatCommands(session: String, pin: String): ChatCommands { requireName(session); return call("chat-commands $session", stdin = pin) }
    suspend fun chatInterrupt(session: String, pin: String): JsonObject { requireName(session); return call("chat-interrupt $session", stdin = pin) }
    suspend fun loginKeystore(name: String, alias: String, storePassword: String, keyPassword: String, fileBase64: String): JsonObject =
        call("login-keystore $name", stdin = listOf(alias.trim(), storePassword, keyPassword, fileBase64).joinToString("\n"))
    /** Saves an App Store Connect API key: key ID, issuer ID, team ID and the .p8 (base64) go on stdin. */
    suspend fun loginApple(keyId: String, issuerId: String, teamId: String, fileBase64: String): JsonObject =
        call("login-apple", stdin = listOf(keyId.trim(), issuerId.trim(), teamId.trim(), fileBase64).joinToString("\n"))
    suspend fun removeApple(): JsonObject = call("remove-apple")
    suspend fun removeKeystore(name: String): JsonObject = call("remove-keystore $name")
    suspend fun removeSecret(name: String): JsonObject = call("remove-secret $name")
    suspend fun installCli(name: String, sudoPassword: String = ""): JsonObject =
        call("install-cli $name", stdin = sudoPassword, timeoutMs = 660_000)
    /** Saves a token service's credential; one value per field, or the whole JSON key. */
    suspend fun loginToken(id: String, values: List<String>): LoginDone =
        call("login-token $id", stdin = values.joinToString("\n") { it.trim() })

    // Migrate: move everything to another server. Secrets go on stdin only.
    suspend fun migratePlan(): MigratePlan = call("migrate-plan", timeoutMs = 180_000)
    suspend fun migrateKeygen(): PublicKey = call("migrate-keygen")
    /** On the new server: lets the old one send its backup ([fromAddress] is the old server's address). */
    suspend fun migrateAuthorize(publicKey: String, fromAddress: String): JsonObject {
        val key = publicKey.trim()
        if (!key.startsWith("ssh-") || key.any { it == '\n' || it == '\r' }) throw ApiException(Codes.INVALID_NAME, "Bad migrate key")
        requireMigrate(MIGRATE_HOST_RE, fromAddress.trim(), "Bad server address")
        return call("migrate-authorize", stdin = key + "\n" + fromAddress.trim())
    }
    /** On the old server: starts the export + transfer to the new one (the pinned host key, five lines on stdin); returns at once. */
    suspend fun migrateSend(host: String, port: Int, user: String, hostKeyType: String, hostKeyBlob: String): JsonObject {
        requireMigrate(MIGRATE_HOST_RE, host, "Bad server address")
        if (port !in 1..65535) throw ApiException(Codes.INVALID_NAME, "Bad port")
        requireMigrate(MIGRATE_SSH_USER_RE, user, "Bad user name")
        requireMigrate(MIGRATE_KEY_TYPE_RE, hostKeyType, "Bad host key type")
        requireMigrate(MIGRATE_KEY_BLOB_RE, hostKeyBlob, "Bad host key")
        return call("migrate-send", stdin = listOf(host, port.toString(), user, hostKeyType, hostKeyBlob).joinToString("\n"))
    }
    suspend fun migratePassphrase(): Passphrase = call("migrate-passphrase")
    suspend fun migrateStatus(): MigrateStatus = call("migrate-status")
    /** On the new server: restores the received [file]; the passphrase goes on stdin; returns at once. */
    suspend fun migrateRestore(file: String, passphrase: String): JsonObject {
        requireMigrate(MIGRATE_FILE_RE, file, "Bad backup file name")
        if (passphrase.isBlank() || passphrase.any { it == '\n' || it == '\r' }) throw ApiException(Codes.INVALID_NAME, "Bad passphrase")
        return call("migrate-restore $file", stdin = passphrase)
    }
    suspend fun migrateSudoCheck(password: String = ""): SudoCheck = call("migrate-sudo-check", stdin = password, timeoutMs = 60_000)
    /** [loginPublicKey] (optional) becomes a plain login key for ssh from a computer; the private half stays with the caller. */
    suspend fun migrateCreateUser(name: String, sudoPassword: String, phonePublicKey: String, loginPublicKey: String? = null): JsonObject {
        requireMigrate(MIGRATE_USER_RE, name, "User names are lowercase letters, digits, '_' and '-', up to 31, starting with a letter.")
        val key = phonePublicKey.trim()
        if (key.isEmpty() || key.any { it == '\n' || it == '\r' }) throw ApiException(Codes.INVALID_NAME, "Bad phone key")
        val login = loginPublicKey?.trim().orEmpty()
        if (login.any { it == '\n' || it == '\r' }) throw ApiException(Codes.INVALID_NAME, "Bad login key")
        val stdin = sudoPassword + "\n" + key + if (login.isNotEmpty()) "\n" + login else ""
        return call("migrate-create-user $name", stdin = stdin, timeoutMs = 1_200_000)
    }
    /** On the new server: clones the repos of the restored manifest that are still missing (GitHub must be signed in). */
    suspend fun migrateClone(manifestFile: String): JsonObject {
        requireMigrate(MIGRATE_MANIFEST_RE, manifestFile, "Bad manifest file name")
        return call("migrate-clone $manifestFile", timeoutMs = 600_000)
    }
    suspend fun migrateVerify(manifestFile: String? = null): VerifyResult {
        if (manifestFile != null) requireMigrate(MIGRATE_MANIFEST_RE, manifestFile, "Bad manifest file name")
        return call("migrate-verify" + if (manifestFile != null) " $manifestFile" else "", timeoutMs = 180_000)
    }
    suspend fun migrateSignoutOld(items: List<String>): JsonObject {
        if (items.isEmpty() || items.any { it !in MIGRATE_ITEMS }) throw ApiException(Codes.INVALID_NAME, "Bad sign-out choice")
        return call("migrate-signout-old " + items.distinct().joinToString(" "), timeoutMs = 120_000)
    }
    suspend fun migrateReboot(sudoPassword: String): JsonObject = call("migrate-reboot", stdin = sudoPassword)

    private fun requireMigrate(re: Regex, value: String, message: String) {
        if (!re.matches(value)) throw ApiException(Codes.INVALID_NAME, message)
    }

    private fun requireName(name: String) {
        if (!PROJECT_NAME_RE.matches(name)) {
            throw ApiException(
                Codes.INVALID_NAME,
                "Names may use letters, digits, '.', '_' and '-' (not first), up to 100.",
            )
        }
    }

    private suspend inline fun <reified T> call(
        command: String,
        stdin: String? = null,
        timeoutMs: Long = 90_000,
    ): T {
        val raw = run(command, stdin, timeoutMs)
        val data = parseEnvelope(raw)
        return try {
            json.decodeFromJsonElement(data)
        } catch (e: Exception) {
            throw ApiException(Codes.BAD_RESPONSE, "Unexpected answer from the server: ${e.message}")
        }
    }

    private suspend fun run(command: String, stdin: String?, timeoutMs: Long): String {
        val cfg = config()
        val id = withContext(Dispatchers.Default) {
            try {
                identity()
            } catch (e: Exception) {
                throw ApiException(
                    Codes.KEY_ERROR,
                    "This phone's SSH key couldn't be unlocked (${e.javaClass.simpleName}). Regenerate it in Settings.",
                )
            }
        }
        // Interruptible, so leaving a screen or cancelling stops the call.
        return runInterruptible(Dispatchers.IO) {
            try {
                SshRunner.exec(cfg, id, command, stdin, timeoutMs)
            } catch (f: SshFailure) {
                throw ApiException(
                    when (f.kind) {
                        SshFailure.Kind.HOST_KEY_CHANGED -> Codes.HOST_KEY_CHANGED
                        SshFailure.Kind.AUTH_FAILED -> Codes.AUTH_FAILED
                        SshFailure.Kind.TIMEOUT -> Codes.TIMEOUT
                        SshFailure.Kind.NOT_CONFIGURED -> Codes.NOT_CONFIGURED
                        SshFailure.Kind.NETWORK -> Codes.NETWORK
                    },
                    f.message ?: "Connection failed",
                )
            } catch (e: ApiException) {
                throw e
            } catch (e: InterruptedException) {
                throw e
            } catch (e: java.io.InterruptedIOException) {
                throw InterruptedException()   // a cancelled call, not a network failure
            } catch (e: Exception) {
                throw ApiException(Codes.NETWORK, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Returns `data` from `{"ok":true,...}` or throws the server's error. */
    internal fun parseEnvelope(raw: String): JsonElement {
        val line = raw.lineSequence().map { it.trim() }.lastOrNull { it.startsWith("{") }
            ?: throw ApiException(Codes.BAD_RESPONSE, "The server sent no JSON. Is claude-launcher-api installed?")
        val obj = try {
            json.parseToJsonElement(line).jsonObject
        } catch (e: Exception) {
            throw ApiException(Codes.BAD_RESPONSE, "The server sent invalid JSON.")
        }
        if (obj["ok"]?.jsonPrimitive?.booleanOrNull == true) {
            return obj["data"] ?: JsonObject(emptyMap())
        }
        val err = obj["error"]?.jsonObject
        val code = err?.get("code")?.jsonPrimitive?.contentOrNull ?: Codes.INTERNAL
        val message = err?.get("message")?.jsonPrimitive?.contentOrNull ?: "The server reported an error."
        val missing = runCatching {
            err?.get("missing")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
        }.getOrNull().orEmpty()
        val path = runCatching { err?.get("path")?.jsonPrimitive?.contentOrNull }.getOrNull()
        val pane = runCatching { err?.get("pane")?.jsonPrimitive?.contentOrNull }.getOrNull()
        throw ApiException(code, message, missing, path, pane)
    }
}
