package life.mygig.clauderc

import life.mygig.clauderc.ui.LayoutKind
import life.mygig.clauderc.ui.layoutKind
import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveTest {
    @Test
    fun breakpoints() {
        assertEquals(LayoutKind.COMPACT, layoutKind(0))
        assertEquals(LayoutKind.COMPACT, layoutKind(360))
        assertEquals(LayoutKind.COMPACT, layoutKind(599))
        assertEquals(LayoutKind.MEDIUM, layoutKind(600))
        assertEquals(LayoutKind.MEDIUM, layoutKind(839))
        assertEquals(LayoutKind.EXPANDED, layoutKind(840))
        assertEquals(LayoutKind.EXPANDED, layoutKind(1600))
    }
}
