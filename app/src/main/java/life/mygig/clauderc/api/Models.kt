package life.mygig.clauderc.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StatusData(
    val claude: ClaudeStatus = ClaudeStatus(),
    val github: GithubStatus = GithubStatus(),
    val aws: AwsStatus = AwsStatus(),
    val hostname: String = "",
    /** Missing when the server's scripts predate these services. */
    val services: Map<String, ServiceStatus>? = null,
    /** Last commit that changed server/, as recorded by install.sh; null if unknown. */
    val commit: String? = null,
    @SerialName("script_api") val scriptApi: Int = 0,
    @SerialName("run_enabled") val runEnabled: Boolean = false,
    /** Names of custom API keys (never their values). */
    val custom: List<String> = emptyList(),
    /** Names of Android signing keys saved on the server. */
    val keystores: List<String> = emptyList(),
)

@Serializable
data class ServiceStatus(
    val installed: Boolean = false,
    @SerialName("logged_in") val loggedIn: Boolean = false,
    val detail: String? = null,
)

@Serializable
data class ClaudeStatus(@SerialName("logged_in") val loggedIn: Boolean = false)

@Serializable
data class GithubStatus(
    @SerialName("logged_in") val loggedIn: Boolean = false,
    val user: String? = null,
    @SerialName("missing_scopes") val missingScopes: List<String> = emptyList(),
)

@Serializable
data class AwsStatus(
    @SerialName("logged_in") val loggedIn: Boolean = false,
    val identity: AwsIdentity? = null,
    val profile: String = "default",
    @SerialName("sso_configured") val ssoConfigured: Boolean = false,
)

@Serializable
data class AwsIdentity(val account: String? = null, val arn: String? = null)

@Serializable
data class Org(val login: String)

@Serializable
data class OwnersData(
    val user: String,
    val orgs: List<Org> = emptyList(),
    @SerialName("default_owner") val defaultOwner: String? = null,
)

@Serializable
data class Repo(
    @SerialName("full_name") val fullName: String,
    val name: String,
    val owner: String,
    @SerialName("owner_type") val ownerType: String = "User",
    val private: Boolean = false,
    @SerialName("pushed_at") val pushedAt: String? = null,
    val local: Boolean = false,
    val running: Boolean = false,
    /** A clone started from the phone is still running in the background. */
    val cloning: Boolean = false,
    /** Archived on GitHub (read-only there). */
    val archived: Boolean = false,
) {
    val isOrg: Boolean get() = ownerType == "Organization"
}

@Serializable
data class ReposData(val user: String = "", val repos: List<Repo> = emptyList())

@Serializable
data class Session(
    val name: String,
    val project: String,
    val dir: String = "",
    @SerialName("started_at") val startedAt: Long = 0,
    val attached: Boolean = false,
    @SerialName("uptime_seconds") val uptimeSeconds: Long = 0,
    /** The session's last lines (Claude's input box and status bar left out). */
    val preview: String = "",
    /** A question on screen (e.g. approve a new MCP server). */
    val waiting: Boolean = false,
    /** Claude is working. */
    val busy: Boolean = false,
)

@Serializable
data class SessionsData(val now: Long = 0, val sessions: List<Session> = emptyList())

@Serializable
data class NewResult(
    val repo: String,
    val url: String,
    val path: String,
    val visibility: String = "private",
    val session: String? = null,
)

@Serializable
data class OpenResult(
    val repo: String,
    val path: String,
    val action: String,
    val pending: Boolean = false,
    val note: String? = null,
    val session: String? = null,
)

@Serializable
data class StartResult(
    val session: String,
    val path: String = "",
    @SerialName("already_running") val alreadyRunning: Boolean = false,
)

@Serializable
data class StopResult(val session: String, val stopped: Boolean)

@Serializable
data class TailResult(val session: String, val lines: Int = 0, val text: String = "")

@Serializable
data class LoginUrl(val url: String, val code: String? = null)

@Serializable
data class YoutubeStart(val url: String, val code: String, val interval: Int = 5, @SerialName("expires_in") val expiresIn: Int = 1800)

@Serializable
data class LoginDone(
    @SerialName("logged_in") val loggedIn: Boolean = false,
    val user: String? = null,
    val account: String? = null,
    val arn: String? = null,
)

@Serializable
data class CloneStatus(
    /** running, done, failed, or none (no clone known). */
    val state: String,
    val message: String? = null,
    val session: String? = null,
)

@Serializable
data class RunResult(
    @SerialName("exit_code") val exitCode: Int = 0,
    val output: String = "",
)

@Serializable
data class McpServer(
    val name: String,
    val label: String = name,
    /** user | project | plugin | claude.ai */
    val scope: String = "user",
    val plugin: String? = null,
    val kind: String = "stdio",
    val target: String = "",
    /** connected | failed | needs_auth | unknown */
    val health: String = "unknown",
    val detail: String = "",
)

@Serializable
data class McpData(
    val servers: List<McpServer> = emptyList(),
    @SerialName("checked_seconds_ago") val checkedSecondsAgo: Long? = null,
    val refreshing: Boolean = false,
)

@Serializable
data class InstalledPlugin(
    val id: String,
    val name: String,
    val marketplace: String = "",
    val version: String = "",
    val enabled: Boolean = false,
    val scope: String = "user",
)

@Serializable
data class AvailablePlugin(
    val id: String,
    val name: String,
    val marketplace: String = "",
    val description: String = "",
    val installs: Long = 0,
    val installed: Boolean = false,
)

@Serializable
data class Marketplace(val name: String, val source: String = "")

@Serializable
data class PluginsData(
    @SerialName("claude_version") val claudeVersion: String? = null,
    val installed: List<InstalledPlugin> = emptyList(),
    val available: List<AvailablePlugin> = emptyList(),
    val marketplaces: List<Marketplace> = emptyList(),
)

@Serializable
data class ChatMessage(
    val id: String = "", val role: String = "assistant", val text: String = "", val ts: String? = null,
    /** role "file": paths of files Claude sent. */
    val files: List<String> = emptyList(),
)

@Serializable
data class ChatFile(val name: String, val bytes: Long = 0, val data: String = "")

@Serializable
data class ChatData(
    val session: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val waiting: Boolean = false,
    val busy: Boolean = false,
    /** Claude's permission mode: auto, plan, edits, bypass or default. */
    val mode: String = "default",
    /** The question on screen when [waiting]. */
    val screen: String? = null,
)

@Serializable
data class PinStatus(val set: Boolean = false, @SerialName("locked_until") val lockedUntil: Long? = null)

@Serializable
data class ChatLogEntry(val ts: String = "", val session: String = "", val action: String = "")

@Serializable
data class UploadResult(val path: String = "", val bytes: Long = 0)
