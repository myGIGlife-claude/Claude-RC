package life.mygig.clauderc.data

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class DriveFile(val id: String, val name: String, val size: Long, val time: String)

enum class DriveError { NETWORK, EXPIRED, DENIED, OTHER }

class DriveException(val kind: DriveError, message: String) : Exception(message)

/** Pure parts of the Drive appDataFolder backup (request building, parsing, retention). Google only ever receives the already-encrypted file. */
object DriveLogic {
    const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    const val PREFIX = "clauderc-app-"
    const val KEEP = 3
    private const val API = "https://www.googleapis.com/drive/v3/files"
    private const val FIELDS = "id,name,size,createdTime"
    private val idRe = Regex("^[A-Za-z0-9_-]{10,100}$")
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    private val json = Json { ignoreUnknownKeys = true }

    fun fileName(now: Instant) = "$PREFIX${stamp.format(now)}.clrcbk"

    fun listUrl(): String {
        val q = URLEncoder.encode("name contains '$PREFIX' and trashed = false", "UTF-8")
        return "$API?spaces=appDataFolder&q=$q&orderBy=createdTime%20desc&pageSize=100&fields=" +
            URLEncoder.encode("files($FIELDS)", "UTF-8")
    }

    fun uploadUrl() = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=" + URLEncoder.encode(FIELDS, "UTF-8")
    fun downloadUrl(id: String) = "$API/${checkedId(id)}?alt=media"
    fun deleteUrl(id: String) = "$API/${checkedId(id)}"

    private fun checkedId(id: String): String { require(idRe.matches(id)) { "Bad Drive file id." }; return id }

    fun metadata(name: String) = """{"name":${JsonPrimitive(name)},"parents":["appDataFolder"]}"""

    fun multipartBody(name: String, bytes: ByteArray, boundary: String): ByteArray {
        val head = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n${metadata(name)}\r\n" +
            "--$boundary\r\nContent-Type: application/octet-stream\r\n\r\n"
        return head.toByteArray(Charsets.UTF_8) + bytes + "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
    }

    fun parseFile(o: JsonObject) = DriveFile(
        id = o["id"]?.jsonPrimitive?.contentOrNull ?: "",
        name = o["name"]?.jsonPrimitive?.contentOrNull ?: "",
        size = o["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
        time = o["createdTime"]?.jsonPrimitive?.contentOrNull ?: "",
    )

    /** Newest first; ignores files that are not ours or have no id. */
    fun parseList(body: String): List<DriveFile> =
        (json.parseToJsonElement(body).jsonObject["files"]?.jsonArray ?: return emptyList())
            .map { parseFile(it.jsonObject) }
            .filter { it.id.isNotEmpty() && it.name.startsWith(PREFIX) }
            .sortedByDescending { it.time }

    /** Everything beyond the newest [keep]. */
    fun toDelete(files: List<DriveFile>, keep: Int = KEEP): List<DriveFile> = files.sortedByDescending { it.time }.drop(keep)

    fun httpError(code: Int): DriveException = when (code) {
        401 -> DriveException(DriveError.EXPIRED, "Google sign-in expired. Try again.")
        403 -> DriveException(DriveError.DENIED, "Google Drive refused access. Check that the Drive API is on and the sign-in allows the app folder.")
        else -> DriveException(DriveError.OTHER, "Google Drive error ($code).")
    }

    /** Status codes of ApiException from Google sign-in. */
    fun signInMessage(statusCode: Int): String = when (statusCode) {
        10 -> "Google sign-in is not set up for this build. Only the official release app can sign in."
        12501, 16 -> "Sign-in cancelled."
        7 -> "No network. Check your connection."
        else -> "Google sign-in failed ($statusCode)."
    }

    fun label(f: DriveFile): String =
        f.time.take(16).replace('T', ' ') + " UTC, " + (if (f.size >= 1024) "${f.size / 1024} KB" else "${f.size} B")
}

interface DriveApi {
    suspend fun upload(token: String, name: String, bytes: ByteArray): DriveFile
    suspend fun list(token: String): List<DriveFile>
    suspend fun download(token: String, id: String): ByteArray
    suspend fun delete(token: String, id: String)
}

/** HttpURLConnection implementation; the token goes only in the Authorization header and is never logged. */
class HttpDriveApi : DriveApi {
    private fun call(method: String, url: String, token: String, body: ByteArray? = null, contentType: String? = null): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 15_000
            c.readTimeout = 60_000
            c.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", contentType)
                c.setFixedLengthStreamingMode(body.size)
                c.outputStream.use { it.write(body) }
            }
            val code = c.responseCode
            if (code !in 200..299) throw DriveLogic.httpError(code)
            return c.inputStream.use { it.readBytes() }
        } catch (e: IOException) {
            throw DriveException(DriveError.NETWORK, "No network. Check your connection.")
        } finally { c.disconnect() }
    }

    override suspend fun upload(token: String, name: String, bytes: ByteArray): DriveFile = withContext(Dispatchers.IO) {
        val boundary = "clrc" + java.util.UUID.randomUUID().toString().replace("-", "")
        val out = call("POST", DriveLogic.uploadUrl(), token, DriveLogic.multipartBody(name, bytes, boundary), "multipart/related; boundary=$boundary")
        DriveLogic.parseFile(Json.parseToJsonElement(out.toString(Charsets.UTF_8)).jsonObject)
    }

    override suspend fun list(token: String) = withContext(Dispatchers.IO) { DriveLogic.parseList(call("GET", DriveLogic.listUrl(), token).toString(Charsets.UTF_8)) }
    override suspend fun download(token: String, id: String) = withContext(Dispatchers.IO) { call("GET", DriveLogic.downloadUrl(id), token) }
    override suspend fun delete(token: String, id: String) = withContext(Dispatchers.IO) { call("DELETE", DriveLogic.deleteUrl(id), token); Unit }

    /** Best effort: ends the grant at Google so the next sign-in asks again. */
    fun revoke(token: String) {
        runCatching {
            val c = URL("https://oauth2.googleapis.com/revoke?token=" + URLEncoder.encode(token, "UTF-8")).openConnection() as HttpURLConnection
            try { c.requestMethod = "POST"; c.connectTimeout = 10_000; c.readTimeout = 10_000; c.responseCode } finally { c.disconnect() }
        }
    }
}
