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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import life.mygig.clauderc.api.ApiException
import life.mygig.clauderc.api.AskQuestion
import life.mygig.clauderc.api.ChatCommand
import life.mygig.clauderc.api.ChatData
import life.mygig.clauderc.api.ChatLogEntry
import life.mygig.clauderc.api.LauncherApi
import life.mygig.clauderc.api.PinStatus
import life.mygig.clauderc.api.Permissions
import life.mygig.clauderc.data.ChatPinVault
import life.mygig.clauderc.data.cached
import life.mygig.clauderc.data.chatDraftsStore
import life.mygig.clauderc.data.chatVoiceStore
import life.mygig.clauderc.ui.friendly
import life.mygig.clauderc.ui.screens.ChatImage

/** Server answers that mean the PIN is wrong or the chat locked: retrying won't help. */
private val PIN_CODES = setOf("wrong_pin", "chat_locked", "pin_not_set")

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
        if (_chatSession.value != session) cancelAttachments()   // another chat: its uploads don't belong here
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
        cancelAttachments()
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
        // The grace window covers one trip out (picker, Save), not later ones: if it ran out while we were away, lock now.
        val expired = externalUntil != 0L && SystemClock.elapsedRealtime() >= externalUntil
        externalUntil = 0L
        if (expired) lockChat()
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
                    if (e.code in PIN_CODES) { forgetChatPin(); _chatError.value = friendly(e).first; break }
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

    /** The open chat's permission rules (user, project and local settings), loaded when its Permissions sheet opens. */
    private val _permissions = MutableStateFlow<Permissions?>(null)
    val permissions = _permissions.asStateFlow()
    private val _permissionsOpen = MutableStateFlow(false)
    val permissionsOpen = _permissionsOpen.asStateFlow()
    fun openPermissions() { _permissionsOpen.value = true }
    fun clearPermissions() { _permissions.value = null; _permissionsOpen.value = false }
    fun loadPermissions() {
        val session = _chatSession.value ?: return
        val pin = chatPin ?: return
        tools.action("Loading permissions…") { _permissions.value = api.permissionsGet(session, pin) }
    }
    /** One change (see permissionChange); the screen shows the file as the server saved it. */
    fun changePermissions(json: String) {
        val session = _chatSession.value ?: return
        val pin = chatPin ?: return
        tools.action("Saving…") {
            api.permissionsSet(session, pin, json)
            _permissions.value = api.permissionsGet(session, pin)
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

    /**
     * Clears the box and shows the message as pending at once; the SSH round trip takes seconds. A failure puts the text back.
     * Ready attachments go along as ` [attached: path]` after the text (in pick order) and leave the strip; a failure puts them back.
     * Not while an attachment is still uploading (the Send button is off then too).
     */
    fun sendChat(text: String, onSent: () -> Unit, onFail: () -> Unit = {}) {
        val session = _chatSession.value ?: return
        val pin = chatPin ?: return
        val items = _attachments.value
        if (AttachQueue.active(items) > 0) { tools.say("Wait until the attachments have uploaded."); return }
        val sent = items.filter { it.state == AttachState.Ready && it.path != null }
        val sentIds = sent.mapTo(HashSet()) { it.id }
        val message = composeMessage(text, sent.mapNotNull { it.path })
        val t = message.trim()
        if (t.isEmpty()) return
        if (t == "/permissions") { onSent(); openPermissions(); return }   // our own screen instead of the terminal menu
        val tracked = !t.startsWith("/")   // a slash command never shows up as a message, so it would stay "queued" for good
        onSent()
        if (sent.isNotEmpty()) updateAttachments { AttachQueue.removeAll(it, sentIds) }
        if (tracked) _chatPending.value = _chatPending.value + t
        tools.action("Sending…", onError = { e ->
            if (tracked) _chatPending.value = _chatPending.value.toMutableList().also { it.remove(t) }
            // Back in the strip, ahead of anything picked since (unless the chat changed meanwhile).
            if (sent.isNotEmpty() && _chatSession.value == session) updateAttachments { sent + it.filterNot { a -> a.id in sentIds } }
            onFail()
            tools.report(e)
        }) {
            api.chatSend(session, pin, message)
            delay(800)
            setChat(session, api.chatHistory(session, pin))
        }
    }

    /** Drops a message stuck as "queued" (it only hides it here; nothing is unsent). */
    fun dismissPending(text: String) { _chatPending.value = _chatPending.value.toMutableList().also { it.remove(text) } }

    /** New history; drop pending messages the conversation now shows. */
    private fun setChat(session: String, c: ChatData) {
        if (session != _chatSession.value) return   // a late reply from the chat we just left
        if (chatPin == null) return   // locked while this reply was on its way: it must not reappear
        _chat.value = c
        // Compare loosely: Claude Code may change spacing, and the server cuts long messages at 8000 characters.
        fun norm(t: String) = t.trim().replace(Regex("\\s+"), " ").take(7000)
        val shown = c.messages.filter { it.role == "user" }.map { norm(it.text) }.toSet()
        _chatPending.value = _chatPending.value.filterNot { norm(it) in shown }
    }

    // ---- Attachments ------------------------------------------------------------------
    // Picked files upload at once, UPLOAD_PARALLEL at a time, and wait in a strip above the text box
    // until the message is sent. They live in memory only and are NOT kept across process death
    // (the picker's read grants end with the activity anyway); only the typed text draft is saved.

    private val _attachments = MutableStateFlow<List<Attachment>>(emptyList())
    /** The open chat's picked files, in pick order, with their upload state. */
    val attachments = _attachments.asStateFlow()
    private val uploadSlots = Semaphore(UPLOAD_PARALLEL)
    private val uploadJobs = mutableMapOf<Long, Job>()
    private var nextAttachId = 1L
    private var lastUploadReport = 0L
    /** Kept so a retry can read the file again (a ContentResolver, not the activity). Dropped when the chat closes. */
    private var uploadResolver: android.content.ContentResolver? = null

    private fun updateAttachments(f: (List<Attachment>) -> List<Attachment>) { _attachments.value = f(_attachments.value) }

    /** Picked files (any number): each goes into the strip and starts uploading to the project's uploads/ folder right away. */
    fun attachFiles(resolver: android.content.ContentResolver, uris: List<android.net.Uri>) {
        val session = _chatSession.value ?: return
        if (uris.isEmpty()) return
        if (chatPin == null) { tools.say("Unlock the chat to attach files."); return }
        uploadResolver = resolver
        tools.scope.launch {
            val metas = withContext(Dispatchers.IO) { uris.map { it to ChatImage.meta(resolver, it) } }
            if (_chatSession.value != session) return@launch   // the chat closed while we looked them up
            val taken = _attachments.value.map { it.base }.toMutableList()
            val added = metas.map { (uri, m) ->
                val (name, mime, size) = m
                val base = uniqueBase(splitName(name).first, taken).also { taken += it }
                Attachment(nextAttachId++, uri.toString(), name, base, mime, size, image = mime?.startsWith("image/") == true)
            }
            updateAttachments { AttachQueue.add(it, added) }
            added.forEach { startUpload(it.id) }
        }
    }

    /** The × on an item: drops it and cancels its upload if that is still running. */
    fun removeAttachment(id: Long) {
        uploadJobs.remove(id)?.cancel()
        updateAttachments { AttachQueue.remove(it, id) }
    }

    /** Tap on a failed item. */
    fun retryAttachment(id: Long) {
        if (_attachments.value.none { it.id == id && it.state == AttachState.Failed }) return
        updateAttachments { AttachQueue.retry(it, id) }
        startUpload(id)
    }

    private fun cancelAttachments() {
        uploadJobs.values.forEach { it.cancel() }
        uploadJobs.clear()
        _attachments.value = emptyList()
        uploadResolver = null
    }

    private fun startUpload(id: Long) {
        val session = _chatSession.value ?: return
        val resolver = uploadResolver ?: return
        uploadJobs.remove(id)?.cancel()
        val job = tools.scope.launch {
            uploadSlots.withPermit {
                val item = _attachments.value.firstOrNull { it.id == id } ?: return@withPermit
                updateAttachments { AttachQueue.start(it, id) }
                // Read, scale and encode off the main thread; the semaphore keeps at most UPLOAD_PARALLEL files in memory.
                val ok = when (val p = withContext(Dispatchers.IO) { ChatImage.prepare(resolver, android.net.Uri.parse(item.uri), item) }) {
                    is ChatImage.Prepared.Bad -> { updateAttachments { AttachQueue.fail(it, id, p.reason) }; return@withPermit }
                    is ChatImage.Prepared.Ok -> p
                }
                val name = uploadName(item.base, ok.ext)
                val b64 = withContext(Dispatchers.Default) { android.util.Base64.encodeToString(ok.bytes, android.util.Base64.NO_WRAP) }
                var failure: ApiException? = null
                for (attempt in 1..3) {
                    val pin = chatPin
                    if (pin == null) { failure = ApiException("chat_locked", "The chat is locked: unlock it, then tap to retry."); break }
                    try {
                        val path = api.upload(session, pin, name, b64).path
                        updateAttachments { AttachQueue.finish(it, id, path) }
                        return@withPermit
                    } catch (e: ApiException) {
                        failure = e
                        if (e.code in PIN_CODES) break
                        if (attempt < 3) delay(800)
                    }
                }
                val e = failure ?: return@withPermit
                updateAttachments { AttachQueue.fail(it, id, friendly(e).first) }
                if (e.code == "wrong_pin" || e.code == "pin_not_set") { forgetChatPin(); return@withPermit }
                // One message for a batch that fails together (say the connection dropped), not one per photo.
                val now = SystemClock.elapsedRealtime()
                if (now - lastUploadReport > 5_000) { lastUploadReport = now; tools.report(e) }
            }
        }
        uploadJobs[id] = job
        job.invokeOnCompletion { tools.scope.launch { if (uploadJobs[id] === job) uploadJobs.remove(id) } }
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
