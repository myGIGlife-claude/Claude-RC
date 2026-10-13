package life.mygig.clauderc.api

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Same shape the server accepts: `Tool` or `Tool(specifier)`, no nested parentheses. */
private val RULE_RE = Regex("""^[A-Za-z][A-Za-z0-9_.:-]{0,79}(\([^()\r\n]{1,250}\))?$""")

fun isValidPermissionRule(rule: String): Boolean = RULE_RE.matches(rule)

/** Joins a tool and what it applies to: ("Bash", "git status:*") -> Bash(git status:*); a blank specifier means the whole tool. */
fun permissionRule(tool: String, specifier: String): String = tool.trim() + specifier.trim().let { if (it.isEmpty()) "" else "($it)" }

/** The JSON line permissions-set reads on stdin. */
fun permissionChange(scope: String, op: String, list: String = "", rule: String = "", new: String = "", mode: String = ""): String =
    buildJsonObject { put("scope", scope); put("op", op); put("list", list); put("rule", rule); put("new", new); put("mode", mode) }.toString()

/** A plain-English line under a rule, e.g. "Bash commands starting git status". */
fun describePermissionRule(rule: String): String {
    val tool = rule.substringBefore('(')
    val spec = if (rule.contains('(')) rule.substringAfter('(').removeSuffix(")") else ""
    return when {
        spec.isEmpty() -> "Every $tool action"
        tool == "Bash" && spec.endsWith(":*") -> "Bash commands starting " + spec.removeSuffix(":*")
        tool == "Bash" -> "The Bash command $spec"
        tool == "WebFetch" && spec.startsWith("domain:") -> "Fetching pages from " + spec.removePrefix("domain:")
        tool == "Read" || tool == "Edit" || tool == "Write" -> "$tool for $spec"
        else -> "$tool: $spec"
    }
}
