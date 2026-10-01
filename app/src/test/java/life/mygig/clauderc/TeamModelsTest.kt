package life.mygig.clauderc

import kotlinx.serialization.json.Json
import life.mygig.clauderc.api.WORKER_NAME_RE
import life.mygig.clauderc.api.WorkerRunsData
import life.mygig.clauderc.api.WorkersData
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

    @Test fun workerNameRule() {
        for (ok in listOf("research", "UI_2", "a-b")) assertTrue(ok, WORKER_NAME_RE.matches(ok))
        for (bad in listOf("", "../x", "-x", ".x", "a b", "1x", "x".repeat(31))) assertFalse(bad, WORKER_NAME_RE.matches(bad))
    }
}
