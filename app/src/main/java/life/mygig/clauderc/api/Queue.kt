package life.mygig.clauderc.api

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Queue actions arrived with server script API 53; older servers get no queue UI. */
const val QUEUE_MIN_SCRIPT_API = 53

/** The copy-* actions (Copy between servers, docs/copy-design.md) arrived with server script API 58; both servers need it. */
const val COPY_MIN_SCRIPT_API = 58

/** chat-watch (long-poll for the open chat) arrived with server script API 57; older servers are polled with chat-history. */
const val CHAT_WATCH_MIN_SCRIPT_API = 57

/** Usage of the owner's account as the server last saw it (null = unknown). */
@Serializable
data class QueueUsage(
    val five: Double? = null,
    val seven: Double? = null,
    @SerialName("five_resets") val fiveResets: String? = null,
    @SerialName("seven_resets") val sevenResets: String? = null,
)

/** One starred chat: names and states only, never chat text. Times are epoch seconds (0 = none). */
@Serializable
data class QueueChat(
    val session: String,
    val state: String = "idle",
    val since: Long = 0,
    val note: String = "",
    val turns: Int = 0,
    @SerialName("next_try") val nextTry: Long = 0,
    @SerialName("running_min") val runningMin: Int = 0,
)

/** Answer of queue-status, queue-on and queue-off. */
@Serializable
data class QueueStatus(
    val enabled: Boolean = false,
    val parallel: Int = 1,
    @SerialName("max_5h") val max5h: Double? = null,
    @SerialName("max_7d") val max7d: Double? = null,
    val usage: QueueUsage = QueueUsage(),
    @SerialName("limited_until") val limitedUntil: Long = 0,
    val chats: List<QueueChat> = emptyList(),
)

@Serializable
data class QueueStarResult(val session: String, val starred: Boolean = false)

@Serializable
data class QueueLogLine(val ts: String = "", val session: String = "", val event: String = "", val detail: String = "")

@Serializable
data class QueueLog(val lines: List<QueueLogLine> = emptyList())

enum class QueueTone { WARN, OK, IDLE }

/** A starred chat's state as a short word and a colour class (amber needs you, green working, grey the rest). */
data class QueueChip(val label: String, val tone: QueueTone)

fun queueChip(state: String): QueueChip = when (state) {
    "running" -> QueueChip("working", QueueTone.OK)
    "question" -> QueueChip("question", QueueTone.WARN)
    "needs_you" -> QueueChip("needs you", QueueTone.WARN)
    "error" -> QueueChip("error", QueueTone.WARN)
    "unsafe" -> QueueChip("not allowed", QueueTone.WARN)
    "nothing" -> QueueChip("nothing to do", QueueTone.IDLE)
    "limited" -> QueueChip("limited", QueueTone.IDLE)
    "backoff" -> QueueChip("retrying", QueueTone.IDLE)
    "stopped" -> QueueChip("stopped", QueueTone.IDLE)
    else -> QueueChip("waiting", QueueTone.IDLE)   // "idle" and anything a newer server adds
}

/** "5h 62% · 7d 41%", or a plain note when the server has no numbers yet. */
fun queueUsageText(u: QueueUsage): String {
    val parts = listOfNotNull(u.five?.let { "5h ${it.roundToInt()}%" }, u.seven?.let { "7d ${it.roundToInt()}%" })
    return if (parts.isEmpty()) "usage unknown" else parts.joinToString(" · ")
}

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

/** The small line under a starred chat's title in its top bar. */
fun queueLine(enabled: Boolean, chat: QueueChat?, limitedUntil: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    if (chat == null) return "queue: starred"
    if (!enabled) return "queue: off"
    return when (chat.state) {
        "running" -> "queue: running"
        "question", "needs_you" -> "waiting for you"
        "nothing" -> "nothing to do"
        "limited" -> if (limitedUntil > 0) "paused: usage until " + CLOCK.format(Instant.ofEpochSecond(limitedUntil).atZone(zone)) else "paused: usage"
        "error" -> "queue: error"
        "stopped" -> "queue: chat stopped"
        "unsafe" -> "queue: not allowed in this mode"
        else -> "queue: waiting"
    }
}
