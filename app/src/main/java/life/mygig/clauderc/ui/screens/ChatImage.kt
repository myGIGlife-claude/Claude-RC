package life.mygig.clauderc.ui.screens

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import life.mygig.clauderc.ui.vm.Attachment
import life.mygig.clauderc.ui.vm.OutputFormat
import life.mygig.clauderc.ui.vm.PHOTO_MAX_EDGE
import life.mygig.clauderc.ui.vm.PHOTO_SOURCE_LIMIT_BYTES
import life.mygig.clauderc.ui.vm.UPLOAD_LIMIT_BYTES
import life.mygig.clauderc.ui.vm.outputFormat
import life.mygig.clauderc.ui.vm.sampleSize
import life.mygig.clauderc.ui.vm.splitName
import life.mygig.clauderc.ui.vm.targetSize

/**
 * Reading picked files and getting photos ready to send. Everything here BLOCKS: call it on
 * Dispatchers.IO. Photos are streamed from the content URI (bounds, EXIF, then one sampled decode),
 * so a 40 MB original is never held whole; at most two bitmaps exist per photo at a time
 * (the sampled decode and its scaled/rotated copy), and the caller limits how many run at once.
 */
object ChatImage {
    sealed interface Prepared {
        class Ok(val bytes: ByteArray, val ext: String) : Prepared
        class Bad(val reason: String) : Prepared
    }

    /** Display name, MIME type (guessed from the extension if the provider gives none) and size (-1 if unknown). */
    fun meta(resolver: ContentResolver, uri: Uri): Triple<String, String?, Long> {
        var name = "file"
        var size = -1L
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    if (!c.isNull(0)) c.getString(0)?.takeIf { it.isNotBlank() }?.let { name = it }
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
        } catch (_: Exception) { }
        val mime = (try { resolver.getType(uri) } catch (_: Exception) { null })
            ?: splitName(name).second.takeIf { it.isNotEmpty() }?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it.lowercase()) }
        return Triple(name, mime, size)
    }

    /** The bytes to upload and their extension, or why it can't be sent. */
    fun prepare(resolver: ContentResolver, uri: Uri, a: Attachment): Prepared = try {
        prepareOrThrow(resolver, uri, a)
    } catch (_: OutOfMemoryError) {
        Prepared.Bad("Not enough memory for this photo")
    } catch (_: SecurityException) {
        Prepared.Bad("Access to it ran out: pick it again")
    } catch (e: java.io.IOException) {
        Prepared.Bad("Couldn't read it" + (e.message?.let { ": $it" } ?: ""))
    }

    private fun prepareOrThrow(resolver: ContentResolver, uri: Uri, a: Attachment): Prepared {
        val format = if (a.image) outputFormat(a.mime, a.size) else OutputFormat.ORIGINAL
        val origExt = splitName(a.displayName).second
        if (format == OutputFormat.ORIGINAL) {
            if (a.size > UPLOAD_LIMIT_BYTES) return Prepared.Bad("Bigger than 15 MB")
            val bytes = readLimited(resolver, uri, UPLOAD_LIMIT_BYTES) ?: return Prepared.Bad("Couldn't read it, or it's bigger than 15 MB")
            if (bytes.isEmpty()) return Prepared.Bad("The file is empty")
            return Prepared.Ok(bytes, origExt)
        }
        if (a.size > PHOTO_SOURCE_LIMIT_BYTES) return Prepared.Bad("Bigger than 40 MB")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // A bounds-only decode always returns null: only a missing stream means "couldn't open".
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds); true } ?: return Prepared.Bad("Couldn't open it")
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return Prepared.Bad("Couldn't read this image")
        val orientation = orientation(resolver, uri)
        val (tw, th) = targetSize(w, h, PHOTO_MAX_EDGE)
        // A screenshot that needs nothing done goes as it is: re-encoding a PNG only costs time.
        if (format == OutputFormat.PNG && tw == w && th == h && orientation == ExifInterface.ORIENTATION_NORMAL) {
            val bytes = readLimited(resolver, uri, UPLOAD_LIMIT_BYTES) ?: return Prepared.Bad("Couldn't read it, or it's bigger than 15 MB")
            if (bytes.isEmpty()) return Prepared.Bad("The file is empty")
            return Prepared.Ok(bytes, "png")
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(w, h, PHOTO_MAX_EDGE) }
        val src = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return Prepared.Bad("Couldn't decode this image")
        var out = try { transform(src, tw, th, orientation) } catch (e: Throwable) { src.recycle(); throw e }
        if (out !== src) src.recycle()
        val jpeg = format == OutputFormat.JPEG
        if (jpeg && out.hasAlpha()) {
            // JPEG has no transparency: put a transparent screenshot on white instead of black.
            val flat = try { Bitmap.createBitmap(out.width, out.height, Bitmap.Config.ARGB_8888) } catch (e: Throwable) { out.recycle(); throw e }
            Canvas(flat).apply { drawColor(android.graphics.Color.WHITE); drawBitmap(out, 0f, 0f, null) }
            out.recycle()
            out = flat
        }
        val stream = java.io.ByteArrayOutputStream()
        val ok = try { out.compress(if (jpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG, if (jpeg) 85 else 100, stream) } finally { out.recycle() }
        val bytes = stream.toByteArray()
        if (!ok || bytes.isEmpty()) return Prepared.Bad("Couldn't encode this image")
        if (bytes.size > UPLOAD_LIMIT_BYTES) return Prepared.Bad("Still bigger than 15 MB after shrinking")
        return Prepared.Ok(bytes, format.ext ?: origExt)
    }

    /** A small picture for the strip: sampled so its SHORT edge is at least [px] (it is shown cropped), orientation applied; null if it can't be read. */
    fun thumbnail(resolver: ContentResolver, uri: Uri, px: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val short = minOf(bounds.outWidth, bounds.outHeight)
        if (short <= 0) null else {
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(short, short, px) }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }?.let { src ->
                val o = orientation(resolver, uri)
                val out = transform(src, src.width, src.height, o)
                if (out !== src) src.recycle()
                out
            }
        }
    } catch (_: Exception) { null } catch (_: OutOfMemoryError) { null }

    private fun orientation(resolver: ContentResolver, uri: Uri): Int = try {
        resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            ?: ExifInterface.ORIENTATION_NORMAL
    } catch (_: Exception) { ExifInterface.ORIENTATION_NORMAL }

    /** [src] scaled to [tw] x [th] (pre-rotation frame) with the EXIF [orientation] applied, in one copy; [src] itself if nothing changes. */
    private fun transform(src: Bitmap, tw: Int, th: Int, orientation: Int): Bitmap {
        val sx = tw.toFloat() / src.width
        val sy = th.toFloat() / src.height
        val m = Matrix().apply { setScale(sx, sy) }
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(-90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(-90f)
        }
        if (m.isIdentity) return src
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    /** The whole stream, or null if it is bigger than [limit] (stops reading as soon as it is). */
    private fun readLimited(resolver: ContentResolver, uri: Uri, limit: Long): ByteArray? =
        resolver.openInputStream(uri)?.use { s ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                if (out.size() + n > limit) return@use null
                out.write(buf, 0, n)
            }
            out.toByteArray()
        }
}
