package life.mygig.clauderc

import life.mygig.clauderc.ui.screens.modeLabel
import life.mygig.clauderc.ui.screens.modelAlias
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatControlsTest {
    @Test fun readsTheModelAliasFromTheReportedId() {
        assertEquals("opus", modelAlias("claude-opus-5-5"))
        assertEquals("sonnet", modelAlias("claude-sonnet-5-5"))
        assertEquals("haiku", modelAlias("claude-haiku-5-5"))
        assertNull(modelAlias(""))
    }

    @Test fun namesTheModes() {
        assertEquals("Accept edits", modeLabel("edits"))
        assertEquals("Ask first", modeLabel("default"))
        assertEquals("Plan", modeLabel("plan"))
    }
}
