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

/** Same rule as the server's worker names (Team). */
val WORKER_NAME_RE = Regex("^[A-Za-z][A-Za-z0-9_-]{0,29}$")

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
    /** Saves an Android signing key: alias, passwords and the file (base64) go on stdin. */
    /** Google device sign-in for YouTube: client ID and secret on stdin; returns the code to enter. */
    suspend fun youtubeStart(clientId: String, clientSecret: String): YoutubeStart =
        call("youtube-login-start", stdin = clientId.trim() + "\n" + clientSecret.trim())
    suspend fun youtubePoll(): JsonObject = call("youtube-login-poll")
    suspend fun mcp(): McpData = call("mcp")
    suspend fun doctorStart(): StartResult = call("doctor-start")
    suspend fun mcpRefresh(): McpData = call("mcp-refresh", timeoutMs = 150_000)
    suspend fun mcpAuthStart(name: String): LoginUrl = call("mcp-auth-start", stdin = name, timeoutMs = 240_000)
    suspend fun mcpAuthFinish(callbackUrl: String): McpData = call("mcp-auth-finish", stdin = callbackUrl.trim(), timeoutMs = 180_000)
    suspend fun mcpAuthCancel(): JsonObject = call("mcp-auth-cancel")

    // Team: extra Claude accounts the main Claude can delegate to.
    suspend fun workers(): WorkersData = call("worker-list")
    suspend fun workerAdd(name: String, role: String): JsonObject {
        requireWorker(name)
        return call("worker-add $name", stdin = role.trim().replace('\n', ' '))
    }
    suspend fun workerSet(name: String, field: String, value: String): JsonObject {
        requireWorker(name)
        if (field != "role" && field != "mode") throw ApiException(Codes.INVALID_NAME, "Bad field")
        return call("worker-set $name $field", stdin = value.trim().replace('\n', ' '))
    }
    suspend fun workerRemove(name: String): JsonObject { requireWorker(name); return call("worker-remove $name") }
    suspend fun workerLoginStart(name: String): LoginUrl { requireWorker(name); return call("worker-login-start $name", timeoutMs = 60_000) }
    suspend fun workerLoginCode(name: String, code: String): LoginDone {
        requireWorker(name)
        return call("worker-login-code $name", stdin = code.trim(), timeoutMs = 120_000)
    }
    suspend fun workerRuns(name: String): WorkerRunsData { requireWorker(name); return call("worker-runs $name") }
    private fun requireWorker(name: String) {
        if (!WORKER_NAME_RE.matches(name)) throw ApiException(Codes.INVALID_NAME, "Bad worker name")
    }
    suspend fun plugins(): PluginsData = call("plugins", timeoutMs = 120_000)
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
    suspend fun chatInterrupt(session: String, pin: String): JsonObject { requireName(session); return call("chat-interrupt $session", stdin = pin) }
    suspend fun loginKeystore(name: String, alias: String, storePassword: String, keyPassword: String, fileBase64: String): JsonObject =
        call("login-keystore $name", stdin = listOf(alias.trim(), storePassword, keyPassword, fileBase64).joinToString("\n"))
    suspend fun removeKeystore(name: String): JsonObject = call("remove-keystore $name")
    suspend fun removeSecret(name: String): JsonObject = call("remove-secret $name")
    suspend fun installCli(name: String, sudoPassword: String = ""): JsonObject =
        call("install-cli $name", stdin = sudoPassword, timeoutMs = 660_000)
    /** Saves a token service's credential; one value per field, or the whole JSON key. */
    suspend fun loginToken(id: String, values: List<String>): LoginDone =
        call("login-token $id", stdin = values.joinToString("\n") { it.trim() })

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
