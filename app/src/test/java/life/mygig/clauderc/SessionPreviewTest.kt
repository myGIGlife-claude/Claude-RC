package life.mygig.clauderc

import life.mygig.clauderc.ui.screens.previewLine
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionPreviewTest {
    @Test fun takesTheLastMeaningfulLineWithoutPromptSymbols() {
        assertEquals("waiting for your next message", previewLine("● Added the 7-day forecast screen\n❯ waiting for your next message\n\n"))
    }

    @Test fun emptyWhenThereIsNothing() {
        assertEquals("", previewLine(""))
        assertEquals("", previewLine("  \n ❯ \n"))
    }
}
