package life.mygig.clauderc.ui.screens

import android.content.ClipData
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------------------------------------------------
// Pure parser: a reply's text cut into blocks (prose, headings, code). Nothing is ever dropped or reordered: every block
// keeps the exact source lines it came from (Segment.raw), and the raws joined give back the input (line ends as \n).
// ---------------------------------------------------------------------------------------------------------------------

/** One piece of a chat message. */
sealed interface Block {
    /** Code or commands: [code] is exactly what Copy puts on the clipboard (no fences, no "$ " prompts, no trailing blank lines). */
    data class Code(val lang: String, val code: String) : Block
    /** Anything else; inline marks (**bold**, `code`, links) are left for [linkify]. */
    data class Text(val text: String) : Block
    /** A "# ", "## " or "### " line. */
    data class Heading(val level: Int, val text: String) : Block
}

/** A block plus the exact source lines it was made from. */
internal data class Segment(val block: Block, val raw: String)

fun parseBlocks(text: String): List<Block> = parseSegments(text).map { it.block }

/** \r\n and lone \r become \n: the parser (and the property that raws rejoin to the input) works on this form. */
internal fun normalizeNewlines(text: String): String = text.replace("\r\n", "\n").replace('\r', '\n')

private val FENCE_OPEN = Regex("""^([ \t]*)(`{3,}|~{3,})(.*)$""")
private val HEADING = Regex("""^(#{1,3}) +(\S.*)$""")
private val LIST_ITEM = Regex("""^[ \t]*([-*+•]|\d{1,3}[.)])[ \t]+\S.*$""")
private val PROMPT = Regex("""^[ \t]*\$ """)
private val SHELLS = setOf("shell", "sh", "bash", "zsh", "console", "terminal", "shell-session", "fish", "powershell", "ps", "cmd", "bat")

internal fun parseSegments(input: String): List<Segment> {
    val text = normalizeNewlines(input)
    if (text.isEmpty()) return emptyList()
    val lines = text.split("\n")
    val n = lines.size
    val out = mutableListOf<Segment>()
    // Source of lines [a, b): the newline after line b-1 belongs to this segment unless b-1 is the last line.
    fun raw(a: Int, b: Int) = lines.subList(a, b).joinToString("\n") + if (b < n) "\n" else ""
    var textStart = -1
    fun flushText(upTo: Int) {
        if (textStart >= 0 && textStart < upTo) out += Segment(Block.Text(lines.subList(textStart, upTo).joinToString("\n")), raw(textStart, upTo))
        textStart = -1
    }
    var i = 0
    while (i < n) {
        val line = lines[i]
        // Fenced code (``` or ~~~, any indent so fences inside list items count). Unclosed = code until the end.
        val open = FENCE_OPEN.matchEntire(line)
        if (open != null && !(open.groupValues[2][0] == '`' && open.groupValues[3].contains('`'))) {
            val indent = indentWidth(open.groupValues[1])
            val fence = open.groupValues[2]
            val close = Regex("^[ \\t]*" + fence[0] + "{" + fence.length + ",}[ \\t]*$")
            var j = i + 1
            while (j < n && !close.matches(lines[j])) j++
            val body = lines.subList(i + 1, j).map { stripIndent(it, indent) }
            val end = if (j < n) j + 1 else n
            flushText(i)
            out += Segment(Block.Code(fenceLang(open.groupValues[3]), trimCode(body)), raw(i, end))
            i = end
            continue
        }
        // A run of "$ command" lines.
        if (PROMPT.containsMatchIn(line)) {
            var j = i
            while (j < n && PROMPT.containsMatchIn(lines[j])) j++
            flushText(i)
            out += Segment(Block.Code("shell", trimCode(lines.subList(i, j).map { it.replaceFirst(PROMPT, "") })), raw(i, j))
            i = j
            continue
        }
        val h = HEADING.matchEntire(line)
        if (h != null) {
            flushText(i)
            out += Segment(Block.Heading(h.groupValues[1].length, h.groupValues[2].trimEnd()), raw(i, i + 1))
            i++
            continue
        }
        val indented = indentedCodeEnd(lines, i)
        if (indented > i) {
            val body = lines.subList(i, indented)
            val strip = body.filter { it.isNotBlank() }.minOf { indentWidth(it.takeWhile { c -> c == ' ' || c == '\t' }) }
            flushText(i)
            out += Segment(Block.Code("", trimCode(body.map { stripIndent(it, strip) })), raw(i, indented))
            i = indented
            continue
        }
        if (textStart < 0) textStart = i
        i++
    }
    flushText(n)
    return out
}

/**
 * Where an indented (4+ spaces) code block starting at [i] ends, or [i] when it isn't one. Conservative: it needs a blank line
 * before it, the paragraph before must not be a list (that would be a list continuation), no line in it may look like a
 * list item, and it must end at a blank line or the end of the message (else it is a wrapped paragraph).
 */
private fun indentedCodeEnd(lines: List<String>, i: Int): Int {
    fun isIndented(l: String) = l.isNotBlank() && indentWidth(l.takeWhile { it == ' ' || it == '\t' }) >= 4
    if (!isIndented(lines[i]) || i == 0 || lines[i - 1].isNotBlank()) return i
    // The paragraph before the blank line(s): any list item or indented line in it means we're inside a list.
    var p = i - 1
    while (p >= 0 && lines[p].isBlank()) p--
    while (p >= 0 && lines[p].isNotBlank()) {
        if (LIST_ITEM.matches(lines[p]) || isIndented(lines[p])) return i
        p--
    }
    var j = i
    var lastCode = i
    while (j < lines.size && (lines[j].isBlank() || isIndented(lines[j]))) {
        if (lines[j].isNotBlank()) {
            if (LIST_ITEM.matches(lines[j])) return i
            lastCode = j
        }
        j++
    }
    // Followed straight by a non-indented line: not code.
    if (lastCode + 1 < lines.size && lines[lastCode + 1].isNotBlank()) return i
    return lastCode + 1
}

/** Columns of leading whitespace (a tab counts as 4). */
private fun indentWidth(ws: String) = ws.sumOf { if (it == '\t') 4L else 1L }.toInt()

/** Removes up to [cols] columns of leading whitespace. */
private fun stripIndent(line: String, cols: Int): String {
    var w = 0
    var k = 0
    while (k < line.length && w < cols && (line[k] == ' ' || line[k] == '\t')) { w += if (line[k] == '\t') 4 else 1; k++ }
    return line.substring(k)
}

/** Lines joined, trailing blank lines (and trailing spaces of the last line) dropped. */
private fun trimCode(lines: List<String>): String = lines.dropLastWhile { it.isBlank() }.joinToString("\n").trimEnd()

/** "kotlin", "bash title=x" -> "bash", "{.python}" -> "python". */
private fun fenceLang(info: String): String = info.trim().split(Regex("\\s+")).first().trim('{', '}', '.').lowercase()

/** The header label of a code box: a single short shell line is a "Command"; a named language shows its name. */
fun codeLabel(lang: String, code: String): String {
    val shellish = lang.isEmpty() || lang in SHELLS
    return when {
        shellish && !code.contains('\n') && code.length <= 200 -> "Command"
        lang.isEmpty() -> "Code"
        else -> lang
    }
}

/** "- item" / "* item" lines shown with a bullet (display only). */
internal fun bulletize(text: String): String = text.lines().joinToString("\n") { l ->
    val m = Regex("""^([ \t]*)[-*+] (.*)$""").matchEntire(l)
    if (m != null) m.groupValues[1] + "• " + m.groupValues[2] else l
}

// ---------------------------------------------------------------------------------------------------------------------
// Attached files: the composer adds " [attached: /path/name.png]" to a message. Shown as chips with the file name only.
// ---------------------------------------------------------------------------------------------------------------------

private val ATTACHED = Regex("""[ \t]*\[attached: ([^\]\n]+)\]""")

/** The message text without its attachment tokens, and the attached files' names in order. */
data class Attachments(val text: String, val names: List<String>)

fun splitAttachments(text: String): Attachments {
    val names = ATTACHED.findAll(text).map { m ->
        val path = m.groupValues[1].trim()
        path.trimEnd('/').substringAfterLast('/').ifEmpty { path }
    }.toList()
    if (names.isEmpty()) return Attachments(text, emptyList())
    return Attachments(ATTACHED.replace(text, "").trim(), names)
}

/** Consecutive "$ command" lines (after fences are gone), spoken as "(code)". */
internal val COMMAND_RUN_RE = Regex("""(?m)^[ \t]*\$ .*(?:\n[ \t]*\$ .*)*""")

// ---------------------------------------------------------------------------------------------------------------------
// Rendering
// ---------------------------------------------------------------------------------------------------------------------

/** A message's blocks, one under the other. Prose is selectable and keeps links/bold via [linkify]. */
@Composable
fun MessageBlocks(blocks: List<Block>, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { b ->
            when (b) {
                is Block.Code -> CodeBlock(b.lang, b.code)
                is Block.Heading -> SelectionContainer {
                    Text(
                        linkify(b.text), modifier = Modifier.semantics { heading() }, fontWeight = FontWeight.SemiBold,
                        style = when (b.level) { 1 -> MaterialTheme.typography.titleLarge; 2 -> MaterialTheme.typography.titleMedium; else -> MaterialTheme.typography.titleSmall },
                    )
                }
                is Block.Text -> {
                    val shown = remember(b.text) { bulletize(b.text.trim('\n')) }
                    if (shown.isNotBlank()) SelectionContainer { Text(linkify(shown), style = MaterialTheme.typography.bodyLarge) }
                }
            }
        }
    }
}

/** Code or a command in its own box with a "Copy" button that copies exactly the code. */
@Composable
fun CodeBlock(lang: String, code: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val shape = RoundedCornerShape(14.dp)
    val label = remember(lang, code) { codeLabel(lang, code) }
    Surface(
        shape = shape, color = scheme.surfaceContainerHighest, modifier = modifier.fillMaxWidth(),
        border = if (dark) BorderStroke(1.dp, scheme.outlineVariant) else null,   // a thin edge so the box reads on a dark bubble
    ) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label.uppercase(), modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp, fontWeight = FontWeight.SemiBold),
                    color = scheme.onSurfaceVariant, maxLines = 1,
                )
                CopyButton(code, what = label.lowercase(), tonal = true)
            }
            SelectionContainer {
                Text(
                    code,
                    modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp, top = 2.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp,
                        lineBreak = LineBreak.Simple, hyphens = Hyphens.None,   // wrap anywhere a long path or URL must break; never hyphenate code
                    ),
                    color = scheme.onSurface, softWrap = true,
                )
            }
        }
    }
}

/** A word button "Copy" that becomes "Copied" for 1.5 s; [tonal] = filled tonal pill, else a plain text button. */
@Composable
fun CopyButton(text: String, what: String, tonal: Boolean, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1500); copied = false } }
    val onClick: () -> Unit = {
        scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(what, text)))
            copied = true
        }
    }
    val a11y = Modifier.semantics {
        contentDescription = if (copied) "Copied $what" else "Copy $what"
        if (copied) stateDescription = "Copied"
        liveRegion = LiveRegionMode.Polite
    }
    val label = if (copied) "Copied" else "Copy"
    if (tonal) {
        FilledTonalButton(onClick = onClick, modifier = modifier.then(a11y), contentPadding = PaddingValues(horizontal = 16.dp)) { Text(label, fontSize = 13.sp) }
    } else {
        TextButton(onClick = onClick, modifier = modifier.then(a11y)) { Text(label, fontSize = 13.sp) }
    }
}

/** Attached files as small muted pills ("📎 name.png"). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AttachmentChips(names: List<String>, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        names.forEach { name ->
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Text(
                    "📎 $name", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp).semantics { contentDescription = "Attached file $name" },
                )
            }
        }
    }
}
