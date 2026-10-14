package life.mygig.clauderc

import life.mygig.clauderc.voice.firstPieces
import life.mygig.clauderc.voice.speechText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTextTest {
    @Test fun stripsMarkdown() {
        assertEquals("Done: the build passed.", speechText("**Done**: the `build` passed."))
        assertEquals("one\ntwo", speechText("- one\n- two"))
        assertEquals("see the docs for details", speechText("see [the docs](https://example.com/x) for details"))
    }

    @Test fun dropsCodeBlocksUrlsAndEmoji() {
        assertEquals("before\nafter", speechText("before\n```\nval x = 1\n```\nafter"))
        assertEquals("open a link now", speechText("open https://example.com/a?b=1 now"))
        assertEquals("Hiya David", speechText("Hiya David 🃏"))
        assertEquals("", speechText("---\n***"))
    }

    @Test fun respellsWords() {
        assertEquals("Okay pudding, ready", speechText("Okay puddin', ready"))
        assertEquals("Not pudding twice", speechText("Not pudding twice"))
        assertEquals("Claude R C is up", speechText("cLaudeRC is up"))
    }

    @Test fun firstSentenceStartsShort() {
        assertEquals(listOf("Short one."), firstPieces("Short one."))
        val long = "Alright puddin, the build passed and I merged it, so now you can update the app and try the new voice again."
        val p = firstPieces(long)
        assertEquals(2, p.size)
        assertTrue(p[0].length <= 90 && p[0].endsWith(","))
        assertEquals(long.length, (p[0] + " " + p[1]).length)
    }
}
