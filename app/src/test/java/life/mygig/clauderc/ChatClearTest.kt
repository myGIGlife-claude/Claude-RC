package life.mygig.clauderc

import life.mygig.clauderc.api.ChatData
import life.mygig.clauderc.api.ChatMessage
import life.mygig.clauderc.ui.vm.withoutCleared
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatClearTest {
    private fun chat(vararg ids: String) = ChatData(messages = ids.map { ChatMessage(id = it, text = it) })

    @Test fun hidesEverythingUpToTheMarker() {
        val marks = mutableMapOf("s" to "b")
        assertEquals(listOf("c"), chat("a", "b", "c").withoutCleared(marks, "s").messages.map { it.id })
        assertEquals("b", marks["s"])   // stays until the new conversation starts
    }

    @Test fun forgetsTheMarkerWhenTheNewConversationStarts() {
        val marks = mutableMapOf("s" to "b")
        assertEquals(listOf("x"), chat("x").withoutCleared(marks, "s").messages.map { it.id })
        assertEquals(emptyMap<String, String>(), marks)
    }

    @Test fun otherSessionsAreUntouched() {
        val marks = mutableMapOf("s" to "b")
        assertEquals(3, chat("a", "b", "c").withoutCleared(marks, "other").messages.size)
    }
}
