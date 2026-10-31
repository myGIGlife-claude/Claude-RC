package life.mygig.clauderc

import life.mygig.clauderc.ui.screens.Block
import life.mygig.clauderc.ui.screens.codeLabel
import life.mygig.clauderc.ui.screens.forSpeech
import life.mygig.clauderc.ui.screens.normalizeNewlines
import life.mygig.clauderc.ui.screens.parseBlocks
import life.mygig.clauderc.ui.screens.parseSegments
import life.mygig.clauderc.ui.screens.splitAttachments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ChatMarkdownTest {
    private fun T(s: String) = Block.Text(s)
    private fun C(lang: String, s: String) = Block.Code(lang, s)

    @Test fun fencedWithLanguage() {
        assertEquals(
            listOf(T("Run this:"), C("bash", "ls -la\ncd /tmp"), T("Done.")),
            parseBlocks("Run this:\n```bash\nls -la\ncd /tmp\n```\nDone."),
        )
    }

    @Test fun fencedWithoutLanguageAndTilde() {
        assertEquals(listOf(C("", "x = 1")), parseBlocks("```\nx = 1\n```"))
        assertEquals(listOf(C("python", "print(1)")), parseBlocks("~~~ python\nprint(1)\n~~~"))
        assertEquals(listOf(C("kotlin", "val a = 1")), parseBlocks("```kotlin title=\"A.kt\"\nval a = 1\n```"))
    }

    @Test fun unclosedFenceIsCodeUntilTheEnd() {
        assertEquals(listOf(T("Here:"), C("python", "print(1)\nprint(2)")), parseBlocks("Here:\n```python\nprint(1)\nprint(2)\n\n"))
        assertEquals(listOf(C("", "")), parseBlocks("```"))
    }

    @Test fun fourBacktickFenceKeepsInnerFences() {
        val md = "````markdown\nUse:\n```bash\nmake\n```\n````\nafter"
        assertEquals(listOf(C("markdown", "Use:\n```bash\nmake\n```"), T("after")), parseBlocks(md))
    }

    @Test fun inlineTripleBackticksStayText() {
        assertEquals(listOf(T("say ```hi``` now")), parseBlocks("say ```hi``` now"))
    }

    @Test fun fenceInsideListItemStripsItsIndent() {
        val md = "1. Install:\n   ```bash\n   npm i\n     --save\n   ```\n2. Run it"
        assertEquals(listOf(T("1. Install:"), C("bash", "npm i\n  --save"), T("2. Run it")), parseBlocks(md))
    }

    @Test fun dollarRunBecomesShellCodeWithoutPrompts() {
        val b = parseBlocks("Then:\n$ cd ~/app\n  $ ./gradlew build\nIt prints OK.")
        assertEquals(listOf(T("Then:"), C("shell", "cd ~/app\n./gradlew build"), T("It prints OK.")), b)
        // Money isn't a prompt.
        assertEquals(listOf(T("It costs \$5 a month")), parseBlocks("It costs \$5 a month"))
    }

    @Test fun indentedCodeAfterBlankLine() {
        assertEquals(
            listOf(T("Put this in the file:\n"), C("", "server {\n  listen 80;\n}"), T("\nThen reload.")),
            parseBlocks("Put this in the file:\n\n    server {\n      listen 80;\n    }\n\nThen reload."),
        )
    }

    @Test fun indentedConservativeCasesStayText() {
        // List continuation.
        val list = "- first item\n\n    more about the first item\n- second"
        assertEquals(listOf(T(list)), parseBlocks(list))
        // No blank line before.
        val noBlank = "para\n    indented"
        assertEquals(listOf(T(noBlank)), parseBlocks(noBlank))
        // Followed straight by a plain line (a wrapped paragraph).
        val wrapped = "para\n\n    indented\nnot indented"
        assertEquals(listOf(T(wrapped)), parseBlocks(wrapped))
        // Indented list items are a nested list, not code.
        val nested = "Steps:\n\n    - one\n    - two"
        assertEquals(listOf(T(nested)), parseBlocks(nested))
    }

    @Test fun headings() {
        assertEquals(
            listOf(Block.Heading(1, "Title"), T("body"), Block.Heading(3, "Small")),
            parseBlocks("# Title\nbody\n### Small"),
        )
        // Four hashes or no space: plain text.
        assertEquals(listOf(T("#### deep\n#tag")), parseBlocks("#### deep\n#tag"))
    }

    @Test fun emptyInput() {
        assertEquals(emptyList<Block>(), parseBlocks(""))
    }

    @Test fun crlfInput() {
        assertEquals(listOf(T("a"), C("sh", "echo hi"), T("b")), parseBlocks("a\r\n```sh\r\necho hi\r\n```\r\nb"))
    }

    @Test fun copyTextHasNoTrailingBlankLines() {
        assertEquals(C("", "a\n\nb"), parseBlocks("```\na\n\nb\n\n\n```").single())
    }

    @Test fun labels() {
        assertEquals("Command", codeLabel("", "ls -la"))
        assertEquals("Command", codeLabel("bash", "sudo systemctl restart caddy"))
        assertEquals("Code", codeLabel("", "a\nb"))
        assertEquals("bash", codeLabel("bash", "a\nb"))
        assertEquals("kotlin", codeLabel("kotlin", "val a = 1"))
    }

    @Test fun attachedTokensBecomeNames() {
        val a = splitAttachments("look at this [attached: /home/u/up/pic one.png] [attached: /tmp/notes.txt]")
        assertEquals("look at this", a.text)
        assertEquals(listOf("pic one.png", "notes.txt"), a.names)
        assertEquals(listOf("x.png"), splitAttachments("[attached: /a/x.png]").names)
        assertEquals("", splitAttachments("[attached: /a/x.png]").text)
        val plain = splitAttachments("no files [here]")
        assertEquals("no files [here]", plain.text)
        assertTrue(plain.names.isEmpty())
    }

    @Test fun speechSkipsCommandRuns() {
        assertEquals("Run:\n (code) \nok", forSpeech("Run:\n$ ls -la\n$ pwd\nok"))
        assertEquals("costs \$10", forSpeech("costs \$10"))
    }

    // ----- property: nothing is lost or reordered -----

    private val pool = listOf(
        "text line", "**bold** and `code`", "- item", "  - sub item", "1. step", "    indented", "      deeper", "", "", "",
        "```", "```kotlin", "````", "~~~", "   ```bash", "$ ls -la", "  $ pwd", "# Head", "## Two", "#### four", "see https://x.y/z",
        "[attached: /a/b.png]", "\tTabbed", "say ```x``` inline",
    )

    private val fenceLine = Regex("^[ \\t]*(`{3,}|~{3,})[^`]*$")

    /** Lines that carry content, as a reader sees them: trimmed, no "$ " prompt, no heading marks, no fence lines or blanks. */
    private fun canon(s: String): List<String> = normalizeNewlines(s).split("\n")
        .filterNot { fenceLine.matches(it) }
        .map { it.trim().removePrefix("$ ").replace(Regex("^#{1,3} +"), "").trim() }
        .filter { it.isNotEmpty() }

    private fun visible(blocks: List<Block>): String = blocks.joinToString("\n") {
        when (it) { is Block.Text -> it.text; is Block.Code -> it.code; is Block.Heading -> it.text }
    }

    @Test fun propertyNothingLost() {
        val rnd = Random(42)
        repeat(3000) {
            val input = List(rnd.nextInt(0, 14)) { pool[rnd.nextInt(pool.size)] }
                .joinToString(if (rnd.nextInt(4) == 0) "\r\n" else "\n")
            val segs = parseSegments(input)
            // Exact: the blocks' source slices rejoin to the input.
            assertEquals("raw rejoin for: $input", normalizeNewlines(input), segs.joinToString("") { s -> s.raw })
            // What is shown, in order, equals the input minus fence markers.
            assertEquals("visible for: $input", canon(input), canon(visible(segs.map { s -> s.block })))
        }
    }
}
