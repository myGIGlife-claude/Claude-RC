package life.mygig.clauderc

import java.time.Instant
import life.mygig.clauderc.data.DriveError
import life.mygig.clauderc.data.DriveFile
import life.mygig.clauderc.data.DriveLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveBackupTest {
    private val json = """{"files":[
      {"id":"aaaaaaaaaa1","name":"clauderc-app-20260101T000000Z.clrcbk","size":"1500","createdTime":"2026-01-01T00:00:00.000Z"},
      {"id":"aaaaaaaaaa3","name":"clauderc-app-20260301T000000Z.clrcbk","size":"2048","createdTime":"2026-03-01T00:00:00.000Z"},
      {"id":"aaaaaaaaaa2","name":"clauderc-app-20260201T000000Z.clrcbk","createdTime":"2026-02-01T00:00:00.000Z"},
      {"id":"aaaaaaaaaa9","name":"other.txt","size":"5","createdTime":"2026-04-01T00:00:00.000Z"},
      {"name":"clauderc-app-noid.clrcbk"}]}"""

    @Test fun nameIsUtcStamped() {
        assertEquals("clauderc-app-20261120T140305Z.clrcbk", DriveLogic.fileName(Instant.parse("2026-11-20T14:03:05Z")))
    }

    @Test fun listIsNewestFirstAndFiltered() {
        val l = DriveLogic.parseList(json)
        assertEquals(listOf("aaaaaaaaaa3", "aaaaaaaaaa2", "aaaaaaaaaa1"), l.map { it.id })
        assertEquals(0L, l[1].size)
        assertEquals(2048L, l[0].size)
    }

    @Test fun emptyListParses() { assertTrue(DriveLogic.parseList("{}").isEmpty()) }

    @Test fun retentionKeepsNewestThree() {
        val files = (1..5).map { DriveFile("id$it", "clauderc-app-$it", 1, "2026-01-0${it}T00:00:00Z") }
        assertEquals(listOf("id2", "id1"), DriveLogic.toDelete(files).map { it.id })
        assertTrue(DriveLogic.toDelete(files.take(3)).isEmpty())
    }

    @Test fun multipartHasMetadataAndBytes() {
        val body = DriveLogic.multipartBody("n.clrcbk", byteArrayOf(1, 2, 3), "B").toString(Charsets.ISO_8859_1)
        assertTrue(body.contains("\"parents\":[\"appDataFolder\"]"))
        assertTrue(body.contains("\"name\":\"n.clrcbk\""))
        assertTrue(body.endsWith("\r\n--B--\r\n"))
    }

    @Test fun urlsUseAppDataFolderAndRejectBadIds() {
        assertTrue(DriveLogic.listUrl().contains("spaces=appDataFolder"))
        assertTrue(DriveLogic.downloadUrl("abcdefghij12").endsWith("abcdefghij12?alt=media"))
        assertThrows(IllegalArgumentException::class.java) { DriveLogic.deleteUrl("../x") }
    }

    @Test fun httpErrorsMapToKinds() {
        assertEquals(DriveError.EXPIRED, DriveLogic.httpError(401).kind)
        assertEquals(DriveError.DENIED, DriveLogic.httpError(403).kind)
        assertEquals(DriveError.OTHER, DriveLogic.httpError(500).kind)
        assertTrue(DriveLogic.signInMessage(10).contains("release"))
    }
}
