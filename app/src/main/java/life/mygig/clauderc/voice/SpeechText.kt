package life.mygig.clauderc.voice

private val FENCE = Regex("```[\\s\\S]*?```")
private val LINK = Regex("\\[([^\\]]*)]\\([^)]*\\)")
private val URL = Regex("https?://\\S+")
private val SYMBOLS = Regex("[*_#>|`~\\\\^=]+|[\\p{So}\\p{Sk}\\p{Cs}\\uFE0F\\u200D]+")
private val BULLET = Regex("(?m)^\\s*(?:[-•+]|\\d+[.)])\\s+")
private val BLANKS = Regex("[ \\t]{2,}")
// $10, $1,250, $10.50, $0.99, $1.5M, $5k, $20/month (also euro and pound signs): said as words, not as "dollar sign, ten".
private val MONEY = Regex("(?<![\\w])([\\u0024\\u20AC\\u00A3])\\s?(\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.(\\d{1,2}))?\\s?([kKmMbB])?(?![\\w])(?:/(month|mo|year|yr|day|hour|hr|week|wk)\\b)?")
private val PER = mapOf("mo" to "month", "yr" to "year", "hr" to "hour", "wk" to "week")

private fun money(m: MatchResult): String {
    val (sym, rawWhole, rawFrac, suffix, per) = m.destructured
    val whole = rawWhole.replace(",", "")
    val big = when (suffix.lowercase()) { "k" -> "thousand"; "m" -> "million"; "b" -> "billion"; else -> "" }
    val (major, minor) = when (sym) { "€" -> "euro" to "cent"; "£" -> "pound" to "pence"; else -> "dollar" to "cent" }
    fun plural(n: String, w: String) = if (n == "1" || w == "pence") w else w + "s"
    val tail = if (per.isEmpty()) "" else " per " + (PER[per] ?: per)
    if (big.isNotEmpty()) return "$whole${if (rawFrac.isNotEmpty()) ".$rawFrac" else ""} $big ${major}s$tail"
    val cents = rawFrac.padEnd(2, '0').toIntOrNull() ?: 0
    return when {
        cents == 0 -> "$whole ${plural(whole, major)}$tail"
        whole == "0" -> "$cents ${plural(cents.toString(), minor)}$tail"
        else -> "$whole ${plural(whole, major)} and $cents ${plural(cents.toString(), minor)}$tail"
    }
}

private val PRONUNCIATIONS = listOf(
    Regex("\\bpuddin(?!g)['’]?", RegexOption.IGNORE_CASE) to "pudding",
    Regex("cLaudeRC", RegexOption.IGNORE_CASE) to "Claude R C",
)

/**
 * Claude's reply as it should be spoken: no markdown symbols, code blocks, link addresses or emoji (the voice stalls on them or
 * reads them out), and a few words respelled so they come out right. Add to PRONUNCIATIONS as words come up.
 */
fun speechText(raw: String): String {
    var t = FENCE.replace(raw, "\n")
    t = LINK.replace(t, "$1")
    t = URL.replace(t, "a link")
    t = BULLET.replace(t, "")
    t = MONEY.replace(t, ::money)
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
