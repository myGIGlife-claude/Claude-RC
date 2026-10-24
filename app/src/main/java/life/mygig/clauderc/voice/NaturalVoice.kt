package life.mygig.clauderc.voice

import android.content.Context
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

/** One voice of the downloaded Kokoro model: its name in the picker and its speaker id in voices.bin (kokoro-multi-lang-v1_0). */
data class NaturalVoiceChoice(val sid: Int, val name: String)

val NATURAL_VOICES = listOf(
    NaturalVoiceChoice(3, "Heart"), NaturalVoiceChoice(2, "Bella"), NaturalVoiceChoice(6, "Nicole"), NaturalVoiceChoice(9, "Sarah"),
    NaturalVoiceChoice(10, "Sky"), NaturalVoiceChoice(7, "Nova"), NaturalVoiceChoice(21, "Emma (British)"), NaturalVoiceChoice(22, "Isabella (British)"),
)
const val NATURAL_DEFAULT_SID = 3

sealed interface NaturalDownload {
    data object Idle : NaturalDownload
    data class Running(val percent: Int) : NaturalDownload
    data class Failed(val message: String) : NaturalDownload
}

/**
 * The natural voice: Kokoro (Apache 2.0) run on the phone by sherpa-onnx. The model is a one-time download (about 333 MB)
 * from the sherpa-onnx releases, checked against a pinned SHA-256 and unpacked into the app's private storage.
 */
object NaturalVoice {
    private const val URL_MODEL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-multi-lang-v1_0.tar.bz2"
    private const val SHA256 = "c5f7e2d2caf082bc1d20fb70334a61d99d20b484500aad32e7cf84c128ea3298"
    private const val TOP = "kokoro-multi-lang-v1_0/"
    private const val EXPECTED_BYTES = 349_906_910L
    private const val NEEDED_BYTES = 700L * 1024 * 1024
    /** What speech needs from the archive; the Chinese dictionaries and the rest stay out. */
    private val WANTED = Regex("^(model\\.onnx|voices\\.bin|tokens\\.txt|lexicon-(us-en|gb-en|zh)\\.txt|espeak-ng-data/.+)$")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<NaturalDownload>(NaturalDownload.Idle)
    val state: StateFlow<NaturalDownload> = _state.asStateFlow()
    private val _installed = MutableStateFlow<Boolean?>(null)
    /** null until [refresh] has looked. */
    val installed: StateFlow<Boolean?> = _installed.asStateFlow()

    private fun dir(c: Context) = File(c.filesDir, "natural-voice")

    fun isInstalled(c: Context): Boolean = File(dir(c), "model.onnx").isFile && File(dir(c), "done").isFile

    fun refresh(c: Context) {
        _installed.value = isInstalled(c)
        cleanTmp(c)
    }

    /** A download that was interrupted leaves its half-unpacked folder (hundreds of MB): remove it unless one is running. */
    private fun cleanTmp(c: Context) { if (_state.value !is NaturalDownload.Running) File(c.applicationContext.filesDir, "natural-voice.tmp").deleteRecursively() }

    fun delete(c: Context) {
        dir(c).deleteRecursively()
        cleanTmp(c)
        _installed.value = false
    }

    /** Starts the download; progress shows in [state]. A second call while one runs does nothing. */
    fun download(c: Context) {
        if (_state.value is NaturalDownload.Running) return
        val app = c.applicationContext
        _state.value = NaturalDownload.Running(0)
        scope.launch {
            val tmp = File(app.filesDir, "natural-voice.tmp")
            try {
                if (app.filesDir.usableSpace < NEEDED_BYTES) error("Not enough free space: about 700 MB is needed.")
                tmp.deleteRecursively(); tmp.mkdirs()
                unpack(open(), tmp)
                File(tmp, "done").writeText("ok")
                dir(app).deleteRecursively()
                check(tmp.renameTo(dir(app))) { "Couldn't save the voice." }
                _installed.value = true
                _state.value = NaturalDownload.Idle
            } catch (e: Exception) {
                tmp.deleteRecursively()
                _state.value = NaturalDownload.Failed(e.message ?: "The download failed.")
            }
        }
    }

    private var length = 0L

    private fun open(): InputStream {
        val conn = URL(URL_MODEL).openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000; conn.readTimeout = 60_000
        check(conn.responseCode == 200) { "The download server answered ${conn.responseCode}." }
        // The CDN may answer without a Content-Length: then use the archive's known size for the progress bar.
        length = conn.contentLengthLong.takeIf { it > 0 } ?: EXPECTED_BYTES
        return CountingStream(BufferedInputStream(conn.inputStream, 1 shl 16))
    }

    /** Counts what came off the network, to show progress. */
    private class CountingStream(private val inner: InputStream) : InputStream() {
        @Volatile var n = 0L
        override fun read(): Int = inner.read().also { if (it >= 0) { n++; if (n and 0xFFFF == 0L) report() } }
        override fun read(b: ByteArray, off: Int, len: Int): Int = inner.read(b, off, len).also { if (it > 0) { n += it; report() } }
        override fun close() = inner.close()
        private var last = -1
        private fun report() {
            val pct = if (length > 0) (n * 100 / length).toInt().coerceIn(0, 99) else 0
            if (pct != last) { last = pct; _state.value = NaturalDownload.Running(pct) }
        }
    }

    /** Streams the archive through the checksum, bzip2 and tar, writing only [WANTED] files under [out]; a wrong checksum throws. */
    private fun unpack(net: InputStream, out: File) {
        val md = MessageDigest.getInstance("SHA-256")
        val root = out.canonicalPath + File.separator
        val digesting = DigestInputStream(net, md)
        TarArchiveInputStream(BZip2CompressorInputStream(BufferedInputStream(digesting, 1 shl 16))).use { tar ->
            while (true) {
                val e = tar.nextEntry ?: break
                val name = e.name.removePrefix("./").removePrefix(TOP)
                if (e.isDirectory || !e.name.removePrefix("./").startsWith(TOP) || !WANTED.matches(name)) continue
                val f = File(out, name)
                check(f.canonicalPath.startsWith(root)) { "The download holds an unsafe file name." }
                f.parentFile?.mkdirs()
                FileOutputStream(f).use { tar.copyTo(it) }
            }
            // tar and bzip2 stop before the last bytes of the download: read the rest so the checksum covers all of it.
            val rest = ByteArray(65536)
            while (digesting.read(rest) >= 0) { /* drain */ }
        }
        val got = md.digest().joinToString("") { "%02x".format(it) }
        check(got == SHA256) { "The download didn't match its checksum, so it was thrown away." }
    }

    /** Shared, because the speech engine takes seconds and hundreds of MB to load. Every [ChatVoice] calls [acquire] when made and [release] when done; the last one out frees it. */
    @Volatile private var engine: OfflineTts? = null
    private val lock = Any()
    private val genLock = Any()   // one sentence at a time through the native engine
    private var users = 0

    fun acquire() = synchronized(lock) { users++ }

    private fun engine(c: Context): OfflineTts = synchronized(lock) {
        engine ?: run {
            val d = dir(c.applicationContext).path
            val kokoro = OfflineTtsKokoroModelConfig(
                model = "$d/model.onnx", voices = "$d/voices.bin", tokens = "$d/tokens.txt", dataDir = "$d/espeak-ng-data",
                lexicon = "$d/lexicon-us-en.txt,$d/lexicon-zh.txt",
            )
            OfflineTts(null, OfflineTtsConfig(model = OfflineTtsModelConfig(kokoro = kokoro, numThreads = 4))).also { engine = it }
        }
    }

    /** Makes one piece of speech; loads the model first if nobody has yet. */
    fun generate(c: Context, text: String, sid: Int, speed: Float = 1.0f): GeneratedAudio = synchronized(genLock) { engine(c).generate(text, sid, speed) }

    /** One user is done. The engine is freed only when no [ChatVoice] is left and never while a sentence is being made. */
    fun release() = synchronized(genLock) {
        synchronized(lock) {
            if (users > 0) users--
            if (users == 0) { engine?.release(); engine = null }
        }
    }
}
