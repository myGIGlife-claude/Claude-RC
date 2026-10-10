package life.mygig.clauderc.ui.vm

import android.app.Application
import android.os.SystemClock
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import life.mygig.clauderc.api.ApiException
import life.mygig.clauderc.api.AskQuestion
import life.mygig.clauderc.api.ChatCommand
import life.mygig.clauderc.api.ChatData
import life.mygig.clauderc.api.ChatLogEntry
import life.mygig.clauderc.api.LauncherApi
import life.mygig.clauderc.api.PinStatus
import life.mygig.clauderc.data.ChatPinVault
import life.mygig.clauderc.data.cached
import life.mygig.clauderc.data.chatDraftsStore
import life.mygig.clauderc.data.chatVoiceStore
import life.mygig.clauderc.ui.friendly

/**
 * In-app chat. The PIN is kept only in memory while the chat is unlocked, and forgotten the
 * moment the app goes to the background (lockChat, from MainActivity.onStop),
 * unless you chose "don't ask again for" a while: then it's kept encrypted in
 * ChatPinVault until that runs out or the phone restarts.
 * [onClose] clears what other groups keep for the open chat (its workers, its hosts).
 */
class ChatController(
    app: Application,
    private val api: LauncherApi,
    private val tools: VmTools,
    private val onClose: () -> Unit,
) {
    private val _chatSession = MutableStateFlow<String?>(null)
    val chatSession = _chatSession.asStateFlow()
    private val _chatPinNeeded = MutableStateFlow<PinStatus?>(null)
    val chatPinNeeded = _chatPinNeeded.asStateFlow()
    private val _chatError = MutableStateFlow<String?>(null)
    val chatError = _chatError.asStateFlow()
    private val _chat = MutableStateFlow<ChatData?>(null)
    val chat = _chat.asStateFlow()
    private var chatPin: String? = null
    private val pinVault = ChatPinVault(app)
    /** Messages you sent that the conversation file doesn't show yet (Claude queues them while it works). */
    private val _chatCommands = MutableStateFlow<List<ChatCommand>>(emptyList())
    /** Slash commands from your skills, commands and plugins, for the suggestions above the chat box. */
    val chatCommands = _chatCommands.asStateFlow()

    private val _chatPending = MutableStateFlow<List<String>>(emptyList())
    val chatPending = _chatPending.asStateFlow()
    /** Pending messages of chats you left: they come back when you reopen that chat (until the conversation shows them). */
    private val queuedBySession = mutableMapOf<String, List<String>>()

    /** Half-typed chat messages, kept per session so leaving the chat or the app doesn't lose them. */
    private val draftPrefs = app.chatDraftsStore.cached()
    fun draft(session: String): String = draftPrefs.get()[stringPreferencesKey(session)].orEmpty()
    fun setDraft(session: String, text: String) = draftPrefs.edit { p ->
        val k = stringPreferencesKey(session)
        if (text.isEmpty()) { p.remove(k) } else { p[k] = text }
    }
    /** The chat's speaker switch: stays as the owner left it, across chats and app restarts. */
    val voicePrefs = app.chatVoiceStore.cached()
    private var chatPoller: Job? = null

    fun openChat(session: String) {
        _chatSession.value = session
        _chatPending.value = queuedBySession[session].orEmpty()
        _chat.value = null
        _chatCommands.value = emptyList()
        _chatError.value = null
        if (chatPin == null) chatPin = pinVault.load()
        if (chatPin != null) { startChatPolling(); return }
        tools.scope.launch {
            _chatPinNeeded.value = runCatching { api.chatPinStatus() }.getOrElse { e ->
                _chatError.value = (e as? ApiException)?.let { friendly(it).first } ?: e.message
                PinStatus(set = true)
            }
        }
    }

    /** Files Claude sent, kept in memory while the chat is open (about 24 MB). */
    private val fileCache = object : android.util.LruCache<String, ByteArray>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray) = value.size
    }

    suspend fun chatFileBytes(path: String): ByteArray {
        fileCache.get(path)?.let { return it }
        val session = _chatSession.value ?: throw IllegalStateException("The chat is closed")
        val pin = chatPin ?: throw IllegalStateException("The chat is locked")
        val f = try { api.chatFile(session, pin, path) } catch (e: ApiException) { throw IllegalStateException(friendly(e).first) }
        // Megabytes of text: decode off the main thread.
        return withContext(Dispatchers.Default) { android.util.Base64.decode(f.data, android.util.Base64.DEFAULT) }.also { fileCache.put(path, it) }
    }

    fun closeChat() {
        fileCache.evictAll()
        chatPoller?.cancel()
        _chatSession.value?.let { queuedBySession[it] = _chatPending.value }
        _chatPending.value = emptyList()
        _chatSession.value = null
        _chat.value = null
        _chatPinNeeded.value = null
        onClose()
    }

    /** App went to the background: forget the PIN; the chat asks again on return. */
    /** A system screen the chat opened (file picker, Save, voice): leaving for it isn't "leaving the app". */
    private var externalUntil = 0L
    fun externalScreen() { externalUntil = SystemClock.elapsedRealtime() + 120_000 }

    fun lockChat() {
        chatPoller?.cancel()
        if (SystemClock.elapsedRealtime() < externalUntil) return
        if (chatPin != null && pinVault.load() != null) return
        chatPin = null
        if (_chatSession.value != null) { _chat.value = null; _chatPinNeeded.value = PinStatus(set = true) }
    }

    /** The server refused the PIN: drop it and ask again, even inside the picker grace window. */
    private fun forgetChatPin() {
        pinVault.clear()
        chatPin = null
        if (_chatSession.value != null) { _chat.value = null; _chatPinNeeded.value = PinStatus(set = true) }
    }

    /** Back in front while still unlocked: carry on polling. */
    fun resumeChat() {
        externalUntil = 0L   // the grace window covers one trip out (picker, Save), not later ones
        if (chatPin != null && _chatSession.value != null && _chatPinNeeded.value == null) startChatPolling()
    }

    fun setChatPin(newPin: String) = tools.scope.launch {
        _chatError.value = null
        try {
            api.chatPinSet(newPin)
            unlockChat(newPin)
        } catch (e: ApiException) { _chatError.value = friendly(e).first }
    }

    /** [keepMinutes] > 0: don't ask again for that long, even after leaving the app. */
    fun unlockChat(pin: String, keepMinutes: Int = 0) = tools.scope.launch {
        val session = _chatSession.value ?: return@launch
        _chatError.value = null
        try {
            api.chatOpen(session, pin)
            chatPin = pin
            if (keepMinutes > 0) pinVault.save(pin, keepMinutes) else pinVault.clear()
            _chatPinNeeded.value = null
            startChatPolling()
        } catch (e: ApiException) { _chatError.value = friendly(e).first }
    }

    private fun startChatPolling() {
        chatPoller?.cancel()
        val s0 = _chatSession.value; val p0 = chatPin
        if (s0 != null && p0 != null) tools.scope.launch {
            runCatching { api.chatCommands(s0, p0).commands }.getOrNull()?.let { if (_chatSession.value == s0) _chatCommands.value = it }
        }
        chatPoller = tools.scope.launch {
            while (true) {
                val session = _chatSession.value ?: break
                val pin = chatPin ?: break
                try {
                    setChat(session, api.chatHistory(session, pin))
                    _chatError.value = null
                } catch (e: ApiException) {
                    if (e.code in setOf("wrong_pin", "chat_locked", "pin_not_set")) { forgetChatPin(); _chatError.value = friendly(e).first; break }
                    _chatError.value = friendly(e).first
                }
                delay(if (_chat.value?.busy == true) 2_000 else 4_000)
            }
        }
    }

    /**
     * Answers Claude's question screen key by key: a number picks (and moves on), a multi-choice
     * list is toggled then Down to its Submit row, "something else" types the text.
     */
    fun answerAsk(qs: List<AskQuestion>, picks: List<Set<Int>>, others: List<String>) {
        val session = _chatSession.value ?: return
        val pin = chatPin ?: return
        tools.action("Sending your answers…") {
            val keys = mutableListOf<String>()
            suspend fun flush() {
                keys.chunked(5).forEach { api.keys(session, *it.toTypedArray()) }
                keys.clear()
            }
            qs.forEachIndexed { qi, q ->
                val n = q.options.size
                val other = others[qi].trim()
                when {
                    q.multiSelect -> {
                        picks[qi].sorted().forEach { keys += (it + 1).toString() }
                        repeat(n + 1) { keys += "Down" }
                        keys += "Enter"
                    }
                    other.isNotEmpty() -> {
                        keys += (n + 1).toString()
                        flush()
                        api.chatSend(session, pin, other)
                        delay(800)
                    }
                    else -> keys += ((picks[qi].firstOrNull() ?: 0) + 1).toString()
                }
            }
            flush()
            delay(800)
            var h = api.chatHistory(session, pin)
            // A "Review your answers" screen after several questions: the first entry is Submit.
            if (h.screen?.contains("Submit answers") == true) {
                api.keys(session, "Enter")
                delay(500)
                h = api.chatHistory(session, pin)
            }
            setChat(session, h)
        }
    }

    /** A slash command like /model: it never shows as a message, so it isn't tracked as pending. */
    fun chatCommand(text: String) {
        val session = _chatSession.value ?: return
        val pin = chatPin ?: return
        tools.action("Sending $text…") {
            api.chatSend(session, pin, text)
            delay(1500)
            setChat(session, api.chatHistory(session, pin))
        }
    }

    /** Clears the box and shows the message as pending at once; the SSH round trip takes seconds. A failure puts the text back. */
    fun sendChat(text: String, onSent: () -> Unit, onFail: () -> Unit = {}) {
        val session = _chatSession.value ?: return
        val pin = chatPin ?: return
        val t = text.trim()
        val tracked = !t.startsWith("/")   // a slash command never shows up as a message, so it would stay "queued" for good
        onSent()
        if (tracked) _chatPending.value = _chatPending.value + t
        tools.action("Sending…", onError = { e ->
            if (tracked) _chatPending.value = _chatPending.value.toMutableList().also { it.remove(t) }
            onFail()
            tools.report(e)
        }) {
            api.chatSend(session, pin, text)
            delay(800)
            setChat(session, api.chatHistory(session, pin))
        }
    }

    /** Drops a message stuck as "queued" (it only hides it here; nothing is unsent). */
    fun dismissPending(text: String) { _chatPending.value = _chatPending.value.toMutableList().also { it.remove(text) } }

    /** New history; drop pending messages the conversation now shows. */
    private fun setChat(session: String, c: ChatData) {
        if (session != _chatSession.value) return   // a late reply from the chat we just left
        _chat.value = c
        // Compare loosely: Claude Code may change spacing, and the server cuts long messages at 8000 characters.
        fun norm(t: String) = t.trim().replace(Regex("\\s+"), " ").take(7000)
        val shown = c.messages.filter { it.role == "user" }.map { norm(it.text) }.toSet()
        _chatPending.value = _chatPending.value.filterNot { norm(it) in shown }
    }

    /** Send a file from the phone into the chat's project folder; [onDone] gets its path. */
    fun uploadToChat(name: String, bytes: ByteArray, onDone: (String) -> Unit) {
        val session = _chatSession.value ?: return
        val pin = chatPin ?: return
        tools.action("Sending $name…") {
            val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            onDone(api.upload(session, pin, name, b64).path)
        }
    }

    private val _chatLog = MutableStateFlow<List<ChatLogEntry>?>(null)
    val chatLog = _chatLog.asStateFlow()
    fun loadChatLog() = tools.action("Loading the chat log…") { _chatLog.value = api.chatLog() }
    fun clearChatLog() { _chatLog.value = null }

    fun interruptChat() {
        val session = _chatSession.value ?: return
        val pin = chatPin ?: return
        tools.action("Stopping…") { api.chatInterrupt(session, pin) }
    }

    /** Answer a question in the chat's session (keys, like the log's). */
    fun chatKey(key: String) {
        val session = _chatSession.value ?: return
        tools.action("Sending $key…") { api.keys(session, key); chatPin?.let { _chat.value = api.chatHistory(session, it) } }
    }

    /** Another server: session names repeat across servers, so nothing of the old one's chats may stay. */
    fun clearServerState() {
        _chatLog.value = null
        closeChat()
        queuedBySession.clear()
        draftPrefs.edit { it.clear() }
        chatPin = null
        pinVault.clear()
    }
}
