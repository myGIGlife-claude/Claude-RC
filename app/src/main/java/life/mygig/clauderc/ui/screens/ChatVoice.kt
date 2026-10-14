package life.mygig.clauderc.ui.screens

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import java.util.concurrent.Executors
import life.mygig.clauderc.data.CachedPrefs
import life.mygig.clauderc.voice.NATURAL_DEFAULT_SID
import life.mygig.clauderc.voice.NaturalVoice
import life.mygig.clauderc.voice.firstPieces
import life.mygig.clauderc.voice.speechText

/**
 * Reads Claude's replies aloud. A reply is spoken sentence by sentence, so Pause (stop) remembers
 * where it was and Play carries on from that sentence. [playing] is the key of the reply being read.
 * With the natural voice downloaded and switched on (Settings › App › Voice) the sentences are made by
 * Kokoro on the phone; otherwise, or if that fails, the phone's own speech engine reads them.
 */
class ChatVoice(private val context: Context, private val prefs: CachedPrefs, private val onFail: (String) -> Unit) {
    var ready by mutableStateOf(false); private set
    var playing by mutableStateOf<String?>(null); private set
    /** What the natural voice last did (load time, speed) or why it failed: shown in Settings › App › Voice. */
    var note by mutableStateOf(""); private set
    private val resume = mutableMapOf<String, Int>()
    private val count = mutableMapOf<String, Int>()
    private lateinit var tts: TextToSpeech
    private val worker = Executors.newSingleThreadExecutor()

    init {
        if (prefs.getBoolean("natural", false) && NaturalVoice.isInstalled(context)) {
            ready = true   // the natural voice doesn't need the phone's engine
            worker.execute { runCatching { NaturalVoice.engine(context).generate("Hi.", prefs.getInt("sid", NATURAL_DEFAULT_SID), 1.0f) } }   // load and warm the model now: the first run is the slow one, so the first Play shouldn't pay for it
        }
        tts = TextToSpeech(context) { st ->
            if (st == TextToSpeech.SUCCESS) {
                tts.language = Locale.getDefault()
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String) { val (k, i) = split(id); playing = k; resume[k] = i }
                    override fun onDone(id: String) {
                        val (k, i) = split(id)
                        if (i == (count[k] ?: 0) - 1) { resume.remove(k); if (playing == k) playing = null }
                    }
                    @Deprecated("Deprecated in Java") override fun onError(id: String) { if (playing == split(id).first) playing = null }
                })
                ready = true
            } else if (!ready) onFail("No text-to-speech voice on this phone.")
        }
    }

    private fun split(id: String) = id.substringBeforeLast('|') to (id.substringAfterLast('|').toIntOrNull() ?: 0)

    private fun naturalOn() = prefs.getBoolean("natural", false) && NaturalVoice.isInstalled(context) && !naturalBroken

    /** Speaks [text] under [key]. [flush]: cut off what's playing first (the Play button); else queue behind it. */
    fun speak(key: String, text: String, flush: Boolean, fromStart: Boolean = false) {
        val spoken = speechText(text)
        val natural = naturalOn()
        if (!ready && !natural) return
        // Android's speech engine refuses one piece over getMaxSpeechInputLength() (4000): a stretch with no full stop is cut into smaller ones.
        val parts = spoken.split(Regex("(?<=[.!?:])\\s+|\\n+")).filter { it.isNotBlank() }.let { ss -> ss.mapIndexed { i, x -> if (i == 0) firstPieces(x) else x.chunked(3000) }.flatten() }
        if (parts.isEmpty()) return
        count[key] = parts.size
        val from = if (fromStart) 0 else (resume[key] ?: 0).takeIf { it < parts.size } ?: 0
        if (natural) { speakNatural(key, parts, from, flush); return }
        if (flush) tts.stop()
        parts.forEachIndexed { i, p -> if (i >= from) tts.speak(p, TextToSpeech.QUEUE_ADD, null, "$key|$i") }
    }

    // --- natural voice ---------------------------------------------------------------------------------------------
    /** Bumped by every flush and pause: work queued under an older number is dropped. */
    @Volatile private var generation = 0
    @Volatile private var naturalBroken = false
    @Volatile private var track: AudioTrack? = null
    @Volatile private var written = 0L
    private val starts = mutableListOf<Triple<String, Int, Long>>()   // (reply, sentence, first frame of it)

    private fun newTrack(rate: Int): AudioTrack {
        // 16-bit samples (every phone plays them) and about 2 s of buffer: write() waits when it is full.
        val bytes = maxOf(AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) * 4, rate * 2 * 2)
        return AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(bytes).setTransferMode(AudioTrack.MODE_STREAM).build()
    }

    /** One made piece of speech on its way to the speaker; [pcm] null marks the end of a reply. */
    private class Chunk(val gen: Int, val key: String, val index: Int, val pcm: ShortArray?, val rate: Int)

    /** Made ahead of playback: while one sentence plays the next ones are being made, so commas and full stops don't wait. */
    private val queue = java.util.concurrent.LinkedBlockingQueue<Chunk>(6)
    private val player = Executors.newSingleThreadExecutor()
    @Volatile private var closed = false
    private var playerRunning = false

    private fun enqueue(c: Chunk): Boolean {
        while (c.gen == generation && !closed) if (queue.offer(c, 200, java.util.concurrent.TimeUnit.MILLISECONDS)) return true
        return false
    }

    private fun startPlayer() {
        if (playerRunning) return
        playerRunning = true
        player.execute {
            while (!closed) {
                val c = queue.poll(200, java.util.concurrent.TimeUnit.MILLISECONDS) ?: continue
                if (c.gen != generation) continue
                try {
                    val pcm = c.pcm
                    if (pcm == null) {   // the reply is all queued: wait until it is heard, then mark it done
                        val t = track
                        while (t != null && c.gen == generation && !closed && t.playbackHeadPosition < written) Thread.sleep(100)
                        if (c.gen == generation) { resume.remove(c.key); if (playing == c.key) playing = null }
                        continue
                    }
                    val t = track ?: newTrack(c.rate).also { track = it; written = 0; synchronized(starts) { starts.clear() }; it.play() }
                    synchronized(starts) { starts += Triple(c.key, c.index, written) }
                    var off = 0
                    while (off < pcm.size && c.gen == generation) {
                        val n = t.write(pcm, off, pcm.size - off, AudioTrack.WRITE_BLOCKING)
                        if (n < 0) break
                        off += n
                    }
                    if (c.gen == generation) written += pcm.size
                } catch (e: Throwable) {
                    note = "Playback failed: $e"
                }
            }
        }
    }

    private fun speakNatural(key: String, parts: List<String>, from: Int, flush: Boolean) {
        if (flush) stopNatural()
        startPlayer()
        val gen = generation
        val sid = prefs.getInt("sid", NATURAL_DEFAULT_SID)
        playing = key
        worker.execute {
            try {
                val t0 = System.nanoTime()
                note = "Loading the voice…"
                val engine = NaturalVoice.engine(context)
                val loaded = (System.nanoTime() - t0) / 1_000_000
                for (i in from until parts.size) {
                    if (gen != generation) return@execute
                    val t1 = System.nanoTime()
                    val audio = engine.generate(parts[i], sid, 1.0f)
                    val made = (System.nanoTime() - t1) / 1_000_000
                    val secs = audio.samples.size.toDouble() / audio.sampleRate
                    note = "Voice loaded in ${loaded / 1000.0} s; last piece: ${"%.1f".format(secs)} s of speech made in ${made / 1000.0} s"
                    val pcm = ShortArray(audio.samples.size) { (audio.samples[it].coerceIn(-1f, 1f) * 32767f).toInt().toShort() }
                    if (!enqueue(Chunk(gen, key, i, pcm, audio.sampleRate))) return@execute
                }
                enqueue(Chunk(gen, key, parts.size, null, 0))
            } catch (e: Throwable) {
                // The engine or the files are broken: fall back to the phone's voice for good (until the app restarts).
                naturalBroken = true
                note = "Failed: $e"
                if (gen == generation) { playing = null; onFail("The natural voice couldn't start, so the phone's voice is used. Try deleting and downloading it again in Settings › App › Voice.") }
            }
        }
    }

    /** Silences the natural voice and remembers which sentence of which reply was being heard. */
    private fun stopNatural() {
        generation++
        queue.clear()
        val t = track ?: return
        val head = t.playbackHeadPosition.toLong()
        synchronized(starts) {
            starts.lastOrNull { it.third <= head }?.let { (k, i, _) -> resume[k] = i }
            starts.clear()
        }
        runCatching { t.pause(); t.flush(); t.release() }
        track = null
        written = 0
    }

    fun pause() { tts.stop(); stopNatural(); playing = null }

    fun shutdown() {
        tts.stop(); tts.shutdown()
        closed = true
        stopNatural()
        worker.execute { NaturalVoice.release() }
        worker.shutdown()
        player.shutdown()
    }
}
