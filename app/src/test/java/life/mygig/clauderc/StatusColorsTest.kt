package life.mygig.clauderc

import androidx.compose.ui.graphics.Color
import life.mygig.clauderc.ui.theme.DarkStatusColors
import life.mygig.clauderc.ui.theme.LightStatusColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class StatusColorsTest {
    // The surfaces from Theme.kt that this text and these dots sit on.
    private val lightBackground = Color(0xFFFAF9F5)
    private val lightLow = Color(0xFFF4F2EC)
    private val lightHigh = Color(0xFFE8E5DD)
    private val lightHighest = Color(0xFFE1DED5)
    private val darkBackground = Color(0xFF0E0E10)
    private val darkLow = Color(0xFF16171B)
    private val darkHighest = Color(0xFF26272D)

    private fun channel(c: Float): Double =
        if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun luminance(c: Color): Double =
        0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    /** WCAG 2 contrast ratio. */
    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun assertContrast(name: String, needed: Double, a: Color, b: Color) {
        val ratio = contrast(a, b)
        assertTrue("$name is $ratio:1, needs $needed:1", ratio >= needed)
    }

    @Test
    fun theHelperGivesTheKnownRatios() {
        assertEquals(21.0, contrast(Color.Black, Color.White), 0.001)
        assertEquals(1.0, contrast(lightBackground, lightBackground), 0.001)
    }

    @Test
    fun lightText() {
        val s = LightStatusColors
        assertContrast("on warn", 7.0, s.onWarnContainer, s.warnContainer)
        assertContrast("on error", 7.0, s.onErrorContainer, s.errorContainer)
        assertContrast("warn text on its card", 4.5, s.warnText, s.warnContainer)
        assertContrast("error text on its card", 4.5, s.errorText, s.errorContainer)
        for ((name, surface) in listOf("background" to lightBackground, "low" to lightLow, "dialog" to lightHigh)) {
            assertContrast("ok text on $name", 4.5, s.okText, surface)
            assertContrast("warn text on $name", 4.5, s.warnText, surface)
            assertContrast("bad text on $name", 4.5, s.badText, surface)
            assertContrast("error text on $name", 4.5, s.errorText, surface)
        }
        assertContrast("installed on the sheet's rows", 4.5, s.installedText, lightHighest)
    }

    @Test
    fun lightDotsAndBorders() {
        val s = LightStatusColors
        for ((name, surface) in listOf("background" to lightBackground, "low" to lightLow)) {
            assertContrast("ok dot on $name", 3.0, s.okDot, surface)
            assertContrast("warn dot on $name", 3.0, s.warnDot, surface)
            assertContrast("bad dot on $name", 3.0, s.badDot, surface)
            assertContrast("off dot on $name", 3.0, s.offDot, surface)
        }
        assertContrast("warn dot on its card", 3.0, s.warnDot, s.warnContainer)
        // Decorative, so no WCAG minimum: only "clearly visible on the page".
        assertContrast("warn border", 1.5, s.warnBorder, lightBackground)
        assertContrast("error border", 1.5, s.errorBorder, lightBackground)
    }

    @Test
    fun darkPairs() {
        val s = DarkStatusColors
        assertContrast("on warn", 7.0, s.onWarnContainer, s.warnContainer)
        assertContrast("on error", 7.0, s.onErrorContainer, s.errorContainer)
        assertContrast("warn text on its card", 4.5, s.warnText, s.warnContainer)
        assertContrast("error text on its card", 4.5, s.errorText, s.errorContainer)
        assertContrast("installed on the sheet's rows", 4.5, s.installedText, darkHighest)
        for ((name, surface) in listOf("background" to darkBackground, "low" to darkLow)) {
            assertContrast("ok text on $name", 4.5, s.okText, surface)
            assertContrast("warn text on $name", 4.5, s.warnText, surface)
            assertContrast("bad text on $name", 4.5, s.badText, surface)
            assertContrast("error text on $name", 4.5, s.errorText, surface)
            assertContrast("ok dot on $name", 3.0, s.okDot, surface)
            assertContrast("warn dot on $name", 3.0, s.warnDot, surface)
            assertContrast("bad dot on $name", 3.0, s.badDot, surface)
            assertContrast("off dot on $name", 3.0, s.offDot, surface)
        }
        assertContrast("warn dot on its card", 3.0, s.warnDot, s.warnContainer)
    }

    /** The dark theme must stay exactly as it was before the tokens existed. */
    @Test
    fun darkValuesAreTheOldConstants() {
        val s = DarkStatusColors
        assertEquals(Color(0xFF2A2418), s.warnContainer)
        assertEquals(Color(0xFF5C4A24), s.warnBorder)
        assertEquals(Color(0xFF2A1718), s.errorContainer)
        assertEquals(Color(0xFF6A2A2D), s.errorBorder)
        assertEquals(Color(0xFFF1EFE9), s.onWarnContainer) // the dark onBackground/onSurface these cards inherited
        assertEquals(Color(0xFFF1EFE9), s.onErrorContainer)
        assertEquals(Color(0xFF4CC38A), s.okText)
        assertEquals(Color(0xFF7FD4A8), s.installedText)
        assertEquals(Color(0xFFF2C46D), s.warnText)
        assertEquals(Color(0xFFE5484D), s.badText)
        assertEquals(Color(0xFFF09A9D), s.errorText)
        assertEquals(Color(0xFF4CC38A), s.okDot)
        assertEquals(Color(0xFFF2C46D), s.warnDot)
        assertEquals(Color(0xFFE5484D), s.badDot)
        assertEquals(Color(0xFF6B6862), s.offDot)
    }
}
