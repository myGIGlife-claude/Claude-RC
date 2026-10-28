package life.mygig.clauderc.widget

import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

/* Pure helpers behind the widgets' text and colors (no Android types, so JVM-tested). */

/** At most this many session rows; the list scrolls inside the widget. */
const val MAX_SESSION_ROWS = 12

/** Widgets show this many Claude accounts at most. */
const val MAX_USAGE_ACCOUNTS = 3

fun sessionRows(sessions: List<WidgetSession>): List<WidgetSession> = sessions.forWidget().take(MAX_SESSION_ROWS)

fun stateWord(dot: Dot) = when (dot) {
    Dot.WAITING -> "needs you"
    Dot.BUSY -> "working"
    Dot.IDLE -> "idle"
}

fun isStale(at: Long, now: Long) = at <= 0 || now - at > STALE_AFTER_MS

/** The small footer: how old the data is, and a hint when it is stale. */
fun footerText(at: Long, now: Long): String {
    val age = ageLabel(at, now)
    return when {
        at <= 0 -> "Not updated yet"
        isStale(at, now) -> "Updated $age, phone locked or offline?"
        else -> "Updated $age"
    }
}

/** Bar color: accent below 75 % used, warn 75-89 %, bad from 90 %. */
enum class BarLevel { OK, WARN, BAD }

fun barLevel(pct: Int): BarLevel = when {
    pct >= 90 -> BarLevel.BAD
    pct >= 75 -> BarLevel.WARN
    else -> BarLevel.OK
}

/** "resets in 2 h 10 min" from an ISO-8601 time; null when missing, unparsable or already past. */
fun resetsInText(iso: String?, now: Instant): String? {
    val at = parseInstant(iso ?: return null) ?: return null
    val min = Duration.between(now, at).toMinutes()
    return when {
        min < 0 -> null
        min < 1 -> "resets in under a minute"
        min < 60 -> "resets in $min min"
        min < 24 * 60 -> if (min % 60 == 0L) "resets in ${min / 60} h" else "resets in ${min / 60} h ${min % 60} min"
        else -> if (min / 60 % 24 == 0L) "resets in ${min / 1440} d" else "resets in ${min / 1440} d ${min / 60 % 24} h"
    }
}

private fun parseInstant(s: String): Instant? =
    runCatching { Instant.parse(s) }.getOrNull() ?: runCatching { OffsetDateTime.parse(s).toInstant() }.getOrNull()
