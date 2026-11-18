package life.mygig.clauderc.ui.screens

/** Harness-injected user-role entries that the chat folds into one collapsed row. */
enum class FoldKind(val label: String) {
    HANDBACK("Agent report"),
    TASK("Agent report"),
    SYSTEM("Automated notice"),
}

private const val HANDBACK_START = "Another Claude session sent a message"
private const val SYSTEM_START = "[SYSTEM NOTIFICATION - NOT USER INPUT]"
private const val TASK_OPEN = "<task-notification>"
private const val TASK_CLOSE = "</task-notification>"

/** Conservative: only a user message that STARTS with a known harness wrapper (or is nothing but one task block) folds. */
fun foldKind(role: String, text: String): FoldKind? {
    if (role != "user") return null
    val t = text.trim()
    return when {
        t.startsWith(HANDBACK_START) -> FoldKind.HANDBACK
        t.startsWith(SYSTEM_START) -> FoldKind.SYSTEM
        t.startsWith(TASK_OPEN) && t.endsWith(TASK_CLOSE) && t.split(TASK_OPEN).size == 2 -> FoldKind.TASK
        else -> null
    }
}

private val TAG_LINE = Regex("^</?[A-Za-z][\\w-]*(\\s[^>]*)?>$")

/** First meaningful line of a folded message, plain text, at most [max] characters. */
fun foldPreview(kind: FoldKind, text: String, max: Int = 80): String {
    val body = if (kind == FoldKind.TASK) {
        Regex("<summary>(.*?)</summary>", RegexOption.DOT_MATCHES_ALL).find(text)?.groupValues?.get(1) ?: text
    } else text
    val line = body.lineSequence().map { it.trim() }.firstOrNull { l ->
        l.isNotEmpty() && !l.startsWith(HANDBACK_START) && !l.startsWith(SYSTEM_START) &&
            !l.startsWith("[Subagent hand-back]") && !TAG_LINE.matches(l)
    } ?: ""
    val plain = line.replace(Regex("<[^>]*>"), "").replace(Regex("^[#>*\\-\\s]+"), "").replace(Regex("[*_`]"), "").trim()
    return if (plain.length > max) plain.take(max - 1).trimEnd() + "…" else plain
}
