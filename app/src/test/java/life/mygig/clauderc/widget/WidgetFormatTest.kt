package life.mygig.clauderc.widget

import java.time.Instant
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class WidgetFormatTest {
    private val now = Instant.parse("2026-10-28T12:00:00Z")

    @Test fun resetText() {
        assertEquals("resets in 2 h 10 min", resetsInText("2026-10-28T14:10:00Z", now))
        assertEquals("resets in 3 h", resetsInText("2026-10-28T15:00:00Z", now))
        assertEquals("resets in 45 min", resetsInText("2026-10-28T12:45:30Z", now))
        assertEquals("resets in under a minute", resetsInText("2026-10-28T12:00:20Z", now))
        assertEquals("resets in 2 d 5 h", resetsInText("2026-10-30T17:00:00Z", now))
        assertEquals("resets in 1 d", resetsInText("2026-10-29T12:00:00Z", now))
        // Offsets and fractions as the server may send them.
        assertEquals("resets in 1 h", resetsInText("2026-10-28T15:00:00.123+02:00", now))
    }

    @Test fun resetTextFallsBackToNothing() {
        assertNull(resetsInText(null, now))
        assertNull(resetsInText("", now))
        assertNull(resetsInText("tomorrow", now))
        assertNull(resetsInText("2026-10-28T11:00:00Z", now))
    }

    @Test fun barLevels() {
        assertEquals(BarLevel.OK, barLevel(0))
        assertEquals(BarLevel.OK, barLevel(74))
        assertEquals(BarLevel.WARN, barLevel(75))
        assertEquals(BarLevel.WARN, barLevel(89))
        assertEquals(BarLevel.BAD, barLevel(90))
        assertEquals(BarLevel.BAD, barLevel(100))
    }

    @Test fun rowsSortedAndCapped() {
        val many = (1..20).map { WidgetSession("s$it", "p%02d".format(it)) } + WidgetSession("w", "zz", waiting = true)
        val rows = sessionRows(many)
        assertEquals(MAX_SESSION_ROWS, rows.size)
        assertEquals("w", rows.first().name)
        assertEquals(emptyList<WidgetSession>(), sessionRows(emptyList()))
    }

    @Test fun staleAndFooter() {
        val t = 1_000_000_000L
        assertFalse(isStale(t, t + STALE_AFTER_MS))
        assertTrue(isStale(t, t + STALE_AFTER_MS + 1))
        assertTrue(isStale(0, t))
        assertEquals("Updated 3 min ago", footerText(t, t + 3 * 60_000))
        assertEquals("Updated 1 h ago, phone locked or offline?", footerText(t, t + 61 * 60_000))
        assertEquals("Not updated yet", footerText(0, t))
    }

    @Test fun stateWords() {
        assertEquals("needs you", stateWord(Dot.WAITING))
        assertEquals("working", stateWord(Dot.BUSY))
        assertEquals("idle", stateWord(Dot.IDLE))
    }
}
