package life.mygig.clauderc

import java.security.SecureRandom
import java.util.Base64
import life.mygig.clauderc.ssh.Ed25519Identity
import life.mygig.clauderc.ssh.ServerConfig
import life.mygig.clauderc.ssh.SshFailure
import life.mygig.clauderc.ssh.SshRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Runs the app's real SSH code against a real sshd whose authorized_keys pins
 * the key to claude-launcher-api. Driven by server/tests/test-sshd.sh, which
 * sets these variables; skipped otherwise.
 */
class SshIntegrationTest {

    private val host = System.getenv("CLAUDERC_IT_HOST")
    private val port = System.getenv("CLAUDERC_IT_PORT")?.toInt() ?: 22
    private val user = System.getenv("CLAUDERC_IT_USER").orEmpty()
    private val expectedFp = System.getenv("CLAUDERC_IT_HOSTKEY_FP").orEmpty()
    private val installCmd = System.getenv("CLAUDERC_IT_INSTALL").orEmpty()

    @Test
    fun endToEnd() {
        assumeTrue("set CLAUDERC_IT_HOST to run", host != null)
        val identity = Ed25519Identity(ByteArray(32).also { SecureRandom().nextBytes(it) })
        val pub = identity.authorizedKey("clauderc")

        // 1. Host key probe matches what ssh-keygen says on the server.
        val hk = SshRunner.probeHostKey(host!!, port, user)
        assertEquals(expectedFp, hk.fingerprint)
        val cfg = ServerConfig(host, port, user, hk.type, hk.blob)

        // 2. Before the key is installed, auth fails cleanly.
        try {
            SshRunner.exec(cfg, identity, "status")
            fail("expected auth failure")
        } catch (e: SshFailure) {
            assertEquals(SshFailure.Kind.AUTH_FAILED, e.kind)
        }

        // 3. Install the key through install-launcher-key.sh.
        val p = ProcessBuilder("sh", "-c", "$installCmd '$pub'").inheritIO().start()
        assertEquals(0, p.waitFor())

        // 4. Allowlisted action works and returns one JSON object.
        val status = SshRunner.exec(cfg, identity, "status")
        assertTrue(status, status.trim().startsWith("{\"ok\":true"))

        // 5. A shell, or anything off the allowlist, is forbidden.
        for (cmd in listOf("", "bash", "status; id", "sh -c id")) {
            val out = SshRunner.exec(cfg, identity, cmd)
            assertTrue("'$cmd' -> $out", out.contains("\"code\":\"forbidden\""))
        }

        // 6. Secrets go over stdin.
        val gh = SshRunner.exec(cfg, identity, "login-github", stdin = "ghp_" + "a".repeat(30))
        assertTrue(gh, gh.contains("\"ok\":true"))

        // 7. A different pinned key is refused before authenticating.
        val wrong = ByteArray(51).also { SecureRandom().nextBytes(it) }
        System.arraycopy(Base64.getDecoder().decode(hk.blob), 0, wrong, 0, 19) // same type prefix
        try {
            SshRunner.exec(cfg.copy(hostKeyBlob = Base64.getEncoder().encodeToString(wrong)), identity, "status")
            fail("expected host key mismatch")
        } catch (e: SshFailure) {
            assertEquals(SshFailure.Kind.HOST_KEY_CHANGED, e.kind)
        }
    }
}
