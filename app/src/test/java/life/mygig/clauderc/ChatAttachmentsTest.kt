package life.mygig.clauderc

import life.mygig.clauderc.ui.vm.AttachQueue
import life.mygig.clauderc.ui.vm.AttachState
import life.mygig.clauderc.ui.vm.Attachment
import life.mygig.clauderc.ui.vm.OutputFormat
import life.mygig.clauderc.ui.vm.composeMessage
import life.mygig.clauderc.ui.vm.outputFormat
import life.mygig.clauderc.ui.vm.sampleSize
import life.mygig.clauderc.ui.vm.splitName
import life.mygig.clauderc.ui.vm.targetSize
import life.mygig.clauderc.ui.vm.uniqueBase
import life.mygig.clauderc.ui.vm.uploadName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatAttachmentsTest {
    private fun item(id: Long, state: AttachState = AttachState.Queued, image: Boolean = true, path: String? = null) =
        Attachment(id, "content://x/$id", "IMG_$id.jpg", "IMG_$id", "image/jpeg", 1000, image, state, path)

    @Test fun composesTheMessageLikeBefore() {
        assertEquals("look [attached: uploads/a.jpg] [attached: uploads/b.png]", composeMessage("look", listOf("uploads/a.jpg", "uploads/b.png")))
        assertEquals("look [attached: uploads/a.jpg]", composeMessage("  look  \n", listOf("uploads/a.jpg")))
        // Only attachments, no text.
        assertEquals("[attached: uploads/a.jpg] [attached: uploads/b.jpg]", composeMessage("", listOf("uploads/a.jpg", "uploads/b.jpg")))
        assertEquals("[attached: uploads/a.jpg]", composeMessage("   ", listOf("uploads/a.jpg")))
        // No attachments: the text goes exactly as typed (as before).
        assertEquals(" hi there ", composeMessage(" hi there ", emptyList()))
    }

    @Test fun splitsAndDeduplicatesNames() {
        assertEquals("IMG_1" to "jpeg", splitName("IMG_1.jpeg"))
        assertEquals("README" to "", splitName("README"))
        assertEquals(".env" to "", splitName(".env"))
        assertEquals("a.tar" to "gz", splitName("a.tar.gz"))
        assertEquals("trailing." to "", splitName("trailing."))

        assertEquals("shot", uniqueBase("shot", emptyList()))
        assertEquals("shot-2", uniqueBase("shot", listOf("shot")))
        assertEquals("shot-3", uniqueBase("shot", listOf("shot", "shot-2")))
        assertEquals("Shot-2", uniqueBase("Shot", listOf("shot")))   // case doesn't make it different
        assertEquals("file", uniqueBase("", emptyList()))

        val taken = mutableListOf<String>()
        val names = listOf("image.jpg", "image.jpg", "image.png", "other.jpg").map { uniqueBase(splitName(it).first, taken).also { b -> taken += b } }
        assertEquals(listOf("image", "image-2", "image-3", "other"), names)

        assertEquals("image-2.jpg", uploadName("image-2", "jpg"))
        assertEquals("Makefile", uploadName("Makefile", ""))
    }

    @Test fun computesTargetSizes() {
        assertEquals(1000 to 800, targetSize(1000, 800, 2560))
        assertEquals(2560 to 2560, targetSize(2560, 2560, 2560))
        assertEquals(2560 to 1920, targetSize(4000, 3000, 2560))
        assertEquals(1920 to 2560, targetSize(3000, 4000, 2560))
        assertEquals(2560 to 1440, targetSize(5120, 2880, 2560))
        assertEquals(2560 to 1, targetSize(100_000, 10, 2560))   // never 0 px
        assertEquals(0 to 0, targetSize(0, 100, 2560))

        assertEquals(1, sampleSize(4000, 3000, 2560))   // halving would go below 2560
        assertEquals(2, sampleSize(6000, 4000, 2560))
        assertEquals(4, sampleSize(12000, 9000, 2560))
        assertEquals(1, sampleSize(1000, 800, 2560))
    }

    @Test fun picksTheOutputFormat() {
        assertEquals(OutputFormat.JPEG, outputFormat("image/jpeg", 5_000_000))
        assertEquals(OutputFormat.JPEG, outputFormat("image/heic", 3_000_000))
        assertEquals(OutputFormat.PNG, outputFormat("image/png", 1_500_000))
        assertEquals(OutputFormat.PNG, outputFormat("image/png", -1))   // size unknown: keep it a PNG
        assertEquals(OutputFormat.JPEG, outputFormat("image/png", 3_000_000))
        assertEquals(OutputFormat.ORIGINAL, outputFormat("image/gif", 3_000_000))
        assertEquals(OutputFormat.ORIGINAL, outputFormat("image/webp", 3_000_000))
        assertEquals(OutputFormat.ORIGINAL, outputFormat("application/pdf", 3_000_000))
        assertEquals(OutputFormat.ORIGINAL, outputFormat(null, 10))
        assertEquals("jpg", OutputFormat.JPEG.ext)
    }

    @Test fun queueTransitions() {
        var q = AttachQueue.add(emptyList(), listOf(item(1), item(2), item(3)))
        q = AttachQueue.start(q, 1)
        assertEquals(AttachState.Uploading, q[0].state)
        q = AttachQueue.finish(q, 1, "uploads/IMG_1.jpg")
        assertEquals(AttachState.Ready, q[0].state)
        assertEquals("uploads/IMG_1.jpg", q[0].path)
        q = AttachQueue.start(q, 2)
        q = AttachQueue.fail(q, 2, "No connection")
        assertEquals(AttachState.Failed, q[1].state)
        assertEquals("No connection", q[1].error)
        // A ready one never turns failed; retry only applies to failed ones.
        assertEquals(AttachState.Ready, AttachQueue.fail(q, 1, "x")[0].state)
        assertEquals(AttachState.Ready, AttachQueue.retry(q, 1)[0].state)
        q = AttachQueue.retry(q, 2)
        assertEquals(AttachState.Queued, q[1].state)
        assertNull(q[1].error)
        // Removed while running: its late result is ignored.
        q = AttachQueue.remove(q, 3)
        assertEquals(listOf(1L, 2L), q.map { it.id })
        assertEquals(q, AttachQueue.finish(q, 3, "uploads/late.jpg"))
        assertEquals(q, AttachQueue.fail(q, 3, "late"))
        assertEquals(listOf(2L), AttachQueue.removeAll(q, setOf(1L)).map { it.id })
    }

    @Test fun sendRules() {
        val ready = listOf(item(1, AttachState.Ready, path = "uploads/a.jpg"))
        assertTrue(AttachQueue.canSend("", ready))   // attachments only
        assertTrue(AttachQueue.canSend("hi", emptyList()))
        assertFalse(AttachQueue.canSend("  ", emptyList()))
        assertFalse(AttachQueue.canSend("hi", ready + item(2, AttachState.Uploading)))
        assertFalse(AttachQueue.canSend("hi", ready + item(2, AttachState.Queued)))
        assertTrue(AttachQueue.canSend("hi", ready + item(2, AttachState.Failed)))   // a failed one doesn't block
        assertFalse(AttachQueue.canSend("", listOf(item(2, AttachState.Failed))))
        // Ready paths in pick order, failed ones left out.
        val mixed = listOf(item(1, AttachState.Ready, path = "uploads/1.jpg"), item(2, AttachState.Failed), item(3, AttachState.Ready, path = "uploads/3.jpg"))
        assertEquals(listOf("uploads/1.jpg", "uploads/3.jpg"), AttachQueue.readyPaths(mixed))
    }

    @Test fun hintsAndSummaries() {
        val five = listOf(
            item(1, AttachState.Ready, path = "p"), item(2, AttachState.Uploading), item(3, AttachState.Uploading),
            item(4, AttachState.Queued), item(5, AttachState.Queued),
        )
        assertEquals("Uploading 2 of 5…", AttachQueue.uploadHint(five))
        assertNull(AttachQueue.uploadHint(listOf(item(1, AttachState.Ready, path = "p"))))
        assertNull(AttachQueue.summary(five))   // short strip: no summary
        val twelve = (1L..12L).map { item(it, AttachState.Ready, path = "p$it") }
        assertEquals("12 photos ready", AttachQueue.summary(twelve))
        val files = (1L..8L).map { item(it, AttachState.Ready, image = false, path = "p$it") }
        assertEquals("8 files ready", AttachQueue.summary(files))
        val someFailed = twelve.dropLast(1) + item(12, AttachState.Failed)
        assertEquals("11 of 12 photos ready · 1 failed, tap to retry", AttachQueue.summary(someFailed))
        assertEquals("Photo 3 of 5, uploading", AttachQueue.describe(five[2], 2, 5))
        assertEquals("Photo 1 of 5, ready", AttachQueue.describe(five[0], 0, 5))
        assertEquals("File IMG_2.jpg, 2 of 3, failed: No connection. Double-tap to retry",
            AttachQueue.describe(item(2, AttachState.Failed, image = false).copy(error = "No connection"), 1, 3))
    }
}
