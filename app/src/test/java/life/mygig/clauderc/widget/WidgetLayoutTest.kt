package life.mygig.clauderc.widget

import org.junit.Test
import org.junit.Assert.assertEquals

class WidgetLayoutTest {
    @Test fun sizeClasses() {
        assertEquals(SizeClass(WidthClass.COMPACT, false), sizeClass(179f, 200f))
        assertEquals(SizeClass(WidthClass.MEDIUM, false), sizeClass(180f, 110f))
        assertEquals(SizeClass(WidthClass.MEDIUM, true), sizeClass(319f, 109f))
        assertEquals(SizeClass(WidthClass.EXPANDED, false), sizeClass(320f, 400f))
    }

    @Test fun sessionsOnCoverScreen() {
        // Narrow: one column, no state word (the dot's description says it), as many 48dp rows as fit.
        assertEquals(SessionsLayout(12f, 1, 2, showWord = false, footer = true, cap = 12), sessionsLayout(150f, 200f, 12))
    }

    @Test fun sessionsOnInnerScreen() {
        val l = sessionsLayout(400f, 400f, 12)
        assertEquals(SessionsLayout(12f, 2, 6, showWord = true, footer = true, cap = 12), l)
        assertEquals(12, l.maxItems)
        // The row setting caps the total, not each column.
        assertEquals(3, sessionsLayout(400f, 400f, 3).maxItems)
    }

    @Test fun sessionsDropFooterBeforeRows() {
        assertEquals(SessionsLayout(12f, 1, 1, showWord = true, footer = false, cap = 12), sessionsLayout(250f, 130f, 12))
    }

    @Test fun sessionsShort() {
        // Header plus the single most important (first, waiting-first) row; header only when even that won't fit.
        assertEquals(SessionsLayout(8f, 1, 1, showWord = true, footer = false, cap = 12), sessionsLayout(250f, 100f, 12))
        assertEquals(0, sessionsLayout(250f, 70f, 12).maxItems)
    }

    @Test fun sessionsNeverOverGlanceChildLimit() {
        assertEquals(MAX_CHILDREN, sessionsLayout(250f, 1000f, 12).rowsPerColumn)
    }

    @Test fun actionsCompactIconsInGrid() {
        assertEquals(ActionsLayout(8f, 2, 2, labels = false), actionsLayout(150f, 150f, 4))
        // A short cover-screen strip shows what fits in one row.
        assertEquals(ActionsLayout(8f, 1, 3, labels = false), actionsLayout(170f, 70f, 4))
    }

    @Test fun actionsExpanded() {
        assertEquals(ActionsLayout(8f, 1, 6, labels = true), actionsLayout(400f, 100f, 6))
        assertEquals(ActionsLayout(8f, 2, 3, labels = true), actionsLayout(400f, 200f, 6))
    }

    @Test fun actionsDropLabelsRatherThanActions() {
        assertEquals(ActionsLayout(8f, 1, 4, labels = true), actionsLayout(330f, 80f, 4))
        assertEquals(ActionsLayout(8f, 1, 4, labels = false), actionsLayout(250f, 80f, 4))
        assertEquals(1, actionsLayout(250f, 80f, 0).maxItems)
    }

    @Test fun usageCompactOneAccountFiveHour() {
        assertEquals(UsageLayout(12f, 1, 1, weekly = false, resets = true, footer = true), usageLayout(150f, 200f, 3, showWeekly = true))
    }

    @Test fun usageMediumStacks() {
        assertEquals(UsageLayout(12f, 1, 2, weekly = true, resets = true, footer = true), usageLayout(250f, 300f, 3, showWeekly = true))
        assertEquals(UsageLayout(12f, 1, 3, weekly = false, resets = true, footer = true), usageLayout(250f, 300f, 3, showWeekly = false))
    }

    @Test fun usageExpandedSideBySide() {
        assertEquals(UsageLayout(12f, 3, 3, weekly = true, resets = true, footer = true), usageLayout(480f, 200f, 3, showWeekly = true))
        assertEquals(UsageLayout(12f, 2, 2, weekly = true, resets = true, footer = true), usageLayout(480f, 200f, 2, showWeekly = true))
    }

    @Test fun usageGivesUpResetsThenFooter() {
        assertEquals(UsageLayout(12f, 1, 1, weekly = true, resets = false, footer = false), usageLayout(250f, 130f, 1, showWeekly = true))
    }

    @Test fun usageShort() {
        assertEquals(
            UsageLayout(8f, 1, 1, weekly = false, resets = false, footer = false, nameInHeader = true, bars = 1),
            usageLayout(250f, 100f, 3, showWeekly = true),
        )
        assertEquals(0, usageLayout(250f, 60f, 3, showWeekly = true).bars)
    }
}
