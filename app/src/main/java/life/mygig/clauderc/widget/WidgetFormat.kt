package life.mygig.clauderc.widget

import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

/* Pure helpers behind the widgets' text and colors (no Android types, so JVM-tested). */

/** The sessions this widget shows (by its filter), waiting first; the layout decides how many fit. */
fun WidgetConfig.pickSessions(all: List<WidgetSession>): List<WidgetSession> = when (filter) {
    SessionFilter.ACTIVE -> all.filter { it.waiting || it.busy }
    SessionFilter.PICKED -> all.filter { it.name in sessions }
    else -> all
}.forWidget()

/** What the empty list says, by filter. */
fun emptySessionsText(filter: String) = when (filter) {
    SessionFilter.ACTIVE -> "Nothing needs you or is working"
    SessionFilter.PICKED -> "None of the picked sessions is running"
    else -> "No sessions running"
}

/** Row cap from the settings, clamped to 1..12 (a hand-edited or old value can't break the layout). */
fun WidgetConfig.rowCap(): Int = maxRows.coerceIn(1, MAX_ROW_CHOICES.last())

/** The ticked accounts in snapshot order; none ticked (or none of them left) = all. */
fun WidgetConfig.pickAccounts(all: List<WidgetUsage>): List<WidgetUsage> =
    if (accounts.isEmpty()) all else all.filter { it.account in accounts }.ifEmpty { all }

/** The account closest to a limit first (what a one-account widget shows). */
fun List<WidgetUsage>.mostUsedFirst(): List<WidgetUsage> =
    sortedByDescending { maxOf(it.fiveHourPct ?: -1.0, it.sevenDayPct ?: -1.0) }

/** Known action ids in the chosen order, no repeats, at most MAX_ACTIONS; nothing usable = DEFAULT_ACTIONS. */
fun sanitizeActions(ids: List<String>): List<QuickAction> {
    val known = ids.distinct().mapNotNull { id -> QUICK_ACTIONS.firstOrNull { it.id == id } }.take(MAX_ACTIONS)
    return known.ifEmpty { DEFAULT_ACTIONS.mapNotNull { id -> QUICK_ACTIONS.firstOrNull { it.id == id } } }
}

fun stateWord(dot: Dot) = when (dot) {
    Dot.WAITING -> "needs you"
    Dot.BUSY -> "working"
    Dot.IDLE -> "idle"
}

/** Settings screen: tick or untick [id]; ticking adds it at the end, and nothing beyond [max]. */
fun List<String>.toggled(id: String, max: Int = Int.MAX_VALUE): List<String> = when {
    id in this -> this - id
    size >= max -> this
    else -> this + id
}

/** Settings screen: move [id] up (-1) or down (+1); out of range or unknown = unchanged. */
fun List<String>.moved(id: String, by: Int): List<String> {
    val from = indexOf(id)
    val to = from + by
    if (from < 0 || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
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
