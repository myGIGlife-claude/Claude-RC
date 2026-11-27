package life.mygig.clauderc.ui.vm

/*
 * Chat attachments: the pure part (no Android), so it is unit-tested on the JVM.
 * The queue lives in ChatController (ui/vm/Chat.kt), the photo work in ui/screens/ChatImage.kt,
 * the strip in ui/screens/ChatAttachments.kt.
 */

/** The longest edge a photo is sent at; bigger ones are scaled down. */
const val PHOTO_MAX_EDGE = 2560
/** What the server takes (its `upload` action refuses more than 15 MB). */
const val UPLOAD_LIMIT_BYTES = 15L * 1024 * 1024
/** Originals of photos we re-encode may be bigger (camera shots); they are streamed, never held whole. */
const val PHOTO_SOURCE_LIMIT_BYTES = 40L * 1024 * 1024
/** A PNG (usually a screenshot) bigger than this is sent as JPEG instead. */
const val PNG_KEEP_LIMIT_BYTES = 2L * 1024 * 1024
/** How many photos the photo picker lets you pick at once. */
const val PHOTO_PICK_MAX = 20
/** Uploads running at the same time. */
const val UPLOAD_PARALLEL = 3

enum class AttachState { Queued, Uploading, Ready, Failed }

/**
 * One picked file. [uri] is the content URI as text (the picker's read grant lasts while the app's
 * activity lives, so a retry can read it again). [base] is the unique name stem it is uploaded under
 * (the extension follows the format it is sent in). [path] is where the server saved it, once Ready.
 */
data class Attachment(
    val id: Long,
    val uri: String,
    val displayName: String,
    val base: String,
    val mime: String?,
    val size: Long,
    val image: Boolean,
    val state: AttachState = AttachState.Queued,
    val path: String? = null,
    val error: String? = null,
)

/** The message as the server and Claude expect it: the text, then one ` [attached: path]` per file, in order. */
fun composeMessage(text: String, paths: List<String>): String =
    if (paths.isEmpty()) text else (text.trimEnd() + paths.joinToString("") { " [attached: $it]" }).trim()

/** "IMG_1.jpeg" -> ("IMG_1", "jpeg"); "README" -> ("README", ""); ".env" -> (".env", ""). */
fun splitName(name: String): Pair<String, String> {
    val dot = name.lastIndexOf('.')
    return if (dot <= 0 || dot == name.length - 1) name to "" else name.substring(0, dot) to name.substring(dot + 1)
}

/** [base] or, if that is taken (any case), base-2, base-3… */
fun uniqueBase(base: String, taken: Collection<String>): String {
    val lower = taken.mapTo(HashSet()) { it.lowercase() }
    val b = base.ifBlank { "file" }
    if (b.lowercase() !in lower) return b
    var n = 2
    while ("$b-$n".lowercase() in lower) n++
    return "$b-$n"
}

/** The file name to upload: the stem plus the extension of the format it is sent in. */
fun uploadName(base: String, ext: String): String = if (ext.isEmpty()) base else "$base.$ext"

/** How a picked file is sent. JPEG and PNG mean decoded (orientation applied, maybe scaled) and re-encoded. */
enum class OutputFormat(val ext: String?) { JPEG("jpg"), PNG("png"), ORIGINAL(null) }

/**
 * Photos (JPEG, HEIC) go out as JPEG; a PNG stays PNG unless it is bigger than 2 MB ([sizeBytes] < 0 = unknown: keep PNG);
 * everything else (GIF, WebP, any file) passes through unchanged.
 */
fun outputFormat(mime: String?, sizeBytes: Long): OutputFormat = when (mime?.lowercase()) {
    "image/jpeg", "image/jpg", "image/pjpeg", "image/heic", "image/heif", "image/heic-sequence", "image/heif-sequence" -> OutputFormat.JPEG
    "image/png" -> if (sizeBytes > PNG_KEEP_LIMIT_BYTES) OutputFormat.JPEG else OutputFormat.PNG
    else -> OutputFormat.ORIGINAL
}

/** The size to send a [w] x [h] image at: unchanged if its long edge is at most [max], else scaled so it is [max] (aspect kept, at least 1 px). */
fun targetSize(w: Int, h: Int, max: Int): Pair<Int, Int> {
    if (w <= 0 || h <= 0) return 0 to 0
    val long = maxOf(w, h)
    if (long <= max) return w to h
    val s = max.toDouble() / long
    return if (w >= h) max to maxOf(1, Math.round(h * s).toInt()) else maxOf(1, Math.round(w * s).toInt()) to max
}

/** BitmapFactory's inSampleSize: the biggest power of two that still leaves the long edge at least [max] (exact scaling does the rest). */
fun sampleSize(w: Int, h: Int, max: Int): Int {
    val long = maxOf(w, h)
    var s = 1
    while (max > 0 && long / (s * 2) >= max) s *= 2
    return s
}

/** The upload queue's transitions. Each takes the list and returns the new one; an id that is gone is ignored (removed while it ran). */
object AttachQueue {
    private fun List<Attachment>.change(id: Long, f: (Attachment) -> Attachment?) = map { if (it.id == id) f(it) ?: it else it }

    fun add(items: List<Attachment>, new: List<Attachment>) = items + new
    fun remove(items: List<Attachment>, id: Long) = items.filterNot { it.id == id }
    fun removeAll(items: List<Attachment>, ids: Set<Long>) = items.filterNot { it.id in ids }
    /** Queued -> Uploading. */
    fun start(items: List<Attachment>, id: Long) = items.change(id) { if (it.state == AttachState.Queued) it.copy(state = AttachState.Uploading) else null }
    /** Uploading -> Ready with the server's path. */
    fun finish(items: List<Attachment>, id: Long, path: String) = items.change(id) {
        if (it.state == AttachState.Uploading || it.state == AttachState.Queued) it.copy(state = AttachState.Ready, path = path, error = null) else null
    }
    /** -> Failed with a reason to show (never Ready -> Failed). */
    fun fail(items: List<Attachment>, id: Long, error: String) = items.change(id) {
        if (it.state != AttachState.Ready) it.copy(state = AttachState.Failed, error = error) else null
    }
    /** Failed -> Queued. */
    fun retry(items: List<Attachment>, id: Long) = items.change(id) { if (it.state == AttachState.Failed) it.copy(state = AttachState.Queued, error = null) else null }

    fun active(items: List<Attachment>) = items.count { it.state == AttachState.Queued || it.state == AttachState.Uploading }
    fun readyPaths(items: List<Attachment>) = items.filter { it.state == AttachState.Ready }.mapNotNull { it.path }

    /** Send needs text or a ready file, and nothing still uploading (failed ones don't hold it up: they just aren't sent). */
    fun canSend(text: String, items: List<Attachment>) =
        active(items) == 0 && (text.isNotBlank() || items.any { it.state == AttachState.Ready })

    /** "Uploading 2 of 5…" while files are on their way, else null. */
    fun uploadHint(items: List<Attachment>): String? {
        val active = active(items)
        if (active == 0) return null
        val total = items.count { it.state != AttachState.Failed }
        return "Uploading ${total - active + 1} of $total…"
    }

    /** "12 photos ready" (or files / attachments, plus how many failed) when the strip is too long to count at a glance. */
    fun summary(items: List<Attachment>, over: Int = 6): String? {
        if (items.size <= over) return null
        val noun = when { items.all { it.image } -> "photos"; items.none { it.image } -> "files"; else -> "attachments" }
        val ready = items.count { it.state == AttachState.Ready }
        val failed = items.count { it.state == AttachState.Failed }
        val head = if (ready == items.size) "${items.size} $noun ready" else "$ready of ${items.size} $noun ready"
        return if (failed > 0) "$head · $failed failed, tap to retry" else head
    }

    /** What TalkBack reads for an item: "Photo 3 of 5, uploading". */
    fun describe(a: Attachment, index: Int, total: Int): String {
        val kind = if (a.image) "Photo" else "File ${a.displayName},"
        val state = when (a.state) {
            AttachState.Queued -> "waiting to upload"
            AttachState.Uploading -> "uploading"
            AttachState.Ready -> "ready"
            AttachState.Failed -> "failed" + (a.error?.let { ": $it" } ?: "") + ". Double-tap to retry"
        }
        return "$kind ${index + 1} of $total, $state"
    }
}
