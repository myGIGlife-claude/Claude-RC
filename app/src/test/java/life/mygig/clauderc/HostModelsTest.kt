package life.mygig.clauderc

import kotlinx.serialization.json.Json
import life.mygig.clauderc.api.ChatHostsData
import life.mygig.clauderc.api.HOST_FINGERPRINT_RE
import life.mygig.clauderc.api.HOST_NAME_RE
import life.mygig.clauderc.api.HOST_USER_RE
import life.mygig.clauderc.api.HostProbe
import life.mygig.clauderc.api.HostsData
import life.mygig.clauderc.api.hostAddressOk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostModelsTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test fun parsesHostList() {
        val d = json.decodeFromString<HostsData>(
            """{"hosts":[{"name":"shop","address":"example.com","port":2222,"user":"deploy","auth":"password","fingerprint":"SHA256:x","added":1700000000},{"name":"blog"}]}""",
        )
        assertEquals(2222, d.hosts[0].port)
        assertEquals("password", d.hosts[0].auth)
        assertEquals(22, d.hosts[1].port)
        assertEquals("key", d.hosts[1].auth)
    }

    @Test fun parsesProbeAndSession() {
        val p = json.decodeFromString<HostProbe>("""{"fingerprint":"SHA256:abc","keytype":"ED25519"}""")
        assertEquals("ED25519", p.keytype)
        val s = json.decodeFromString<ChatHostsData>("""{"hosts":[{"name":"shop","address":"example.com","user":"deploy","attached":true},{"name":"blog"}]}""")
        assertTrue(s.hosts[0].attached)
        assertFalse(s.hosts[1].attached)
    }

    @Test fun hostRules() {
        for (ok in listOf("shop", "a", "my-site-2")) assertTrue(ok, HOST_NAME_RE.matches(ok))
        for (bad in listOf("", "Shop", "-x", "1x", "a_b", "../x", "x".repeat(31))) assertFalse(bad, HOST_NAME_RE.matches(bad))
        for (ok in listOf("example.com", "192.0.2.10", "2001:db8::1", "a")) assertTrue(ok, hostAddressOk(ok))
        for (bad in listOf("", "-x.com", "a b", "x;y", "host.")) assertFalse(bad, hostAddressOk(bad))
        for (ok in listOf("deploy", "_x", "www-data")) assertTrue(ok, HOST_USER_RE.matches(ok))
        for (bad in listOf("", "Root", "-x", "a b")) assertFalse(bad, HOST_USER_RE.matches(bad))
        assertTrue(HOST_FINGERPRINT_RE.matches("SHA256:" + "A".repeat(43)))
        assertFalse(HOST_FINGERPRINT_RE.matches("SHA256:" + "A".repeat(42)))
    }
}
