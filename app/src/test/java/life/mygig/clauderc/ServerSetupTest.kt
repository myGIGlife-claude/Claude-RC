package life.mygig.clauderc

import java.io.File
import life.mygig.clauderc.api.ServerSetup
import life.mygig.clauderc.api.Updates
import life.mygig.clauderc.ssh.SshFailure
import life.mygig.clauderc.ssh.passwordConnectFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class ServerSetupTest {
    private val key = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIFakeKeyForTestsOnly0123456789abcdefABCDEF clauderc"

    @Test fun shQuoteWrapsAndEscapesSingleQuotes() {
        assertEquals("'abc'", ServerSetup.shQuote("abc"))
        assertEquals("''", ServerSetup.shQuote(""))
        assertEquals("'it'\\''s'", ServerSetup.shQuote("it's"))
        assertEquals("'\$(id) `id` \"x\" ; |'", ServerSetup.shQuote("\$(id) `id` \"x\" ; |"))
    }

    /** What bash makes of the quoted word is exactly the original text (skipped where there is no bash). */
    @Test fun shQuoteRoundTripsThroughBash() {
        assumeTrue(File("/bin/bash").canExecute())
        for (s in listOf("plain", "it's", "a'b'c", "\$HOME \$(id) `id`", "back\\slash", "sp ace\ttab", "'", "''", "!x")) {
            val p = ProcessBuilder("/bin/bash", "-c", "printf %s " + ServerSetup.shQuote(s)).redirectErrorStream(true).start()
            val out = p.inputStream.readBytes().toString(Charsets.UTF_8)
            p.waitFor()
            assertEquals(s, out)
        }
    }

    @Test fun validateUsesTheServerRules() {
        assertNull(ServerSetup.validate("example.com", "22", "deploy"))
        assertNull(ServerSetup.validate("192.168.1.10", "2222", "_svc"))
        assertNull(ServerSetup.validate("2001:db8::1", "22", "ubuntu"))
        assertNotNull(ServerSetup.validate("", "22", "deploy"))
        assertNotNull(ServerSetup.validate("example.com;id", "22", "deploy"))
        assertNotNull(ServerSetup.validate("-oProxyCommand=x", "22", "deploy"))
        assertNotNull(ServerSetup.validate("ex ample.com", "22", "deploy"))
        assertNotNull(ServerSetup.validate("example.com", "0", "deploy"))
        assertNotNull(ServerSetup.validate("example.com", "65536", "deploy"))
        assertNotNull(ServerSetup.validate("example.com", "x", "deploy"))
        assertNotNull(ServerSetup.validate("example.com", "22", ""))
        assertNotNull(ServerSetup.validate("example.com", "22", "de ploy"))
        assertNotNull(ServerSetup.validate("example.com", "22", "root;id"))
        assertNotNull(ServerSetup.validate("example.com", "22", "\$(id)"))
        assertNotNull(ServerSetup.validate("example.com", "22", "-x"))
    }

    @Test fun scriptStartsWithTheQuotedValuesAndRunsTheInstaller() {
        val pw = "p'w \$(id) `x`"
        val s = ServerSetup.script(key, pw)
        val lines = s.lines()
        assertEquals("SUDO_PW=" + ServerSetup.shQuote(pw), lines[0])
        assertEquals("INSTALL=" + ServerSetup.shQuote(Updates.installCommand(key)), lines[1])
        // The password appears once, in that quoted assignment: never as a command argument.
        val plain = "S3cret pw"
        assertEquals(1, ServerSetup.script(key, plain).windowed(plain.length).count { it == plain })
        assertTrue(s.contains("bash -o pipefail -c \"\$INSTALL\""))
        assertTrue(s.contains("curl -fsSL https://claude.ai/install.sh | bash"))
        assertTrue(s.contains("SUDO_ASKPASS"))
        assertTrue(s.trimEnd().endsWith("main </dev/null; exit \$?"))
    }

    @Test fun scriptWithoutSudoPasswordHasAnEmptyOne() {
        assertTrue(ServerSetup.script(key, "").startsWith("SUDO_PW=''\n"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun scriptRefusesAnOddKey() { ServerSetup.script("ssh-ed25519 AAAA'; rm -rf ~; '", "") }

    @Test(expected = IllegalArgumentException::class)
    fun scriptRefusesAMultiLinePassword() { ServerSetup.script(key, "a\nb") }

    @Test fun parseReadsTheMarkers() {
        val out = """
            Installing jq (apt-get)...
            @@clauderc sudo=ok
            @@clauderc missing=
            @@clauderc claude=installed
            Installed ~/claude-setup.sh
            @@clauderc install=ok
            @@clauderc autostart=ok
        """.trimIndent()
        val r = ServerSetup.parse(out)
        assertEquals("ok", r.sudo)
        assertTrue(r.sudoWorked)
        assertTrue(r.missing.isEmpty())
        assertEquals("installed", r.claude)
        assertTrue(r.installed)
        assertTrue(r.autostart)
        assertEquals("Installing jq (apt-get)...\nInstalled ~/claude-setup.sh", ServerSetup.visible(out))
    }

    @Test fun parseOfAFailedRun() {
        val r = ServerSetup.parse("@@clauderc sudo=denied\n@@clauderc missing=jq,tmux\ncurl: (6) Could not resolve host\n@@clauderc install=failed\n")
        assertFalse(r.installed)
        assertFalse(r.sudoWorked)
        assertEquals(listOf("jq", "tmux"), r.missing)
        assertFalse(ServerSetup.parse("no markers at all").installed)
    }

    @Test fun notesSayWhatIsMissing() {
        val n = ServerSetup.notes(ServerSetup.Report(sudo = "denied", missing = listOf("jq"), installed = true, claude = "failed"), claudeSignedIn = false)
        assertTrue(n.any { "sudo password wasn't accepted" in it })
        assertTrue(n.any { "Still missing on the server: jq" in it })
        assertTrue(n.any { it.startsWith("Claude Code not installed") })
        assertFalse(n.any { "not signed in" in it })   // not installed says it already
        assertTrue(n.last().startsWith("Tip:"))
        val ok = ServerSetup.notes(ServerSetup.Report(sudo = "ok", installed = true, claude = "present", autostart = true), claudeSignedIn = false)
        assertTrue(ok.any { it == "Claude is not signed in yet: open Connections > Logins." })
        assertEquals(2, ok.size)
    }

    @Test fun passwordFailuresMapToKinds() {
        assertEquals(SshFailure.Kind.AUTH_FAILED, passwordConnectFailure("Auth fail for methods 'password,keyboard-interactive'", false, "h", 22).kind)
        assertEquals("Wrong user name or password.", passwordConnectFailure("Auth cancel for methods 'password'", false, "h", 22).message)
        assertEquals(SshFailure.Kind.HOST_KEY_CHANGED, passwordConnectFailure("reject HostKey: h", true, "h", 22).kind)
        assertEquals(SshFailure.Kind.HOST_KEY_CHANGED, passwordConnectFailure("Algorithm negotiation fail: algorithmName=\"server_host_key\"", false, "h", 22).kind)
        assertEquals(SshFailure.Kind.TIMEOUT, passwordConnectFailure("timeout: socket is not established", false, "h", 22).kind)
        val n = passwordConnectFailure("java.net.UnknownHostException: h", false, "h", 2222)
        assertEquals(SshFailure.Kind.NETWORK, n.kind)
        assertTrue(n.message!!.contains("h:2222"))
    }
}
