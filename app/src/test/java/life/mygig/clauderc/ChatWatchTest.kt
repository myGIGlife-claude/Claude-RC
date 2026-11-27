package life.mygig.clauderc

import kotlinx.serialization.json.Json
import life.mygig.clauderc.api.ChatData
import life.mygig.clauderc.api.NO_PIN
import life.mygig.clauderc.api.coalescable
import life.mygig.clauderc.ui.vm.watchDelayMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatWatchTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test fun delays() {
        assertEquals(4_000L, watchDelayMs(ChatData(retry = true, changed = false)))
        assertEquals(1_500L, watchDelayMs(ChatData(changed = true)))
        assertEquals(0L, watchDelayMs(ChatData(changed = false, token = "v1.1.2.3.000.0")))
    }

    @Test fun oldServerAnswersStillDecode() {
        val d = json.decodeFromString<ChatData>("""{"session":"a","messages":[],"waiting":false,"busy":true,"mode":"default"}""")
        assertTrue(d.changed)
        assertFalse(d.retry)
        assertEquals("", d.token)
    }

    @Test fun watchAnswersDecode() {
        val d = json.decodeFromString<ChatData>("""{"session":"a","changed":false,"token":"v1.1.2.3.000.0"}""")
        assertFalse(d.changed)
        assertEquals("v1.1.2.3.000.0", d.token)
        assertTrue(json.decodeFromString<ChatData>("""{"session":"a","changed":false,"token":"","retry":true}""").retry)
    }

    @Test fun watchNeverShares() {
        assertFalse(coalescable("chat-watch app", NO_PIN + "\nv1.1.2.3.000.0"))
    }
}
