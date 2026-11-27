package life.mygig.clauderc

import kotlinx.serialization.json.Json
import life.mygig.clauderc.api.ChatData
import life.mygig.clauderc.api.ChatWorkersData
import life.mygig.clauderc.api.ClusterConfig
import life.mygig.clauderc.api.ClusterData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClusterModelsTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test fun clusterAccountWithUsageAndTokens() {
        val d = json.decodeFromString<ClusterData>(
            """{"accounts":[{"name":"main","email":"a@example.com","plan":"max","signed_in":true,
            "usage":{"five_hour":{"pct":42.5,"resets_at":"2026-01-01T10:00:00Z"},"seven_day":{"pct":null}},
            "tokens":{"five_hour":{"in":10,"out":20,"cached":30}},"future_field":1}]}""",
        )
        val a = d.accounts.single()
        assertEquals("claude", a.kind)
        assertEquals(42.5, a.usage!!.fiveHour.pct!!, 0.0)
        assertNull(a.usage!!.sevenDay.pct)
        assertEquals(20L, a.tokens!!.fiveHour.out)
        assertEquals(0L, a.tokens!!.sevenDay.input)
        assertNull(a.usageError)
    }

    @Test fun clusterAccountDefaults() {
        val a = json.decodeFromString<ClusterData>("""{"accounts":[{"name":"w"}]}""").accounts.single()
        assertFalse(a.signedIn)
        assertEquals("acceptEdits", a.mode)
        assertNull(a.usage)
        assertTrue(json.decodeFromString<ClusterData>("{}").accounts.isEmpty())
    }

    @Test fun clusterConfigDefaultsAndOverrides() {
        assertEquals(ClusterConfig(3, true, 95, "auto"), json.decodeFromString<ClusterConfig>("{}"))
        val c = json.decodeFromString<ClusterConfig>("""{"max_parallel":1,"handback":false,"handback_pct":80,"sandbox":"on"}""")
        assertEquals(ClusterConfig(1, false, 80, "on"), c)
    }

    @Test fun chatWorkersWithTasks() {
        val d = json.decodeFromString<ChatWorkersData>(
            """{"workers":[{"name":"ui","attached":true,"role":"ui"}],"tasks":[{"id":"t1","worker":"ui","status":"done","branch":"b"},{"id":"t2"}]}""",
        )
        assertTrue(d.workers[0].attached)
        assertFalse(d.workers[0].signedIn)
        assertEquals("b", d.tasks[0].branch)
        assertNull(d.tasks[1].branch)
        assertEquals("", d.tasks[1].worker)
    }

    @Test fun chatDataFromAnOlderServerIsFull() {
        val d = json.decodeFromString<ChatData>("""{"session":"s","messages":[{"id":"1","role":"user","text":"hi"}]}""")
        assertTrue(d.changed)
        assertFalse(d.retry)
        assertEquals("default", d.mode)
        assertEquals("", d.token)
        assertEquals("user", d.messages.single().role)
        assertTrue(d.messages.single().files.isEmpty())
    }

    @Test fun chatDataUnchangedAnswer() {
        val d = json.decodeFromString<ChatData>("""{"changed":false,"token":"abc"}""")
        assertFalse(d.changed)
        assertEquals("abc", d.token)
        assertTrue(d.messages.isEmpty())
        assertNull(d.ask)
    }
}
