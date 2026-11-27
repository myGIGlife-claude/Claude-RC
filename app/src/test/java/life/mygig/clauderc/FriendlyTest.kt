package life.mygig.clauderc

import androidx.compose.ui.text.TextStyle
import life.mygig.clauderc.api.ApiException
import life.mygig.clauderc.api.Codes
import life.mygig.clauderc.ui.Fix
import life.mygig.clauderc.ui.LoginKind
import life.mygig.clauderc.ui.Tab
import life.mygig.clauderc.ui.components.tabular
import life.mygig.clauderc.ui.friendly
import life.mygig.clauderc.ui.plural
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FriendlyTest {
    private val allCodes = listOf(
        Codes.NOT_LOGGED_IN_CLAUDE, Codes.NOT_LOGGED_IN_GITHUB, Codes.NOT_LOGGED_IN_AWS, Codes.MISSING_SCOPES,
        Codes.REPO_EXISTS, Codes.FOLDER_DIRTY, Codes.INVALID_NAME, Codes.BUSY, Codes.FORBIDDEN,
        Codes.HOST_KEY_CHANGED, Codes.AUTH_FAILED, Codes.NOT_CONFIGURED, Codes.KEY_ERROR, Codes.NETWORK, Codes.TIMEOUT,
    )

    @Test fun `sign in is the only word on the buttons`() {
        for (code in listOf(Codes.NOT_LOGGED_IN_CLAUDE, Codes.NOT_LOGGED_IN_GITHUB, Codes.NOT_LOGGED_IN_AWS)) {
            assertEquals("Sign in", friendly(ApiException(code, "x")).second)
        }
        assertEquals(Fix.Login(LoginKind.AWS), friendly(ApiException(Codes.NOT_LOGGED_IN_AWS, "x")).third)
    }

    @Test fun `forbidden points at the update and opens Connections`() {
        val (text, label, fix) = friendly(ApiException(Codes.FORBIDDEN, "x"))
        assertEquals("The server refused this. Update the server scripts: Connections › Update now.", text)
        assertEquals("Open Connections", label)
        assertEquals(Fix.GoTo(Tab.STATUS), fix)
    }

    @Test fun `an unknown phone key keeps the Settings button`() {
        val (text, label, fix) = friendly(ApiException(Codes.AUTH_FAILED, "x"))
        assertEquals("The server doesn't know this phone yet. Settings › Servers › Connect with a password sets it up.", text)
        assertEquals("Settings", label)
        assertEquals(Fix.OpenSettings, fix)
    }

    @Test fun `messages use phone words`() {
        for (code in allCodes) {
            val (text, label, _) = friendly(ApiException(code, "x"))
            val shown = "$text ${label.orEmpty()}"
            for (word in listOf("log in", "login", "logged", ".sh", "claude-launcher", "create/open", "!")) {
                assertFalse("$code says \"$word\": $shown", shown.contains(word, ignoreCase = true))
            }
        }
    }

    @Test fun `plural has one rule`() {
        assertEquals("0 plugins", plural(0, "plugin", "plugins"))
        assertEquals("1 plugin", plural(1, "plugin", "plugins"))
        assertEquals("2 plugins", plural(2, "plugin", "plugins"))
        assertEquals("1 try", plural(1L, "try", "tries"))
        assertEquals("3 tries", plural(3L, "try", "tries"))
    }

    @Test fun `tabular asks for equal width digits`() {
        assertEquals("tnum", TextStyle().tabular().fontFeatureSettings)
    }
}
