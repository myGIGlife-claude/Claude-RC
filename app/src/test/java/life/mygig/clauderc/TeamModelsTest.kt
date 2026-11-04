package life.mygig.clauderc

import kotlinx.serialization.json.Json
import life.mygig.clauderc.api.Codes
import life.mygig.clauderc.api.LoginUrl
import life.mygig.clauderc.api.WORKER_NAME_RE
import life.mygig.clauderc.api.WorkerRunsData
import life.mygig.clauderc.api.WorkersData
import life.mygig.clauderc.ui.vm.choosesSignIn
import life.mygig.clauderc.ui.vm.grokSignInStillWaiting
import life.mygig.clauderc.ui.vm.usesApiKey
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

    @Test fun apiKeyKinds() {
        assertTrue(usesApiKey("gemini"))
        for (k in listOf("grok", "claude", "codex", "", "other")) assertFalse(k, usesApiKey(k))
    }

    @Test fun onlyGrokChoosesHowToSignIn() {
        assertTrue(choosesSignIn("grok"))
        for (k in listOf("gemini", "claude", "codex", "", "other")) assertFalse(k, choosesSignIn(k))
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
