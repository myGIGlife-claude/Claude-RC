package life.mygig.clauderc

import life.mygig.clauderc.ui.components.Health
import life.mygig.clauderc.ui.components.description
import life.mygig.clauderc.ui.components.sessionState
import life.mygig.clauderc.ui.components.stateLine
import life.mygig.clauderc.ui.components.stateSpoken
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionStateLineTest {
    @Test fun `the state comes first, before the preview`() {
        assertEquals("working · Reading files", stateLine("working", "Reading files"))
        assertEquals("needs an answer · Allow this?", stateLine("needs an answer", "Allow this?"))
        assertEquals("idle · Done", stateLine("idle", "Done"))
    }

    @Test fun `no preview leaves just the state, with no dangling separator`() {
        assertEquals("idle", stateLine("idle", ""))
        assertEquals("idle", stateSpoken("idle", ""))
    }

    @Test fun `the spoken line uses a comma, not the middle dot`() {
        assertEquals("working, Reading files", stateSpoken("working", "Reading files"))
    }

    @Test fun `waiting wins over busy, as it does for the dot`() {
        assertEquals("needs an answer", sessionState(busy = true, waiting = true))
        assertEquals("needs an answer", sessionState(busy = false, waiting = true))
        assertEquals("working", sessionState(busy = true, waiting = false))
        assertEquals("idle", sessionState(busy = false, waiting = false))
    }

    @Test fun `every health has its own description`() {
        assertEquals("OK", Health.OK.description())
        assertEquals("Needs attention", Health.WARN.description())
        assertEquals("Problem", Health.BAD.description())
        assertEquals("Off", Health.OFF.description())
        assertEquals("Not set up", Health.NONE.description())
    }
}
