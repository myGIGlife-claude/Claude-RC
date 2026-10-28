package life.mygig.clauderc.widget

import kotlin.math.ceil
import kotlin.math.floor

/*
 * How each widget lays out at a given size (dp from LocalSize, SizeMode.Exact). Pure, so it is JVM-tested.
 * Foldables resize widgets on fold/unfold: the narrow cover screen gives COMPACT, the inner screen EXPANDED.
 * Everything is capped to what fits, so nothing is clipped; tap rows are 48dp.
 */

enum class WidthClass { COMPACT, MEDIUM, EXPANDED }

/** [short]: under 110dp tall, only the header and the single most important item. */
data class SizeClass(val width: WidthClass, val short: Boolean)

fun sizeClass(widthDp: Float, heightDp: Float) = SizeClass(
    width = when {
        widthDp < 180f -> WidthClass.COMPACT
        widthDp < 320f -> WidthClass.MEDIUM
        else -> WidthClass.EXPANDED
    },
    short = heightDp < 110f,
)

// Heights (dp) of the pieces, matching the composables in the widget files.
const val PAD = 12f
const val PAD_SHORT = 8f
const val HEADER_H = 32f
const val FOOTER_H = 40f
const val ROW_H = 48f
const val COLUMN_GAP = 8f
private const val STATE_WORD_MIN_W = 170f
/** Glance draws at most 10 children in one Row or Column. */
const val MAX_CHILDREN = 10

private fun fit(space: Float, each: Float) = floor(space / each).toInt().coerceAtLeast(0)
private fun ceilDiv(a: Int, b: Int) = ceil(a / b.toDouble()).toInt()

data class SessionsLayout(
    val padding: Float, val columns: Int, val rowsPerColumn: Int, val showWord: Boolean, val footer: Boolean, val cap: Int,
) {
    val maxItems get() = minOf(columns * rowsPerColumn, cap)
}

/** One column (two on a wide widget) of 48dp rows, as many as fit, at most [maxRows] in total. */
fun sessionsLayout(widthDp: Float, heightDp: Float, maxRows: Int): SessionsLayout {
    val sc = sizeClass(widthDp, heightDp)
    val cap = maxRows.coerceAtLeast(1)
    if (sc.short) {
        val rows = fit(heightDp - 2 * PAD_SHORT - HEADER_H, ROW_H).coerceAtMost(1)
        return SessionsLayout(PAD_SHORT, 1, rows, showWord = sc.width != WidthClass.COMPACT, footer = false, cap = cap)
    }
    val columns = if (sc.width == WidthClass.EXPANDED) 2 else 1
    val columnW = (widthDp - 2 * PAD - (columns - 1) * COLUMN_GAP) / columns
    // The footer goes first when it would leave no room for a single row.
    val withFooter = fit(heightDp - 2 * PAD - HEADER_H - FOOTER_H, ROW_H)
    val footer = withFooter >= 1
    val fits = if (footer) withFooter else fit(heightDp - 2 * PAD - HEADER_H, ROW_H)
    val perColumn = minOf(fits, ceilDiv(cap, columns), MAX_CHILDREN)
    return SessionsLayout(PAD, columns, perColumn, showWord = columnW >= STATE_WORD_MIN_W, footer = footer, cap = cap)
}

data class ActionsLayout(val padding: Float, val rows: Int, val columns: Int, val labels: Boolean) {
    val maxItems get() = rows * columns
}

private const val ACTION_W = 48f
private const val ACTION_H = 48f
private const val LABELLED_W = 64f
private const val LABELLED_H = 64f

/**
 * Buttons for [count] actions: icon-only on COMPACT, labelled otherwise when there is room. One row when short,
 * two rows (2x2, 2x3) once two rows fit and there are four or more; labels are dropped if they would hide actions.
 */
fun actionsLayout(widthDp: Float, heightDp: Float, count: Int): ActionsLayout {
    val sc = sizeClass(widthDp, heightDp)
    val w = widthDp - 2 * PAD_SHORT
    val h = heightDp - 2 * PAD_SHORT
    val n = count.coerceIn(1, MAX_ACTIONS)
    fun grid(labels: Boolean): ActionsLayout {
        val maxCols = fit(w, if (labels) LABELLED_W else ACTION_W).coerceAtLeast(1)
        val maxRows = fit(h, if (labels) LABELLED_H else ACTION_H).coerceAtLeast(1)
        val rows = maxOf(if (maxRows >= 2 && n >= 4) 2 else 1, ceilDiv(n, maxCols)).coerceAtMost(maxRows)
        return ActionsLayout(PAD_SHORT, rows, minOf(maxCols, ceilDiv(n, rows)), labels)
    }
    val plain = grid(labels = false)
    if (sc.width == WidthClass.COMPACT || h < LABELLED_H) return plain
    val labelled = grid(labels = true)
    return if (labelled.maxItems >= minOf(n, plain.maxItems)) labelled else plain
}

data class UsageLayout(
    val padding: Float,
    val columns: Int,
    /** How many accounts to show; 1 = the most used one. */
    val accounts: Int,
    val weekly: Boolean,
    val resets: Boolean,
    val footer: Boolean,
    /** Short: the account name goes in the header, with at most one bar under it. */
    val nameInHeader: Boolean = false,
    val bars: Int = if (weekly) 2 else 1,
)

const val USAGE_NAME_H = 20f
const val USAGE_LINE_H = 20f
const val USAGE_RESET_H = 16f
const val USAGE_GAP = 8f
private const val USAGE_COLUMN_MIN_W = 140f
private const val MAX_USAGE_ACCOUNTS = 3

fun usageBlockH(bars: Int, resets: Boolean) = USAGE_NAME_H + bars * (USAGE_LINE_H + if (resets) USAGE_RESET_H else 0f) + USAGE_GAP

/**
 * Short: header with the top account and its 5 h bar. COMPACT: one account, 5 h bar. Wider: up to three accounts
 * (side by side on EXPANDED), both bars when [showWeekly]; reset hints, then the 7 day bar, go first when short of room.
 */
fun usageLayout(widthDp: Float, heightDp: Float, count: Int, showWeekly: Boolean): UsageLayout {
    val sc = sizeClass(widthDp, heightDp)
    if (sc.short) {
        val bar = heightDp - 2 * PAD_SHORT - HEADER_H >= USAGE_LINE_H
        return UsageLayout(PAD_SHORT, 1, 1, weekly = false, resets = false, footer = false, nameInHeader = true, bars = if (bar) 1 else 0)
    }
    val columns = when (sc.width) {
        WidthClass.EXPANDED -> fit(widthDp - 2 * PAD + COLUMN_GAP, USAGE_COLUMN_MIN_W + COLUMN_GAP).coerceIn(1, MAX_USAGE_ACCOUNTS)
        else -> 1
    }
    val most = if (sc.width == WidthClass.COMPACT) 1 else minOf(count.coerceAtLeast(1), MAX_USAGE_ACCOUNTS)
    val tries = buildList {
        if (sc.width != WidthClass.COMPACT && showWeekly) { add(true to true); add(true to false) }
        add(false to true); add(false to false)
    }
    // The footer goes last: only when not even one account with one bar fits beside it.
    for (footer in listOf(true, false)) {
        val avail = heightDp - 2 * PAD - HEADER_H - if (footer) FOOTER_H else 0f
        for ((weekly, resets) in tries) {
            val perColumn = fit(avail, usageBlockH(if (weekly) 2 else 1, resets))
            if (perColumn >= 1) {
                val shown = minOf(most, columns * perColumn)
                return UsageLayout(PAD, minOf(columns, shown), shown, weekly, resets, footer)
            }
        }
    }
    return UsageLayout(PAD, 1, 1, weekly = false, resets = false, footer = false)
}
