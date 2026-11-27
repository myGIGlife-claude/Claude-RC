package life.mygig.clauderc

import kotlinx.serialization.json.Json
import life.mygig.clauderc.api.COPY_FILE_RE
import life.mygig.clauderc.api.CopyEntry
import life.mygig.clauderc.api.CopyKey
import life.mygig.clauderc.api.CopyList
import life.mygig.clauderc.api.CopyProbe
import life.mygig.clauderc.api.CopyRoute
import life.mygig.clauderc.api.CopyStatus
import life.mygig.clauderc.api.chooseCopyRoute
import life.mygig.clauderc.api.copyApplyLines
import life.mygig.clauderc.api.copyPathOk
import life.mygig.clauderc.api.defaultCopyTicks
import life.mygig.clauderc.api.diffCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CopyTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private fun e(path: String, mtime: Long = 100, size: Long = 5, optional: Boolean = false) = CopyEntry(path, size, mtime, "644", optional)

    @Test fun diffSortsIntoThreeLists() {
        val from = listOf(e("only-a"), e("same"), e("a-newer", 200), e("b-newer", 100), e("same-time-other-size", 100, 9))
        val to = listOf(e("same"), e("a-newer", 100), e("b-newer", 200), e("same-time-other-size", 100, 5), e("only-b"))
        val d = diffCopy(from, to)
        assertEquals(listOf("only-a"), d.missingOnTo.map { it.path })
        assertEquals(listOf("a-newer"), d.newerOnFrom.map { it.path })
        assertEquals(listOf("b-newer", "same-time-other-size"), d.newerOnTo.map { it.path })
        assertFalse(d.all.any { it.path == "same" || it.path == "only-b" })
    }

    @Test fun identicalListsAreEmpty() {
        assertTrue(diffCopy(listOf(e("x")), listOf(e("x"))).isEmpty)
        assertTrue(diffCopy(emptyList(), listOf(e("x"))).isEmpty)
    }

    @Test fun defaultTicksLeaveNewerOnTargetAndOptionalUnticked() {
        val d = diffCopy(
            listOf(e("m"), e("n", 200), e("k.keystore", optional = true), e("t", 100)),
            listOf(e("n", 100), e("t", 300)),
        )
        assertEquals(setOf("m", "n"), defaultCopyTicks(d))
    }

    @Test fun applyLinesPolicyAndOverwriteList() {
        val d = diffCopy(listOf(e("m"), e("n", 200), e("t", 100)), listOf(e("n", 100), e("t", 300)))
        assertEquals(listOf("newer"), copyApplyLines(d, defaultCopyTicks(d)))
        assertEquals(listOf("newer", "t"), copyApplyLines(d, defaultCopyTicks(d) + "t"))
        assertEquals(listOf("new-only"), copyApplyLines(d, setOf("m")))
        assertEquals(listOf("new-only", "t"), copyApplyLines(d, setOf("m", "t")))
    }

    @Test fun routeTailnetWhenReachableWithRightKey() {
        val ok = CopyProbe(reachable = true, hostkeyOk = true)
        assertEquals(CopyRoute.Tailnet("100.64.0.2"), chooseCopyRoute(ok, null, "100.64.0.2"))
    }

    @Test fun routeFallsBackToPublicWhenTailnetUnreachableOrNotSet() {
        val down = CopyProbe(reachable = false)
        val ok = CopyProbe(reachable = true, hostkeyOk = true)
        assertEquals(CopyRoute.Public, chooseCopyRoute(down, ok, "100.64.0.2"))
        assertEquals(CopyRoute.Public, chooseCopyRoute(null, ok, ""))
    }

    @Test fun wrongHostKeyStopsWithNoFallback() {
        val bad = CopyProbe(reachable = true, hostkeyOk = false)
        val ok = CopyProbe(reachable = true, hostkeyOk = true)
        assertTrue(chooseCopyRoute(bad, ok, "100.64.0.2") is CopyRoute.Stop)
        assertTrue(chooseCopyRoute(null, bad, "") is CopyRoute.Stop)
    }

    @Test fun nothingReachableStops() {
        val down = CopyProbe()
        assertTrue(chooseCopyRoute(down, down, "100.64.0.2") is CopyRoute.Stop)
        assertTrue(chooseCopyRoute(null, null, "") is CopyRoute.Stop)
    }

    @Test fun pathsWithLineBreaksCannotTravel() {
        assertTrue(copyPathOk("src/a b.kt"))
        assertFalse(copyPathOk("a\nb"))
        assertFalse(copyPathOk("a\rb"))
        assertFalse(copyPathOk(""))
    }

    @Test fun copyFileNames() {
        assertTrue(COPY_FILE_RE.matches("copy-20261127-101500.tgz"))
        assertTrue(COPY_FILE_RE.matches("copy-20261127-101500-7.tgz"))
        assertFalse(COPY_FILE_RE.matches("copy-1.tgz"))
        assertFalse(COPY_FILE_RE.matches("../copy-20261127-101500.tgz"))
        assertFalse(COPY_FILE_RE.matches("copy-20261127-101500.tgz; rm"))
    }

    @Test fun parsesProjectListAnswers() {
        val l = json.decodeFromString<CopyList>(
            """{"entries":[{"path":"a/b.txt","size":3,"mtime":9,"mode":"644"},{"path":".env","size":12,"mtime":10,"mode":"600"}],"truncated":false,"exists":true}""",
        )
        assertEquals(listOf("644", "600"), l.entries.map { it.mode })
        assertEquals(true, l.exists)
        val missing = json.decodeFromString<CopyList>("""{"entries":[],"truncated":false,"exists":false}""")
        assertEquals(false, missing.exists)
        // secrets lists have no `exists`
        assertEquals(null, json.decodeFromString<CopyList>("""{"entries":[],"truncated":true}""").exists)
    }

    @Test fun parsesSecretsAndBundleLists() {
        val s = json.decodeFromString<CopyList>(
            """{"entries":[{"path":"env/FOO_KEY","size":0,"mtime":5,"mode":"600"},{"path":"keystores/a.jks","size":2048,"mtime":6,"mode":"600","optional":true}],"truncated":false}""",
        )
        assertFalse(s.entries[0].optional)
        assertTrue(s.entries[1].optional)
        val sha = "0123456789abcdef0123456789abcdef01234567"
        val b = json.decodeFromString<CopyList>("""{"entries":[{"path":"feature/x","size":3,"mtime":1700000000,"mode":"$sha"}],"truncated":false,"exists":true}""")
        assertEquals(sha, b.entries[0].mode)
        assertEquals(3L, b.entries[0].size)
    }

    @Test fun parsesAnswers() {
        val k = json.decodeFromString<CopyKey>("""{"public_key":"ssh-ed25519 AAAA clauderc-copy","tailnet_ip":""}""")
        assertEquals("", k.tailnetIp)
        val p = json.decodeFromString<CopyProbe>("""{"reachable":true,"hostkey_ok":false}""")
        assertTrue(p.reachable)
        assertFalse(p.hostkeyOk)
    }

    @Test fun parsesStatusAndResult() {
        val sending = json.decodeFromString<CopyStatus>(
            """{"job":"send","phase":"transfer","message":"Sending to the other server","pct":60,"bytes":0,"file":"","started":1,"updated":2,"error":""}""",
        )
        assertEquals("transfer", sending.phase)
        assertEquals(null, sending.result)
        val done = json.decodeFromString<CopyStatus>(
            """{"job":"apply","phase":"done","message":"Copied","pct":100,"bytes":0,"file":"","started":1,"updated":2,"error":"",
              "result":{"copied":["a.txt"],"skipped":[{"path":"b.txt","reason":"exists"}],"refused":[{"path":"env/PATH","reason":"this name can't be copied"}],
              "env_added":["NEW_KEY"],"env_conflicts":["OLD_KEY"],"fetched":["feature/x"],"backup":"/home/u/backups/pre-copy-20261127-101500","key_removed":true}}""",
        )
        val r = done.result!!
        assertEquals(listOf("a.txt"), r.copied)
        assertEquals("exists", r.skipped.single().reason)
        assertEquals("env/PATH", r.refused.single().path)
        assertEquals(listOf("NEW_KEY"), r.envAdded)
        assertEquals(listOf("OLD_KEY"), r.envConflicts)
        assertEquals(listOf("feature/x"), r.fetched)
        assertTrue(r.backup.endsWith("pre-copy-20261127-101500"))
        assertTrue(r.keyRemoved)
        assertFalse(json.decodeFromString<CopyStatus>("""{"job":"none","phase":"none","message":"","pct":0,"bytes":0}""").result != null)
    }
}
