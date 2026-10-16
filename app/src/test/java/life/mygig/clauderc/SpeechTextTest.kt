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

    @Test fun saysMoneyAsWords() {
        assertEquals("10 dollars", speechText("$10"))
        assertEquals("1 dollar", speechText("$1"))
        assertEquals("10 dollars and 50 cents", speechText("$10.50"))
        assertEquals("99 cents", speechText("$0.99"))
        assertEquals("10 dollars and 5 cents", speechText("$10.05"))
        assertEquals("3 dollars and 50 cents", speechText("$3.5"))
        assertEquals("1250 dollars", speechText("$1,250"))
        assertEquals("1.5 million dollars", speechText("$1.5M"))
        assertEquals("5 thousand dollars", speechText("$5k"))
        assertEquals("20 dollars per month", speechText("$20/month"))
        assertEquals("3 euros", speechText("€3.00"))
        assertEquals("1 pound", speechText("£1"))
        assertEquals("costs 10 dollars, then 20 dollars.", speechText("costs $10, then $20."))
        assertEquals("a 5 build", speechText("a 5 build"))
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
