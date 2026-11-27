package life.mygig.clauderc

import life.mygig.clauderc.data.AppBackupPayload
import life.mygig.clauderc.data.PURPOSE_MAX
import life.mygig.clauderc.data.PURPOSE_PRESETS
import life.mygig.clauderc.data.Server
import life.mygig.clauderc.data.appBackupJson
import life.mygig.clauderc.data.cleanPurpose
import life.mygig.clauderc.data.settingsJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerPurposeTest {
    @Test fun trimsAndKeepsPlainText() {
        assertEquals("Web hosting", cleanPurpose("  Web hosting \t"))
        assertEquals("", cleanPurpose("   "))
        assertEquals("Build / CI", cleanPurpose("Build / CI"))
        assertEquals("Säkerhetskopia 🗄", cleanPurpose("Säkerhetskopia 🗄"))
    }

    @Test fun dropsControlCharacters() {
        assertEquals("ab", cleanPurpose("a\nb"))
        assertEquals("ab", cleanPurpose("a\u0000\u001b\u007fb"))
        assertEquals("Database", cleanPurpose("\r\nDatabase\n"))
    }

    @Test fun capsLength() {
        val long = "x".repeat(100)
        assertEquals(PURPOSE_MAX, cleanPurpose(long).length)
        // A cut that lands inside the text leaves no trailing space.
        assertEquals("a".repeat(39), cleanPurpose("a".repeat(39) + " bbbb"))
    }

    @Test fun neverCutsAnEmojiInHalf() {
        val s = cleanPurpose("a".repeat(39) + "🗄")   // the emoji is two chars, starting at index 39
        assertEquals("a".repeat(39), s)
        assertFalse(s.last().isHighSurrogate())
    }

    @Test fun presetsAreClean() {
        assertEquals(6, PURPOSE_PRESETS.size)
        assertEquals(PURPOSE_PRESETS.size, PURPOSE_PRESETS.toSet().size)
        PURPOSE_PRESETS.forEach { assertEquals(it, cleanPurpose(it)); assertTrue(it.isNotBlank()) }
    }

    @Test fun oldSavedServersLoadWithoutPurpose() {
        val old = """[{"id":"a","host":"h.example","port":22,"user":"u","hostKeyBlob":"b","inCluster":true}]"""
        val list = settingsJson.decodeFromString<List<Server>>(old)
        assertEquals("", list.single().purpose)
        assertEquals("h.example", list.single().host)
        assertEquals(true, list.single().inCluster)
    }

    @Test fun purposeRoundTrips() {
        val s = Server("a", "h.example", 22, "u", "ssh-ed25519", "b", "fp", purpose = "Web hosting")
        val text = settingsJson.encodeToString(listOf(s))
        assertEquals(listOf(s), settingsJson.decodeFromString<List<Server>>(text))
        // An empty purpose isn't written at all, so older apps see the same list as before.
        assertFalse(settingsJson.encodeToString(listOf(s.copy(purpose = ""))).contains("purpose"))
    }

    @Test fun olderAppReadsNewerList() {
        val newer = """[{"id":"a","host":"h","user":"u","purpose":"Backup","someFutureField":1}]"""
        assertEquals("Backup", settingsJson.decodeFromString<List<Server>>(newer).single().purpose)
        assertEquals("Backup", appBackupJson.decodeFromString<List<Server>>(newer).single().purpose)
    }

    @Test fun oldBackupImports() {
        val old = """{"servers":[{"id":"a","host":"h","port":22,"user":"u","hostKeyType":"","hostKeyBlob":"b",""" +
            """"hostKeyFingerprint":"","previousFingerprint":"","inCluster":null}],"activeId":"a","appLock":false,""" +
            """"notify":false,"theme":"SYSTEM","sshSeed":"AA=="}"""
        val p = appBackupJson.decodeFromString<AppBackupPayload>(old)
        assertEquals("", p.servers.single().purpose)
    }

    @Test fun backupCarriesPurpose() {
        val p = AppBackupPayload(listOf(Server("a", "h", purpose = "Database")), "a", false, false, "SYSTEM", "AA==")
        val back = appBackupJson.decodeFromString<AppBackupPayload>(appBackupJson.encodeToString(p))
        assertEquals("Database", back.servers.single().purpose)
    }
}
