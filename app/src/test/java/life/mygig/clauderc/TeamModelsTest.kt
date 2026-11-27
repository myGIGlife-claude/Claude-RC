package life.mygig.clauderc

import kotlinx.serialization.json.Json
import life.mygig.clauderc.api.Codes
import life.mygig.clauderc.api.ClusterData
import life.mygig.clauderc.api.LoginUrl
import life.mygig.clauderc.api.WORKER_NAME_RE
import life.mygig.clauderc.api.WorkerRunsData
import life.mygig.clauderc.api.WorkersData
import life.mygig.clauderc.ui.vm.accountName
import life.mygig.clauderc.ui.vm.authLabel
import life.mygig.clauderc.ui.vm.choosesSignIn
import life.mygig.clauderc.ui.vm.googleCodeLooksRight
import life.mygig.clauderc.ui.vm.grokSignInStillWaiting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamModelsTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test fun parsesWorkerList() {
        val d = json.decodeFromString<WorkersData>(
            """{"workers":[{"name":"research","role":"Docs & \"quotes\"","mode":"plan","signed_in":true},{"name":"ui"}]}""",
        )
        assertEquals("Docs & \"quotes\"", d.workers[0].role)
        assertTrue(d.workers[0].signedIn)
        assertEquals("acceptEdits", d.workers[1].mode)
        assertFalse(d.workers[1].signedIn)
    }

    @Test fun parsesRuns() {
        val d = json.decodeFromString<WorkerRunsData>(
            """{"runs":[{"id":"aaaa1111","task":"t","status":"done","reply":"hi","error":null,"started":100.5}]}""",
        )
        assertEquals("hi", d.runs[0].reply)
        assertNull(d.runs[0].error)
    }

    @Test fun grokAndGeminiChooseHowToSignIn() {
        for (k in listOf("grok", "gemini")) assertTrue(k, choosesSignIn(k))
        for (k in listOf("claude", "codex", "", "other")) assertFalse(k, choosesSignIn(k))
        assertEquals("your Google account", accountName("gemini"))
        assertEquals("your Grok account", accountName("grok"))
    }

    @Test fun authLabels() {
        assertEquals("Google account", authLabel("gemini", "account"))
        assertEquals("Grok account", authLabel("grok", "account"))
        for (k in listOf("gemini", "grok")) {
            assertEquals("API key", authLabel(k, "key"))
            assertEquals("an older server says nothing", "", authLabel(k, ""))
            assertEquals("", authLabel(k, "other"))
        }
        for (k in listOf("claude", "codex")) for (a in listOf("account", "key", "")) assertEquals("", authLabel(k, a))
    }

    @Test fun clusterAuthIsOptional() {
        val d = json.decodeFromString<ClusterData>("""{"accounts":[{"name":"g","kind":"gemini","signed_in":true,"auth":"account"},{"name":"main"}]}""")
        assertEquals("account", d.accounts[0].auth)
        assertEquals("", d.accounts[1].auth)
    }

    @Test fun parsesGoogleSignInStart() {
        val d = json.decodeFromString<LoginUrl>("""{"url":"https://accounts.google.com/o/oauth2/v2/auth?x=1","session":"worker-login-g","paste_code":true}""")
        assertTrue(d.pasteCode)
        assertNull("no device code: it must not open the Grok dialog", d.code)
        assertFalse(json.decodeFromString<LoginUrl>("""{"url":"https://claude.ai/x"}""").pasteCode)
    }

    @Test fun googleCodeRule() {
        for (ok in listOf("4/0AVGzR1abc_DEF-123", "12345678", "a.b~c=d+e#f/g")) assertTrue(ok, googleCodeLooksRight(ok))
        for (bad in listOf("", "short", "has space 123", "semi;colon123", "x".repeat(513))) assertFalse(bad, googleCodeLooksRight(bad))
    }

    @Test fun parsesGrokSignInStart() {
        val d = json.decodeFromString<LoginUrl>("""{"url":"https://accounts.x.ai/oauth2/device?user_code=QJPW-MG9J","code":"QJPW-MG9J","session":"worker-login-g"}""")
        assertEquals("QJPW-MG9J", d.code)
        assertFalse(d.pasteUrl)
    }

    @Test fun otherSignInStartsHaveNoCode() {
        assertNull(json.decodeFromString<LoginUrl>("""{"url":"https://claude.ai/x","session":"worker-login-c"}""").code)
        val codex = json.decodeFromString<LoginUrl>("""{"url":"https://auth.openai.com/x","paste_url":true,"session":"worker-login-g"}""")
        assertNull(codex.code)
        assertTrue(codex.pasteUrl)
    }

    @Test fun grokSignInKeepsWaitingOnlyWhenItCan() {
        for (c in listOf(Codes.LOGIN_PENDING, Codes.NETWORK, Codes.TIMEOUT)) assertTrue(c, grokSignInStillWaiting(c))
        for (c in listOf("login_failed", Codes.INTERNAL, Codes.AUTH_FAILED, Codes.FORBIDDEN)) assertFalse(c, grokSignInStillWaiting(c))
    }

    @Test fun workerNameRule() {
        for (ok in listOf("research", "UI_2", "a-b")) assertTrue(ok, WORKER_NAME_RE.matches(ok))
        for (bad in listOf("", "../x", "-x", ".x", "a b", "1x", "x".repeat(31))) assertFalse(bad, WORKER_NAME_RE.matches(bad))
    }
}
