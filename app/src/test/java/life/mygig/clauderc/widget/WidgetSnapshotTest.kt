package life.mygig.clauderc.widget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import life.mygig.clauderc.api.ClusterAccount
import life.mygig.clauderc.api.Usage
import life.mygig.clauderc.api.UsageWindow

class WidgetSnapshotTest {
    @Test fun roundTripAndBadInput() {
        val s = WidgetSnapshot(sessions = listOf(WidgetSession("claude-app", "app", busy = true)), sessionsAt = 5, configured = true)
        assertEquals(s, decodeSnapshot(s.encode()))
        assertEquals(WidgetSnapshot(), decodeSnapshot("not json"))
        assertEquals(WidgetSnapshot(), decodeSnapshot(null))
    }

    @Test fun sortsWaitingThenBusyThenIdle() {
        val l = listOf(WidgetSession("c", "Cc"), WidgetSession("b", "Bb", busy = true), WidgetSession("a", "Aa", waiting = true, busy = true), WidgetSession("d", "Ad"))
        assertEquals(listOf("a", "b", "d", "c"), l.forWidget().map { it.name })
    }

    @Test fun usageOnlyForClaudeWithNumbers() {
        val u = Usage(UsageWindow(40.0, "t"), UsageWindow(null, null))
        assertEquals(40.0, ClusterAccount("main", usage = u).toWidgetUsage()?.fiveHourPct)
        assertNull(ClusterAccount("w", kind = "codex", usage = u).toWidgetUsage())
        assertNull(ClusterAccount("w", usage = Usage()).toWidgetUsage())
        assertNull(ClusterAccount("w").toWidgetUsage())
    }

    @Test fun ageAndBar() {
        assertEquals("never", ageLabel(0, 1))
        assertEquals("just now", ageLabel(1_000, 2_000))
        assertEquals("3 min ago", ageLabel(0 + 60_000, 60_000 + 180_000))
        assertEquals("2 h ago", ageLabel(60_000, 60_000 + 2 * 3_600_000))
        assertEquals(100, barPct(140.0)); assertEquals(0, barPct(-3.0)); assertNull(barPct(null))
    }

    @Test fun sessionNameValidation() {
        assertTrue(WidgetLink.validSession("claude-my.app_1"))
        assertFalse(WidgetLink.validSession("-rf")); assertFalse(WidgetLink.validSession("a b")); assertFalse(WidgetLink.validSession(null))
    }
}
