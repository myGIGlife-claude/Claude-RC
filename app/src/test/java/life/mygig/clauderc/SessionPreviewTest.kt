package life.mygig.clauderc

import life.mygig.clauderc.ui.screens.previewLine
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionPreviewTest {
    @Test fun takesTheLastMeaningfulLineWithoutPromptSymbols() {
        assertEquals("waiting for your next message", previewLine("● Added the 7-day forecast screen\n❯ waiting for your next message\n\n"))
    }

    @Test fun skipsTheCostStatusLineAndTheSpinner() {
        val screen = "Done. Tests pass (24/24).\n· Catapulting… (10s · ↓ 622 tokens)\n● full  \$42.21  last \$0.80  41m 31s     Review  Audit  Board"
        assertEquals("Done. Tests pass (24/24).", previewLine(screen))
    }

    @Test fun emptyWhenThereIsNothing() {
        assertEquals("", previewLine(""))
        assertEquals("", previewLine("  \n ❯ \n"))
    }
}
