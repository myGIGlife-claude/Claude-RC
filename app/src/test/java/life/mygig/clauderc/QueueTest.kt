package life.mygig.clauderc

import java.time.ZoneId
import kotlinx.serialization.json.Json
import life.mygig.clauderc.api.QueueChat
import life.mygig.clauderc.api.QueueLog
import life.mygig.clauderc.api.QueueStarResult
import life.mygig.clauderc.api.QueueStatus
import life.mygig.clauderc.api.QueueTone
import life.mygig.clauderc.api.QueueUsage
import life.mygig.clauderc.api.Session
import life.mygig.clauderc.api.queueChip
import life.mygig.clauderc.api.queueLine
import life.mygig.clauderc.api.queueUsageText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test fun parsesStatus() {
        val d = json.decodeFromString<QueueStatus>(
            """{"enabled":true,"parallel":2,"max_5h":80,"max_7d":90,
               "usage":{"five":62.5,"seven":41,"five_resets":"2026-11-16T14:05:00Z","seven_resets":null},
               "limited_until":0,
               "chats":[{"session":"app","state":"running","since":10,"note":"","turns":3,"next_try":0,"running_min":7},{"session":"web"}]}""",
        )
        assertTrue(d.enabled)
        assertEquals(2, d.parallel)
        assertEquals(80.0, d.max5h!!, 0.0)
        assertEquals(62.5, d.usage.five!!, 0.0)
        assertEquals(null, d.usage.sevenResets)
        assertEquals(7, d.chats[0].runningMin)
        assertEquals("idle", d.chats[1].state)   // missing fields fall back
    }

    @Test fun parsesStatusWithNullUsage() {
        val d = json.decodeFromString<QueueStatus>(
            """{"enabled":false,"parallel":1,"max_5h":null,"max_7d":null,"usage":{"five":null,"seven":null,"five_resets":null,"seven_resets":null},"limited_until":0,"chats":[]}""",
        )
        assertFalse(d.enabled)
        assertEquals("usage unknown", queueUsageText(d.usage))
    }

    @Test fun parsesStarAndLog() {
        assertEquals(QueueStarResult("app", true), json.decodeFromString<QueueStarResult>("""{"session":"app","starred":true}"""))
        val l = json.decodeFromString<QueueLog>("""{"lines":[{"ts":"2026-11-16T10:00:00Z","session":"-","event":"on","detail":""},{"ts":"t","session":"app","event":"done"}]}""")
        assertEquals(2, l.lines.size)
        assertEquals("on", l.lines[0].event)
        assertEquals("", l.lines[1].detail)
        assertEquals(0, json.decodeFromString<QueueLog>("""{"lines":[]}""").lines.size)
    }

    @Test fun sessionStarredDefaultsOff() {
        assertFalse(json.decodeFromString<Session>("""{"name":"a","project":"a"}""").starred)
        assertTrue(json.decodeFromString<Session>("""{"name":"a","project":"a","starred":true}""").starred)
    }

    @Test fun chipsByState() {
        assertEquals("working", queueChip("running").label)
        assertEquals(QueueTone.OK, queueChip("running").tone)
        assertEquals("question", queueChip("question").label)
        assertEquals(QueueTone.WARN, queueChip("question").tone)
        assertEquals(QueueTone.WARN, queueChip("needs_you").tone)
        assertEquals("nothing to do", queueChip("nothing").label)
        assertEquals("limited", queueChip("limited").label)
        assertEquals("error", queueChip("error").label)
        assertEquals(QueueTone.WARN, queueChip("error").tone)
        assertEquals("waiting", queueChip("idle").label)
        assertEquals("waiting", queueChip("something-new").label)
        assertEquals(QueueTone.IDLE, queueChip("backoff").tone)
    }

    @Test fun usageText() {
        assertEquals("5h 62% · 7d 41%", queueUsageText(QueueUsage(five = 62.4, seven = 41.0)))
        assertEquals("7d 41%", queueUsageText(QueueUsage(seven = 41.0)))
    }

    @Test fun topBarLine() {
        val utc = ZoneId.of("UTC")
        fun chat(state: String) = QueueChat(session = "a", state = state)
        assertEquals("queue: off", queueLine(false, chat("running"), 0, utc))
        assertEquals("queue: running", queueLine(true, chat("running"), 0, utc))
        assertEquals("waiting for you", queueLine(true, chat("question"), 0, utc))
        assertEquals("nothing to do", queueLine(true, chat("nothing"), 0, utc))
        assertEquals("paused: usage until 14:05", queueLine(true, chat("limited"), 1_794_751_500, utc))
        assertEquals("paused: usage", queueLine(true, chat("limited"), 0, utc))
        assertEquals("queue: starred", queueLine(true, null, 0, utc))
    }
}
