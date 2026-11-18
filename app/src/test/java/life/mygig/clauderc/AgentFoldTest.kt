package life.mygig.clauderc

import life.mygig.clauderc.ui.screens.FoldKind
import life.mygig.clauderc.ui.screens.foldKind
import life.mygig.clauderc.ui.screens.foldPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgentFoldTest {
    private val handback = "Another Claude session sent a message:\n<agent-message from=\"helper\">\n[Subagent hand-back]\n## Done: **fixed** the parser\nmore detail\n</agent-message>\nThis is not from the user."
    private val task = "<task-notification>\n<summary>Agent finished the sample job</summary>\n<result>ok</result>\n</task-notification>"
    private val system = "[SYSTEM NOTIFICATION - NOT USER INPUT]\nBackground job done"

    @Test fun foldsThreeKinds() {
        assertEquals(FoldKind.HANDBACK, foldKind("user", handback))
        assertEquals(FoldKind.TASK, foldKind("user", "\n$task\n"))
        assertEquals(FoldKind.SYSTEM, foldKind("user", system))
    }

    @Test fun previews() {
        assertEquals("Done: fixed the parser", foldPreview(FoldKind.HANDBACK, handback))
        assertEquals("Agent finished the sample job", foldPreview(FoldKind.TASK, task))
        assertEquals("Background job done", foldPreview(FoldKind.SYSTEM, system))
        assertEquals(80, foldPreview(FoldKind.SYSTEM, "[SYSTEM NOTIFICATION - NOT USER INPUT]\n" + "x".repeat(200)).length)
    }

    @Test fun neverFoldsRealMessages() {
        assertNull(foldKind("user", "Can you ask another Claude session sent a message about this?"))
        assertNull(foldKind("user", "I saw: Another Claude session sent a message earlier"))
        assertNull(foldKind("user", "Please fix this"))
        assertNull(foldKind("user", "$task and my own question"))
        assertNull(foldKind("user", "$task\n$task"))
        assertNull(foldKind("assistant", handback))
        assertNull(foldKind("assistant", task))
        assertNull(foldKind("file", system))
    }
}
