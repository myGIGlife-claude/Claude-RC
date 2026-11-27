package life.mygig.clauderc

import life.mygig.clauderc.api.NO_PIN
import life.mygig.clauderc.api.coalescable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoalesceTest {
    @Test fun theEmptyPinLineIsEmpty() {
        assertEquals("", NO_PIN)
    }

    @Test fun pollsShareACall() {
        assertTrue(coalescable("status", null))
        assertTrue(coalescable("sessions", null))
        assertTrue(coalescable("general-list", null))
        assertTrue(coalescable("queue-status", null))
        assertTrue(coalescable("chat-history app", NO_PIN))
        assertTrue(coalescable("chat-commands app", NO_PIN))
    }

    @Test fun writesNeverShare() {
        assertFalse(coalescable("chat-send app", NO_PIN + "\nhello"))
        assertFalse(coalescable("queue-on", NO_PIN))
        assertFalse(coalescable("queue-star app on", NO_PIN))
        assertFalse(coalescable("permissions-set app", NO_PIN + "\n{}"))
        assertFalse(coalescable("upload app", NO_PIN + "\na.png\nAAAA"))
        assertFalse(coalescable("keys app Enter", null))
        assertFalse(coalescable("general-open 20261127-120000-abcd", null))
    }

    @Test fun aReadWithRealStdinDoesNotShare() {
        // Same action name, but anything on stdin besides the empty PIN line makes it a different call.
        assertFalse(coalescable("chat-history app", "123456"))
        assertFalse(coalescable("status", "x"))
    }

    @Test fun matchesTheWholeActionName() {
        assertFalse(coalescable("status-x", null))
        assertFalse(coalescable("chat-history-all app", NO_PIN))
    }
}
