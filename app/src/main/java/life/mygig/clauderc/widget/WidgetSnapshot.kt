package life.mygig.clauderc.widget

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import life.mygig.clauderc.api.ClusterAccount
import life.mygig.clauderc.api.PROJECT_NAME_RE
import life.mygig.clauderc.api.Session

/*
 * What the home-screen widgets show. One small JSON snapshot in DataStore (`widgetStore`), written by the app
 * (whenever it has fresh sessions/usage) and by WidgetRefreshWorker (in the background), read by the Glance
 * widgets. Widgets never open an SSH connection themselves, and never hold chat text: only names, status
 * and usage numbers.
 */

@Serializable
data class WidgetSession(
    /** The tmux session name; what the app opens. */
    val name: String,
    val project: String,
    val waiting: Boolean = false,
    val busy: Boolean = false,
    val general: Boolean = false,
)

@Serializable
data class WidgetUsage(
    val account: String,
    val fiveHourPct: Double? = null,
    val fiveHourResetsAt: String? = null,
    val sevenDayPct: Double? = null,
    val sevenDayResetsAt: String? = null,
)

@Serializable
data class WidgetSnapshot(
    val sessions: List<WidgetSession> = emptyList(),
    val usage: List<WidgetUsage> = emptyList(),
    /** Epoch millis of the last successful sessions / usage fetch; 0 = never. */
    val sessionsAt: Long = 0,
    val usageAt: Long = 0,
    /** A server is set up in the app (otherwise widgets say "Open cLaudeRC to connect"). */
    val configured: Boolean = false,
)

/** Amber needs you, green working, grey idle (same as the app's status dots). */
enum class Dot { WAITING, BUSY, IDLE }

fun WidgetSession.dot() = when {
    waiting -> Dot.WAITING
    busy -> Dot.BUSY
    else -> Dot.IDLE
}

/** Waiting sessions first, then working, then idle; ties by project name. */
fun List<WidgetSession>.forWidget(): List<WidgetSession> =
    sortedWith(compareBy<WidgetSession> { it.dot().ordinal }.thenBy { it.project.lowercase() })

fun Session.toWidget() = WidgetSession(name = name, project = project, waiting = waiting, busy = busy, general = general)

/** Only Claude accounts have usage numbers; accounts without them are left out. */
fun ClusterAccount.toWidgetUsage(): WidgetUsage? {
    val u = usage ?: return null
    if (kind != "claude" || (u.fiveHour.pct == null && u.sevenDay.pct == null)) return null
    return WidgetUsage(name, u.fiveHour.pct, u.fiveHour.resetsAt, u.sevenDay.pct, u.sevenDay.resetsAt)
}

/** "3 min ago" / "2 h ago" / "never": how old the data is, for the widgets' small footer. */
fun ageLabel(at: Long, now: Long): String {
    if (at <= 0) return "never"
    val min = ((now - at) / 60_000).coerceAtLeast(0)
    return when {
        min < 1 -> "just now"
        min < 60 -> "$min min ago"
        min < 24 * 60 -> "${min / 60} h ago"
        else -> "${min / (24 * 60)} d ago"
    }
}

/** Data older than this is shown greyed with its age (the worker runs every 15 minutes; a locked phone can't fetch). */
const val STALE_AFTER_MS = 45L * 60_000

/** Used percentage clamped to 0..100 for a bar; null stays null. */
fun barPct(pct: Double?): Int? = pct?.coerceIn(0.0, 100.0)?.let { Math.round(it).toInt() }

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
fun WidgetSnapshot.encode(): String = json.encodeToString(this)
fun decodeSnapshot(text: String?): WidgetSnapshot =
    text?.let { runCatching { json.decodeFromString<WidgetSnapshot>(it) }.getOrNull() } ?: WidgetSnapshot()

/** What a widget tap tells MainActivity (Intent extras). Values are checked in the app before use. */
object WidgetLink {
    const val EXTRA_OPEN = "widget_open"
    const val EXTRA_SESSION = "widget_session"
    const val OPEN_SESSIONS = "sessions"   // Sessions tab
    const val OPEN_SESSION = "session"     // that session's chat (EXTRA_SESSION)
    const val OPEN_NEW_REPO = "new_repo"   // New tab
    const val OPEN_KEYS = "keys"           // Connections tab, Keys section
    const val OPEN_NEW_CHAT = "new_chat"   // Sessions tab, General chat, start a new chat
    const val OPEN_CLUSTER = "cluster"     // Claude tab, cLaudeCluster (usage)
    fun validSession(name: String?) = name != null && PROJECT_NAME_RE.matches(name)
}
