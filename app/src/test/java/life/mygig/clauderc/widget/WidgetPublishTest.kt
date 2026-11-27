package life.mygig.clauderc.widget

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import life.mygig.clauderc.api.ClusterAccount
import life.mygig.clauderc.api.Session
import life.mygig.clauderc.api.Usage
import life.mygig.clauderc.api.UsageWindow

class WidgetPublishTest {
    private val old = WidgetSnapshot(
        sessions = listOf(WidgetSession("claude-old", "old")),
        usage = listOf(WidgetUsage("main", 10.0)),
        sessionsAt = 1_000, usageAt = 2_000, configured = true,
    )

    @Test fun failedFetchKeepsOldData() {
        assertSame(old, old.withSessions(null, 9_000))
        assertSame(old, old.withUsage(null, 9_000))
        assertSame(old, old.withSessions(null, 9_000).withUsage(null, 9_000))
    }

    @Test fun sessionsReplacedSortedAndStamped() {
        val s = old.withSessions(listOf(Session("claude-b", "b"), Session("claude-a", "a", waiting = true), Session("chat-x", "x", busy = true, general = true)), 9_000)
        assertEquals(listOf("claude-a", "chat-x", "claude-b"), s.sessions.map { it.name })
        assertTrue(s.sessions[1].general)
        assertEquals(9_000L, s.sessionsAt)
        // The other part is untouched.
        assertEquals(old.usage, s.usage); assertEquals(2_000L, s.usageAt)
    }

    @Test fun emptyListIsRealData() {
        val s = WidgetSnapshot().withSessions(emptyList(), 9_000)
        assertTrue(s.sessions.isEmpty()); assertEquals(9_000L, s.sessionsAt); assertTrue(s.configured)
    }

    @Test fun usageMappedAndStamped() {
        val u = Usage(UsageWindow(40.0, "t"), UsageWindow(null, null))
        val s = old.copy(configured = false).withUsage(listOf(ClusterAccount("main", usage = u), ClusterAccount("w", kind = "codex", usage = u)), 9_000)
        assertEquals(listOf(WidgetUsage("main", 40.0, "t")), s.usage)
        assertEquals(9_000L, s.usageAt); assertTrue(s.configured)
        assertEquals(old.sessions, s.sessions); assertEquals(1_000L, s.sessionsAt)
    }

    @Test fun writesOnlyWhenChangedOrOld() {
        val same = old.withSessions(listOf(Session("claude-old", "old")), old.sessionsAt + 30_000)
        assertFalse(worthWriting(old, same))
        assertTrue(worthWriting(old, old.withSessions(listOf(Session("claude-old", "old")), old.sessionsAt + 61_000)))
        assertTrue(worthWriting(old, old.withSessions(listOf(Session("claude-old", "old", busy = true)), old.sessionsAt + 1)))
        assertTrue(worthWriting(old.copy(configured = false), old))
        assertFalse(worthWriting(old, old))
    }
}
