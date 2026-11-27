package life.mygig.clauderc

import life.mygig.clauderc.ui.vm.askMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AskSessionTest {
    @Test fun namesReasonAndQuestionAreThere() {
        val m = askMessage("chat-1", "proj-b", "  What port?\nAnd why?  ")
        assertTrue(m.contains("\"proj-b\""))
        assertTrue(m.contains("Question from Jonathan via session chat-1"))
        assertTrue(m.contains("Reason: Jonathan asked me to relay this question."))
        assertTrue(m.endsWith("Question: What port?\nAnd why?"))
    }

    @Test fun blankQuestionIsRefused() = assertEquals("", askMessage("a", "b", "  \n "))

    @Test fun longQuestionIsCapped() {
        val m = askMessage("a", "b", "x".repeat(50_000))
        assertEquals(20_000, m.length)
    }
}
