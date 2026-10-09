package life.mygig.clauderc.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

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
    /** An App Store Connect API key is saved (iOS signing and uploads). */
    val apple: Boolean = false,
    /** The developer knowledge pack installed for every session (null = none yet). */
    val knowledge: KnowledgePack? = null,
)

@Serializable
data class KnowledgePack(val version: String = "", val skills: Int = 0)

/** The monthly refresh of the knowledge pack: whether the tool is installed, the schedule, a run in progress and the last result. */
@Serializable
data class KnowledgeRefresh(val installed: Boolean = false, val schedule: String = "off", val running: Boolean = false, val last: KnowledgeRun? = null)

@Serializable
data class KnowledgeRun(val state: String = "", val message: String = "", val pr: String? = null, val started: Long? = null, val finished: Long? = null)

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
    /** "Finished" push alerts are on for this session. */
    @SerialName("push_done") val pushDone: Boolean = false,
)

/** The (public) Firebase ids the server hands the phone; [configured] is false until push is set up there. */
@Serializable
data class PushConfig(
    val configured: Boolean = false,
    @SerialName("project_id") val projectId: String = "",
    @SerialName("app_id") val appId: String = "",
    @SerialName("api_key") val apiKey: String = "",
    @SerialName("sender_id") val senderId: String = "",
)

@Serializable
data class PushTestResult(val sent: Int = 0)

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
data class LoginUrl(val url: String, val code: String? = null, @SerialName("device_code") val deviceCode: String? = null, @SerialName("paste_url") val pasteUrl: Boolean = false)

@Serializable
data class YoutubeStart(val url: String, val code: String, val interval: Int = 5, @SerialName("expires_in") val expiresIn: Int = 1800)

@Serializable data class DriveLoginStart(val url: String, val code: String, val interval: Int = 5, @SerialName("expires_in") val expiresIn: Int = 1800)
@Serializable data class DriveBackupStatus(val state: String = "idle", val phase: String = "", val percent: Int? = null, val message: String = "", val started: Long? = null, val finished: Long? = null, val name: String? = null, val size: Long? = null)
@Serializable data class DriveBackupEntry(val id: String = "", val name: String = "", val size: Long = 0, val time: String = "")
@Serializable data class DriveStatus(val configured: Boolean = false, val connected: Boolean = false, val schedule: String = "off", @SerialName("last_backup") val lastBackup: DriveBackupEntry? = null, @SerialName("has_passphrase") val hasPassphrase: Boolean = false)

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
    /** "current" | "available" | "unknown" | "error"; empty = not checked (old servers). */
    val update: String = "",
    /** Short version or sha of the newest release, when known. */
    val latest: String = "",
    /** "skills" when the `skills` CLI manages it instead of `claude plugin`; empty otherwise (and on old servers). */
    val via: String = "",
    /** owner/repo to give `npx skills add`, when via == "skills". */
    val source: String = "",
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
data class AskOption(val label: String = "", val description: String = "")

@Serializable
data class AskQuestion(
    val question: String = "",
    val header: String = "",
    val multiSelect: Boolean = false,
    val options: List<AskOption> = emptyList(),
)

@Serializable
data class ChatFile(val name: String, val bytes: Long = 0, val data: String = "")

@Serializable
data class ChatCommand(val name: String, val hint: String = "")

@Serializable
data class ChatCommands(val commands: List<ChatCommand> = emptyList())

@Serializable
data class ChatData(
    val session: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val waiting: Boolean = false,
    val busy: Boolean = false,
    /** Claude's permission mode: auto, plan, edits, bypass or default. */
    val mode: String = "default",
    /** The model of Claude's latest reply, e.g. claude-opus-5-5 (empty before its first reply). */
    val model: String = "",
    /** Claude's multiple-choice question waiting for an answer (AskUserQuestion), if that's what's on screen. */
    val ask: List<AskQuestion>? = null,
    /** The question on screen when [waiting]. */
    val screen: String? = null,
)

@Serializable
data class PinStatus(val set: Boolean = false, @SerialName("locked_until") val lockedUntil: Long? = null)

@Serializable
data class ChatLogEntry(val ts: String = "", val session: String = "", val action: String = "")

@Serializable
data class UploadResult(val path: String = "", val bytes: Long = 0)

@Serializable
data class Worker(
    val name: String,
    val kind: String = "claude",
    val role: String = "",
    val mode: String = "acceptEdits",
    @SerialName("signed_in") val signedIn: Boolean = false,
)

@Serializable
data class UsageWindow(val pct: Double? = null, @SerialName("resets_at") val resetsAt: String? = null)

@Serializable
data class Usage(@SerialName("five_hour") val fiveHour: UsageWindow = UsageWindow(), @SerialName("seven_day") val sevenDay: UsageWindow = UsageWindow())

/** Tokens one account used on this server in a window: fresh input (with cache writes), output, cache reads. */
@Serializable
data class TokenWindow(@SerialName("in") val input: Long = 0, val out: Long = 0, val cached: Long = 0)

@Serializable
data class Tokens(@SerialName("five_hour") val fiveHour: TokenWindow = TokenWindow(), @SerialName("seven_day") val sevenDay: TokenWindow = TokenWindow())

/** One Claude account on a server: its main login ("main") or a worker. */
@Serializable
data class ClusterAccount(
    val name: String,
    /** claude, codex (ChatGPT) or gemini. */
    val kind: String = "claude",
    val email: String? = null,
    val plan: String? = null,
    @SerialName("signed_in") val signedIn: Boolean = false,
    val role: String = "",
    val mode: String = "acceptEdits",
    val usage: Usage? = null,
    /** "expired" (Claude renews it next time it runs) or "unavailable". */
    @SerialName("usage_error") val usageError: String? = null,
    val tokens: Tokens? = null,
)

@Serializable
data class ClusterData(val accounts: List<ClusterAccount> = emptyList())

/** Server-wide cluster settings: tasks one chat may run at once, and handing work back to the main Claude near the 5-hour limit. */
@Serializable
data class ClusterConfig(
    @SerialName("max_parallel") val maxParallel: Int = 3,
    val handback: Boolean = true,
    @SerialName("handback_pct") val handbackPct: Int = 95,
    /** Worker sandbox (bubblewrap): "auto" = use it when it works here, "on" = refuse to run workers without it, "off". */
    val sandbox: String = "auto",
)

/** A worker as one chat sees it: attached or not, with this chat's role and mode. */
@Serializable
data class ChatWorker(
    val name: String,
    @SerialName("signed_in") val signedIn: Boolean = false,
    val attached: Boolean = false,
    val role: String = "",
    val mode: String = "acceptEdits",
)

/** Work handed to a worker in this chat that is still running or waiting for the main Claude to merge or discard. */
@Serializable
data class ChatTask(val id: String, val worker: String = "", val task: String = "", val status: String = "", val branch: String? = null)

@Serializable
data class ChatWorkersData(val workers: List<ChatWorker> = emptyList(), val tasks: List<ChatTask> = emptyList())

@Serializable
data class WorkersData(val workers: List<Worker> = emptyList())

/** A server chats can work on (a web server): Claude runs commands there, nothing is installed. No key material. */
@Serializable
data class Host(
    val name: String,
    val address: String = "",
    val port: Int = 22,
    val user: String = "",
    /** "key", "password" (the server's own key was installed with a password) or "generated" (the server's key, installed by the owner). */
    val auth: String = "key",
    val fingerprint: String = "",
    /** What "Set up this server" last ran on it (steps asked for, and whether every step went through); null = never. */
    val setup: HostSetupInfo? = null,
)

@Serializable
data class HostSetupInfo(val at: Long = 0, val steps: List<String> = emptyList(), val ok: Boolean = false)

@Serializable
data class HostsData(val hosts: List<Host> = emptyList())

/** A host's key before it is added, to confirm with the owner. */
@Serializable
data class HostProbe(val fingerprint: String = "", val keytype: String = "")

/** A key the server made for a host not added yet: only the public half, for the owner to put on the host. */
@Serializable
data class HostKey(@SerialName("public_key") val publicKey: String = "", val fingerprint: String = "")

/** One step of host-harden: status "ok", "skipped" or "failed". */
@Serializable
data class HostHardenStep(val name: String = "", val status: String = "", val detail: String = "")

/** What host-harden did; [docker] means published container ports bypass the firewall. */
@Serializable
data class HostHardenResult(
    @SerialName("ssh_port") val sshPort: Int = 22,
    val docker: Boolean = false,
    val steps: List<HostHardenStep> = emptyList(),
)

/** A host as one chat sees it: attached or not. */
@Serializable
data class ChatHost(val name: String, val address: String = "", val user: String = "", val attached: Boolean = false)

@Serializable
data class ChatHostsData(val hosts: List<ChatHost> = emptyList())

@Serializable
data class WorkerRun(
    val id: String,
    val task: String = "",
    val status: String = "",
    val reply: String? = null,
    val error: String? = null,
    val started: Double = 0.0,
)

@Serializable
data class WorkerRunsData(val runs: List<WorkerRun> = emptyList())

// In-app Migrate (docs/migrate-design.md): the server's migrate-* answers.

@Serializable
data class MigrateSession(val name: String = "", val busy: Boolean = false, val waiting: Boolean = false)

@Serializable
data class MigrateRepo(
    val dir: String = "",
    val state: String = "",
    val rescued: Boolean = false,
    @SerialName("skipped_reason") val skippedReason: String = "",
)

@Serializable
data class MigrateLogins(val claude: Boolean = false, val workers: List<String> = emptyList())

@Serializable
data class MigratePlan(
    val sessions: List<MigrateSession> = emptyList(),
    val repos: List<MigrateRepo> = emptyList(),
    val logins: MigrateLogins = MigrateLogins(),
    val services: List<String> = emptyList(),
    @SerialName("docker_volumes") val dockerVolumes: List<String> = emptyList(),
    @SerialName("estimate_mb") val estimateMb: Int = 0,
    @SerialName("other_dirs") val otherDirs: List<String> = emptyList(),
)

@Serializable
data class MigrateStatus(
    /** send, restore or none. */
    val job: String = "none",
    /** export, transfer, restore, done or failed. */
    val phase: String = "",
    val message: String = "",
    val pct: Int = 0,
    val bytes: Long = 0,
    /** The incoming file's name (on the receiving server). */
    val file: String = "",
    /** Timestamps as the server wrote them (number or text); the app doesn't use them. */
    val started: JsonElement = JsonNull,
    val updated: JsonElement = JsonNull,
    val error: String? = null,
)

@Serializable
data class VerifyItem(val id: String = "", val ok: Boolean = false, val detail: String = "")

@Serializable
data class VerifyResult(val items: List<VerifyItem> = emptyList(), val ok: Boolean = false)

@Serializable
data class PublicKey(@SerialName("public_key") val publicKey: String = "")

@Serializable
data class Passphrase(val passphrase: String = "")

/** root, nopasswd, password_ok, password_needed or none. */
@Serializable
data class SudoCheck(val mode: String = "none")
