package life.mygig.clauderc

import life.mygig.clauderc.ssh.Ed25519Identity
import life.mygig.clauderc.ssh.ServerConfig
import life.mygig.clauderc.ssh.SshConnectionKey
import life.mygig.clauderc.ssh.mayRetryOnFreshConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SshConnectionPoolTest {
    private val cfg = ServerConfig("example.test", 22, "me", "ssh-ed25519", "AAAAC3NzaC1lZDI1NTE5AAAAIA==")
    private fun seed(b: Int) = ByteArray(32) { b.toByte() }

    @Test fun sameServerAndKeyShareOneConnection() {
        // Two identity objects from the same seed: different ByteArray instances, same key.
        val a = SshConnectionKey.of(cfg, Ed25519Identity(seed(1)))
        val b = SshConnectionKey.of(cfg.copy(), Ed25519Identity(seed(1)))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test fun anyChangeMeansAnotherConnection() {
        val id = Ed25519Identity(seed(1))
        val base = SshConnectionKey.of(cfg, id)
        assertNotEquals(base, SshConnectionKey.of(cfg.copy(host = "other.test"), id))
        assertNotEquals(base, SshConnectionKey.of(cfg.copy(port = 2222), id))
        assertNotEquals(base, SshConnectionKey.of(cfg.copy(user = "you"), id))
        assertNotEquals(base, SshConnectionKey.of(cfg.copy(hostKeyType = "ssh-rsa"), id))
        assertNotEquals(base, SshConnectionKey.of(cfg.copy(hostKeyBlob = "AAAA"), id))
        assertNotEquals(base, SshConnectionKey.of(cfg, Ed25519Identity(seed(2))))
    }

    @Test fun retriesOnlyChannelOpenFailuresOnAReusedConnectionOnce() {
        assertTrue(mayRetryOnFreshConnection(reused = true, attempt = 0, message = "session is down"))
        assertTrue(mayRetryOnFreshConnection(reused = true, attempt = 0, message = "channel is not opened."))
        // A fresh connection that can't open a channel is a real error, not a stale one.
        assertFalse(mayRetryOnFreshConnection(reused = false, attempt = 0, message = "session is down"))
        // Once only.
        assertFalse(mayRetryOnFreshConnection(reused = true, attempt = 1, message = "session is down"))
        // The exec request may have reached the server: never run an action twice.
        assertFalse(mayRetryOnFreshConnection(reused = true, attempt = 0, message = "channel request: timeout"))
        assertFalse(mayRetryOnFreshConnection(reused = true, attempt = 0, message = "failed to send channel request"))
        assertFalse(mayRetryOnFreshConnection(reused = true, attempt = 0, message = null))
    }
}
