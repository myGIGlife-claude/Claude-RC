package life.mygig.clauderc

import kotlinx.serialization.json.Json
import life.mygig.clauderc.api.GENERAL_CHAT_ID_RE
import life.mygig.clauderc.api.GeneralChat
import life.mygig.clauderc.api.GeneralChats
import life.mygig.clauderc.api.GeneralNew
import life.mygig.clauderc.api.GeneralOpen
import life.mygig.clauderc.api.SessionsData
import life.mygig.clauderc.api.cleanGeneralTitle
import life.mygig.clauderc.ui.vm.chatSession
import life.mygig.clauderc.ui.vm.generalTitle
import life.mygig.clauderc.ui.vm.relativeTime
import life.mygig.clauderc.ui.vm.sortGeneralChats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneralChatsTest {
    // Same settings as LauncherApi.
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test fun chatIdRule() {
        for (ok in listOf("20261010-153000-0a9f", "00000000-000000-ffff")) assertTrue(ok, GENERAL_CHAT_ID_RE.matches(ok))
        for (bad in listOf("", "chat-20261010-153000-0a9f", "20261010-153000-0A9F", "2026101-153000-0a9f", "20261010-153000-0a9f ", "../x", "20261010-153000-0a9", "-20261010-153000-0a9f"))
            assertFalse(bad, GENERAL_CHAT_ID_RE.matches(bad))
    }

    @Test fun parsesList() {
        val d = json.decodeFromString<GeneralChats>(
            """{"chats":[
              {"id":"20261010-153000-0a9f","title":"Trip ideas","dir":"/x/chats/20261010-153000-0a9f","running":true,"session":"chat-20261010-153000-0a9f","created_at":1760100000,"updated_at":1760103600,"preview":"> hi","busy":false,"waiting":true},
              {"id":"20261009-080000-1111","title":"","dir":"","running":false,"session":"","created_at":1760000000,"updated_at":1760000000,"preview":"","busy":false,"waiting":false,"extra":1}
            ]}""",
        )
        assertEquals(2, d.chats.size)
        val a = d.chats[0]
        assertTrue(a.running && a.waiting)
        assertEquals("chat-20261010-153000-0a9f", a.session)
        assertEquals(1760103600L, a.updatedAt)
        assertFalse(d.chats[1].running)
        assertEquals("Untitled chat", generalTitle(d.chats[1]))
        assertEquals("Trip ideas", generalTitle(a))
    }

    @Test fun parsesActionReplies() {
        val n = json.decodeFromString<GeneralNew>("""{"id":"20261010-153000-0a9f","session":"chat-20261010-153000-0a9f","dir":"/x"}""")
        assertEquals("chat-20261010-153000-0a9f", n.session)
        val o = json.decodeFromString<GeneralOpen>("""{"id":"20261010-153000-0a9f","session":"chat-20261010-153000-0a9f","already_running":true}""")
        assertTrue(o.alreadyRunning)
        assertEquals("chat-20261010-153000-0a9f", chatSession(o.id))
        assertEquals(GeneralChats(), json.decodeFromString<GeneralChats>("{}"))
    }

    @Test fun sessionsWithAndWithoutGeneralFields() {
        val old = json.decodeFromString<SessionsData>("""{"now":1,"sessions":[{"name":"app","project":"app","uptime_seconds":5}]}""")
        assertFalse(old.sessions[0].general)
        assertEquals("", old.sessions[0].chatId)
        val new = json.decodeFromString<SessionsData>(
            """{"now":1,"sessions":[{"name":"chat-20261010-153000-0a9f","project":"chat-20261010-153000-0a9f","general":true,"chat_id":"20261010-153000-0a9f"},{"name":"app","project":"app","general":false,"chat_id":null}]}""",
        )
        assertTrue(new.sessions[0].general)
        assertEquals("20261010-153000-0a9f", new.sessions[0].chatId)
        assertEquals(listOf("app"), new.sessions.filterNot { it.general }.map { it.name })
    }

    @Test fun newestActivityFirst() {
        val a = GeneralChat("a", createdAt = 100, updatedAt = 500)
        val b = GeneralChat("b", createdAt = 900)   // never updated: counts from creation
        val c = GeneralChat("c", createdAt = 50, updatedAt = 60)
        assertEquals(listOf("b", "a", "c"), sortGeneralChats(listOf(a, b, c)).map { it.id })
    }

    @Test fun relativeTimes() {
        val now = 1_760_000_000L
        assertEquals("", relativeTime(0, now))
        assertEquals("just now", relativeTime(now - 30, now))
        assertEquals("just now", relativeTime(now + 120, now))   // server clock ahead
        assertEquals("1 minute ago", relativeTime(now - 60, now))
        assertEquals("5 minutes ago", relativeTime(now - 300, now))
        assertEquals("1 hour ago", relativeTime(now - 3_600, now))
        assertEquals("2 hours ago", relativeTime(now - 7_300, now))
        assertEquals("yesterday", relativeTime(now - 90_000, now))
        assertEquals("3 days ago", relativeTime(now - 3 * 86_400, now))
        assertEquals("2 months ago", relativeTime(now - 61 * 86_400, now))
        assertEquals("1 year ago", relativeTime(now - 400 * 86_400L, now))
        assertEquals("2 hours ago", relativeTime((now - 7_300) * 1000, now * 1000))   // milliseconds
    }

    @Test fun titleCleaning() {
        assertEquals("Trip ideas", cleanGeneralTitle("  Trip ideas \n"))
        assertEquals("a b", cleanGeneralTitle("a\nb"))
        assertNull(cleanGeneralTitle("   "))
        assertNull(cleanGeneralTitle("x".repeat(81)))
        assertEquals(80, cleanGeneralTitle("x".repeat(80))?.length)
    }
}
