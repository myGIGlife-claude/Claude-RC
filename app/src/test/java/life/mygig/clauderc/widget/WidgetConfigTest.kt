package life.mygig.clauderc.widget

import org.junit.Test
import org.junit.Assert.assertEquals

class WidgetConfigTest {
    private val sessions = listOf(
        WidgetSession("a", "Alpha"),
        WidgetSession("b", "Beta", busy = true),
        WidgetSession("c", "Gamma", waiting = true),
    )

    @Test fun roundTripAndBadInput() {
        val c = WidgetConfig(kind = WidgetKind.SESSIONS, filter = SessionFilter.PICKED, sessions = listOf("a"), maxRows = 5)
        assertEquals(c, decodeConfig(c.encode()))
        assertEquals(WidgetConfig(), decodeConfig("{broken"))
        assertEquals(WidgetConfig(), decodeConfig(null))
        assertEquals(WidgetConfig(maxRows = 3), decodeConfig("""{"maxRows":3,"future":true}"""))
    }

    @Test fun sessionFilters() {
        assertEquals(listOf("c", "b", "a"), WidgetConfig().pickSessions(sessions).map { it.name })
        assertEquals(listOf("c", "b"), WidgetConfig(filter = SessionFilter.ACTIVE).pickSessions(sessions).map { it.name })
        assertEquals(listOf("a"), WidgetConfig(filter = SessionFilter.PICKED, sessions = listOf("a", "gone")).pickSessions(sessions).map { it.name })
        assertEquals(emptyList<WidgetSession>(), WidgetConfig(filter = SessionFilter.PICKED).pickSessions(sessions))
        // An unknown filter (newer app wrote it) shows all.
        assertEquals(3, WidgetConfig(filter = "later").pickSessions(sessions).size)
    }

    @Test fun rowCapClamped() {
        assertEquals(5, WidgetConfig(maxRows = 5).rowCap())
        assertEquals(1, WidgetConfig(maxRows = 0).rowCap())
        assertEquals(12, WidgetConfig(maxRows = 99).rowCap())
    }

    @Test fun accountSelection() {
        val usage = listOf(WidgetUsage("main", 10.0), WidgetUsage("work", 80.0), WidgetUsage("spare", null, null, 95.0))
        assertEquals(usage, WidgetConfig().pickAccounts(usage))
        assertEquals(listOf("work"), WidgetConfig(accounts = listOf("work")).pickAccounts(usage).map { it.account })
        // Ticked accounts that are all gone: show all rather than an empty widget.
        assertEquals(usage, WidgetConfig(accounts = listOf("old")).pickAccounts(usage))
        assertEquals(listOf("spare", "work", "main"), usage.mostUsedFirst().map { it.account })
    }

    @Test fun actionsSanitized() {
        assertEquals(DEFAULT_ACTIONS, sanitizeActions(emptyList()).map { it.id })
        assertEquals(DEFAULT_ACTIONS, sanitizeActions(listOf("bogus")).map { it.id })
        assertEquals(listOf("usage", "new_chat"), sanitizeActions(listOf("usage", "bogus", "new_chat", "usage")).map { it.id })
        val many = QUICK_ACTIONS.map { it.id } + QUICK_ACTIONS.map { it.id }
        assertEquals(QUICK_ACTIONS.size.coerceAtMost(MAX_ACTIONS), sanitizeActions(many).size)
    }

    @Test fun toggleAndMove() {
        val l = listOf("a", "b", "c")
        assertEquals(listOf("a", "c"), l.toggled("b"))
        assertEquals(listOf("a", "b", "c", "d"), l.toggled("d"))
        assertEquals(l, l.toggled("d", max = 3))
        assertEquals(listOf("b", "a", "c"), l.moved("b", -1))
        assertEquals(listOf("a", "c", "b"), l.moved("b", 1))
        assertEquals(l, l.moved("a", -1))
        assertEquals(l, l.moved("c", 1))
        assertEquals(l, l.moved("x", 1))
    }
}
