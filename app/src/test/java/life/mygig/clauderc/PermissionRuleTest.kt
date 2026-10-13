package life.mygig.clauderc

import life.mygig.clauderc.api.describePermissionRule
import life.mygig.clauderc.api.isValidPermissionRule
import life.mygig.clauderc.api.permissionChange
import life.mygig.clauderc.api.permissionRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionRuleTest {
    @Test fun validRules() {
        listOf("Read", "Bash(git status:*)", "Edit(src/**)", "WebFetch(domain:example.com)", "mcp__github__list_issues").forEach { assertTrue(it, isValidPermissionRule(it)) }
    }

    @Test fun invalidRules() {
        listOf("", "a b", "Bash()", "Bash(a(b))", "Bash(git)x", "1Bash", "Bash(a\nb)").forEach { assertFalse(it, isValidPermissionRule(it)) }
    }

    @Test fun joinsToolAndSpecifier() {
        assertEquals("Bash(git diff:*)", permissionRule("Bash", " git diff:* "))
        assertEquals("Read", permissionRule("Read", "  "))
    }

    @Test fun changeIsOneJsonLine() {
        val j = permissionChange("local", "add", list = "allow", rule = "Bash(git status:*)")
        assertFalse(j.contains('\n'))
        assertTrue(j.contains("\"rule\":\"Bash(git status:*)\""))
    }

    @Test fun describesRules() {
        assertEquals("Bash commands starting git status", describePermissionRule("Bash(git status:*)"))
        assertEquals("Every Read action", describePermissionRule("Read"))
        assertEquals("Fetching pages from example.com", describePermissionRule("WebFetch(domain:example.com)"))
    }
}
