package life.mygig.clauderc.voice

private val FENCE = Regex("```[\\s\\S]*?```")
private val LINK = Regex("\\[([^\\]]*)]\\([^)]*\\)")
private val URL = Regex("https?://\\S+")
private val SYMBOLS = Regex("[*_#>|`~\\\\^=]+|[\\p{So}\\p{Sk}\\p{Cs}\\uFE0F\\u200D]+")
private val BULLET = Regex("(?m)^\\s*(?:[-•+]|\\d+[.)])\\s+")
private val BLANKS = Regex("[ \\t]{2,}")
private val PRONUNCIATIONS = listOf(
    Regex("\\bpuddin(?!g)['’]?", RegexOption.IGNORE_CASE) to "pudding",
    Regex("cLaudeRC", RegexOption.IGNORE_CASE) to "Claude R C",
)

/**
 * Claude's reply as it should be spoken: no markdown symbols, code blocks, link addresses or emoji (the voice stalls on them or
 * reads them out), and a few words respelled so they come out right. Add to PRONUNCIATIONS as words come up.
 */
fun speechText(raw: String): String {
    var t = FENCE.replace(raw, ".\n")
    t = LINK.replace(t, "$1")
    t = URL.replace(t, "a link")
    t = BULLET.replace(t, "")
    t = SYMBOLS.replace(t, if (t.contains('_')) " " else "")
    t = PRONUNCIATIONS.fold(t) { x, (re, to) -> re.replace(x, to) }
    return t.lines().map { BLANKS.replace(it, " ").trim() }.filter { l -> l.any { it.isLetterOrDigit() } }.joinToString("\n")
}

/**
 * The first sentence, if long, is cut near the start (at a comma or dash, else at a space) so speech begins sooner; later
 * sentences stay whole because every cut adds a pause. Anything still over 3000 characters is cut by size (the phone engine's limit).
 */
fun firstPieces(sentence: String): List<String> {
    val s = sentence.trim()
    if (s.length <= 90) return s.chunked(3000)
    val head = s.take(90)
    val cut = Regex("[,;—–] ").findAll(head).lastOrNull()?.range?.last?.takeIf { it > 25 }
        ?: head.lastIndexOf(' ').takeIf { it > 25 } ?: return s.chunked(3000)
    return listOf(s.take(cut + 1).trim()) + s.drop(cut + 1).trim().chunked(3000)
}
