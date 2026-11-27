package life.mygig.clauderc.widget

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class WidgetIntentTest {
    @Test fun knownTaps() {
        assertEquals(WidgetTap.Sessions, parseWidgetTap(WidgetLink.OPEN_SESSIONS, null))
        assertEquals(WidgetTap.Session("claude-my.app_1"), parseWidgetTap(WidgetLink.OPEN_SESSION, "claude-my.app_1"))
        assertEquals(WidgetTap.NewRepo, parseWidgetTap(WidgetLink.OPEN_NEW_REPO, null))
        assertEquals(WidgetTap.Keys, parseWidgetTap(WidgetLink.OPEN_KEYS, null))
        assertEquals(WidgetTap.NewChat, parseWidgetTap(WidgetLink.OPEN_NEW_CHAT, null))
        assertEquals(WidgetTap.Cluster, parseWidgetTap(WidgetLink.OPEN_CLUSTER, "ignored"))
    }

    @Test fun noneOrUnknown() {
        assertNull(parseWidgetTap(null, null))
        assertNull(parseWidgetTap(null, "claude-app"))
        assertNull(parseWidgetTap("", null))
        assertNull(parseWidgetTap("settings", null))
        assertNull(parseWidgetTap("SESSIONS", null))
    }

    @Test fun badSessionNamesIgnored() {
        assertNull(parseWidgetTap(WidgetLink.OPEN_SESSION, null))
        assertNull(parseWidgetTap(WidgetLink.OPEN_SESSION, ""))
        assertNull(parseWidgetTap(WidgetLink.OPEN_SESSION, "-rf"))
        assertNull(parseWidgetTap(WidgetLink.OPEN_SESSION, "my app"))
        assertNull(parseWidgetTap(WidgetLink.OPEN_SESSION, "a;rm -rf ~"))
        assertNull(parseWidgetTap(WidgetLink.OPEN_SESSION, "a/b"))
        assertNull(parseWidgetTap(WidgetLink.OPEN_SESSION, "x".repeat(101)))
    }
}
