package life.mygig.clauderc

import life.mygig.clauderc.ui.screens.ROLE_PRESETS
import life.mygig.clauderc.ui.screens.addRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkerRolesTest {
    @Test fun addsToEmpty() = assertEquals("code writing", addRole("", "code writing"))
    @Test fun appendsWithComma() = assertEquals("coding, ui, research", addRole("coding, ui", "research"))
    @Test fun skipsDuplicateIgnoringCase() = assertEquals("Research", addRole("Research", "research"))
    @Test fun tidiesSpacing() = assertEquals("a, b, testing", addRole(" a ,, b ", "testing"))
    @Test fun presetsAreNonBlankAndUnique() {
        assertTrue(ROLE_PRESETS.all { it.isNotBlank() })
        assertEquals(ROLE_PRESETS.size, ROLE_PRESETS.map { it.lowercase() }.toSet().size)
    }
}
