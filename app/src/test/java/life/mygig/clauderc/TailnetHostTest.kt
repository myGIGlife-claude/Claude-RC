package life.mygig.clauderc

import life.mygig.clauderc.data.AppBackupPayload
import life.mygig.clauderc.data.Server
import life.mygig.clauderc.data.TAILNET_HOST_MAX
import life.mygig.clauderc.data.appBackupJson
import life.mygig.clauderc.data.cleanTailnetHost
import life.mygig.clauderc.data.isValidTailnetHost
import life.mygig.clauderc.data.settingsJson
import life.mygig.clauderc.ssh.Ed25519Identity
import life.mygig.clauderc.ssh.ServerConfig
import life.mygig.clauderc.ssh.SshConnectionKey
import life.mygig.clauderc.ssh.addressesToTry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TailnetHostTest {
    private val cfg = ServerConfig("h.example", 22, "u", "ssh-ed25519", "AAAAC3NzaC1lZDI1NTE5AAAAIA==")
    private val alt = "100.64.0.1"

    // ---- JSON: saved list and app backup ---------------------------------------

    @Test fun oldSavedServersLoadWithoutTailnetHost() {
        val old = """[{"id":"a","host":"h.example","port":22,"user":"u","hostKeyBlob":"b","purpose":"Backup"}]"""
        val s = settingsJson.decodeFromString<List<Server>>(old).single()
        assertEquals("", s.tailnetHost)
        assertEquals("", s.toServerConfig().tailnetHost)
        assertEquals("Backup", s.purpose)
    }

    @Test fun tailnetHostRoundTrips() {
        val s = Server("a", "h.example", 22, "u", "ssh-ed25519", "b", "fp", tailnetHost = alt)
        val text = settingsJson.encodeToString(listOf(s))
        assertEquals(listOf(s), settingsJson.decodeFromString<List<Server>>(text))
        assertEquals(alt, s.toServerConfig().tailnetHost)
        // Empty isn't written, so an older app sees the same list as before.
        assertFalse(settingsJson.encodeToString(listOf(s.copy(tailnetHost = ""))).contains("tailnetHost"))
    }

    @Test fun olderAppSkipsTheField() {
        // An older app's Server has no tailnetHost: ignoreUnknownKeys must keep it loading. Simulated with a future key.
        val newer = """[{"id":"a","host":"h","user":"u","tailnetHost":"100.64.0.1","someFutureField":1}]"""
        assertEquals(alt, settingsJson.decodeFromString<List<Server>>(newer).single().tailnetHost)
    }

    @Test fun oldBackupImportsAndNewOneCarriesIt() {
        val old = """{"servers":[{"id":"a","host":"h","port":22,"user":"u","hostKeyType":"","hostKeyBlob":"b",""" +
            """"hostKeyFingerprint":"","previousFingerprint":"","inCluster":null,"purpose":""}],"activeId":"a",""" +
            """"appLock":false,"notify":false,"theme":"SYSTEM","sshSeed":"AA=="}"""
        assertEquals("", appBackupJson.decodeFromString<AppBackupPayload>(old).servers.single().tailnetHost)

        val p = AppBackupPayload(listOf(Server("a", "h", tailnetHost = "srv.tail0000.ts.net")), "a", false, false, "SYSTEM", "AA==")
        val back = appBackupJson.decodeFromString<AppBackupPayload>(appBackupJson.encodeToString(p))
        assertEquals("srv.tail0000.ts.net", back.servers.single().tailnetHost)
    }

    @Test fun anInvalidStoredValueIsNeverDialled() {
        assertEquals("", Server("a", "h", tailnetHost = "bad host").toServerConfig().tailnetHost)
        assertEquals(alt, Server("a", "h", tailnetHost = " $alt ").toServerConfig().tailnetHost)
    }

    // ---- Validator --------------------------------------------------------------

    @Test fun acceptsEmptyAndAddresses() {
        listOf(
            "", "   ", alt, " $alt\t", "10.0.0.255", "localhost", "srv", "srv.tail0000.ts.net", "a-b.example.com",
            "fd7a:115c:a1e0::1", "fd7a:115c:a1e0:ab12:4843:cd96:6258:1", "::1", "::", "::ffff:100.64.0.1",
            "1:2:3:4:5:6:7:8", "FD7A::AB",
        ).forEach { assertTrue(it, isValidTailnetHost(it)) }
    }

    @Test fun rejectsWhatIsNotAnAddress() {
        listOf(
            "a b", "a\nb", "a\u0000b", "100.64.0", "999.1.1.1", "1.2.3.4.5", "-srv", "srv-", "srv..net", ".srv", "srv.",
            "a_b.example", "user@host", "host:22", "[::1]", "fe80::1%wlan0", "1::2::3", ":::", "1:2:3:4:5:6:7",
            "1:2:3:4:5:6:7:8:9", "12345::1", "::ffff:300.1.1.1", "http://h", "h/x", "x".repeat(64),
            "a.".repeat(127) + "a",   // 255 chars
        ).forEach { assertFalse(it, isValidTailnetHost(it)) }
    }

    @Test fun lengthLimitIs253() {
        val label = "a".repeat(63)
        val ok = listOf(label, label, label, "a".repeat(61)).joinToString(".")
        assertEquals(TAILNET_HOST_MAX, ok.length)
        assertTrue(isValidTailnetHost(ok))
        assertFalse(isValidTailnetHost(ok + "a"))
    }

    @Test fun cleanTrimsOrClears() {
        assertEquals(alt, cleanTailnetHost("  $alt "))
        assertEquals("", cleanTailnetHost("not valid"))
        assertEquals("", cleanTailnetHost(""))
    }

    // ---- Which address is dialled -------------------------------------------------

    @Test fun noTailnetHostMeansTheNormalHostOnly() {
        assertEquals(listOf("h.example"), addressesToTry(cfg, null))
        assertEquals(listOf("h.example"), addressesToTry(cfg.copy(tailnetHost = "  "), "h.example"))
        // Same as the normal host: dial it once.
        assertEquals(listOf("h.example"), addressesToTry(cfg.copy(tailnetHost = "H.example"), null))
    }

    @Test fun tailnetFirstThenTheNormalHost() {
        val c = cfg.copy(tailnetHost = alt)
        assertEquals(listOf(alt, "h.example"), addressesToTry(c, null))
        assertEquals(listOf(alt, "h.example"), addressesToTry(c, alt))
        // A stale memory from another address list is ignored.
        assertEquals(listOf(alt, "h.example"), addressesToTry(c, "100.64.0.2"))
    }

    @Test fun rememberedNormalHostGoesStraightThere() {
        assertEquals(listOf("h.example"), addressesToTry(cfg.copy(tailnetHost = alt), "h.example"))
    }

    // ---- Pool key ---------------------------------------------------------------

    @Test fun poolKeyStillOneServer() {
        val id = Ed25519Identity(ByteArray(32) { 1 })
        val withAlt = cfg.copy(tailnetHost = alt)
        assertEquals(SshConnectionKey.of(withAlt, id), SshConnectionKey.of(withAlt.copy(), id))
        // Editing the address starts a fresh connection; the pinned key and the rest stay the same.
        assertNotEquals(SshConnectionKey.of(cfg, id), SshConnectionKey.of(withAlt, id))
    }
}
