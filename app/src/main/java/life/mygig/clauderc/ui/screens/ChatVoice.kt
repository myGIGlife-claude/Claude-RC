package life.mygig.clauderc.ui.screens

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Reads Claude's replies aloud. A reply is spoken sentence by sentence, so Pause (stop) remembers
 * where it was and Play carries on from that sentence. [playing] is the key of the reply being read.
 */
class ChatVoice(context: Context, private val onFail: () -> Unit) {
    var ready by mutableStateOf(false); private set
    var playing by mutableStateOf<String?>(null); private set
    private val resume = mutableMapOf<String, Int>()
    private val count = mutableMapOf<String, Int>()
    private lateinit var tts: TextToSpeech

    init {
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
            } else onFail()
        }
    }

    private fun split(id: String) = id.substringBeforeLast('|') to (id.substringAfterLast('|').toIntOrNull() ?: 0)

    /** Speaks [text] under [key]. [flush]: cut off what's playing first (the Play button); else queue behind it. */
    fun speak(key: String, text: String, flush: Boolean, fromStart: Boolean = false) {
        if (!ready) return
        val parts = text.split(Regex("(?<=[.!?:])\\s+|\\n+")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return
        count[key] = parts.size
        val from = if (fromStart) 0 else (resume[key] ?: 0).takeIf { it < parts.size } ?: 0
        if (flush) tts.stop()
        parts.forEachIndexed { i, p -> if (i >= from) tts.speak(p, TextToSpeech.QUEUE_ADD, null, "$key|$i") }
    }

    fun pause() { tts.stop(); playing = null }

    fun shutdown() { tts.stop(); tts.shutdown() }
}
