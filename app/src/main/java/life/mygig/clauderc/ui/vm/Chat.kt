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
import life.mygig.clauderc.api.CHAT_WATCH_MIN_SCRIPT_API
import life.mygig.clauderc.api.Codes
import life.mygig.clauderc.api.ChatCommand
import life.mygig.clauderc.api.ChatData
import life.mygig.clauderc.api.ChatLogEntry
import life.mygig.clauderc.api.LauncherApi
import life.mygig.clauderc.api.Permissions
import life.mygig.clauderc.data.cached
import life.mygig.clauderc.data.chatDraftsStore
import life.mygig.clauderc.data.chatVoiceStore
import life.mygig.clauderc.data.deleteOldChatPin
import life.mygig.clauderc.ui.friendly
import life.mygig.clauderc.ui.screens.ChatImage

/**
 * In-app chat, behind App lock only (no chat PIN any more). The history poll runs only while
 * the chat is on screen ([setOnScreen], from ChatScreen) and the app is in front ([pauseChat] /
 * [resumeChat], from MainActivity's onStop/onStart).
 * [onClose] clears what other groups keep for the open chat (its workers, its hosts).
 */
class ChatController(
    app: Application,
    private val api: LauncherApi,
    private val tools: VmTools,
    private val scriptApi: () -> Int,
    private val onClose: () -> Unit,
) {
    init {
        tools.scope.launch(Dispatchers.IO) { deleteOldChatPin(app) }   // the old PIN copy: delete, never read
    }

    private val _chatSession = MutableStateFlow<String?>(null)
    val chatSession = _chatSession.asStateFlow()
    private val _chatError = MutableStateFlow<String?>(null)
    val chatError = _chatError.asStateFlow()
    private val _chat = MutableStateFlow<ChatData?>(null)
    val chat = _chat.asStateFlow()
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

    /**
     * session -> id of the last message when /clear was typed. Claude Code only starts the new conversation file on the next
     * prompt, so until then the history still holds the old chat: hide everything up to that message (the marker is dropped
     * as soon as the history no longer contains it, i.e. the new conversation has begun).
     */
    private val clearedAfter = HashMap<String, String>()

    fun openChat(session: String) {
        if (_chatSession.value != session) cancelAttachments()   // another chat: its uploads don't belong here
        _chatSession.value = session
        _chatPending.value = queuedBySession[session].orEmpty()
        _chat.value = null
        _chatCommands.value = emptyList()
        _chatError.value = null
        chatPoller?.cancel()
        updatePolling()   // a chat already on screen switches here; a new one starts when ChatScreen shows
    }

    /** Files Claude sent, kept in memory while the chat is open (about 24 MB). */
    private val fileCache = object : android.util.LruCache<String, ByteArray>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray) = value.size
    }

    suspend fun chatFileBytes(path: String): ByteArray {
        fileCache.get(path)?.let { return it }
        val session = _chatSession.value ?: throw IllegalStateException("The chat is closed")
        val f = try { api.chatFile(session, path) } catch (e: ApiException) { throw IllegalStateException(friendly(e).first) }
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
        onClose()
    }

    /** ChatScreen is composed (not hidden behind App lock or gone): the history poll may run. */
    private var onScreen = false
    /** The app is in front (MainActivity onStart/onStop). */
    private var foreground = true

    fun setOnScreen(shown: Boolean) { onScreen = shown; updatePolling() }

    /** App went to the background: stop polling (uploads carry on). */
    fun pauseChat() { foreground = false; chatPoller?.cancel() }

    /** Back in front: refresh at once and carry on polling, if the chat is still on screen. */
    fun resumeChat() { foreground = true; updatePolling() }

    /** Starts the poll when the open chat is on screen and the app in front, stops it otherwise. */
    private fun updatePolling() {
        if (onScreen && foreground && _chatSession.value != null) {
            if (chatPoller?.isActive != true) startChatPolling()
        } else chatPoller?.cancel()
    }

    private fun startChatPolling() {
        chatPoller?.cancel()
        val s0 = _chatSession.value
        // Opening or returning to a chat tells the queue the owner is here (it backs off that chat).
        if (s0 != null) tools.scope.launch { runCatching { api.chatOpen(s0) } }
        // Slash commands once per opened chat (openChat empties them), not on every return to the app.
        if (s0 != null && _chatCommands.value.isEmpty()) tools.scope.launch {
            runCatching { api.chatCommands(s0).commands }.getOrNull()?.let { if (_chatSession.value == s0) _chatCommands.value = it }
        }
        chatPoller = tools.scope.launch {
            var token = ""
            var legacy = scriptApi() < CHAT_WATCH_MIN_SCRIPT_API
            while (true) {
                val session = _chatSession.value ?: break
                var pause = 4_000L
                try {
                    if (legacy) {
                        setChat(session, api.chatHistory(session))
                        _chatError.value = null
                    } else {
                        val d = api.chatWatch(session, token)
                        _chatError.value = null
                        if (!d.retry) {
                            token = d.token
                            if (d.changed) setChat(session, d)
                        }
                        pause = watchDelayMs(d)
                    }
                } catch (e: ApiException) {
                    _chatError.value = friendly(e).first
                    // A server that doesn't know chat-watch refuses it: poll that chat the old way.
                    if (!legacy && e.code == Codes.FORBIDDEN) legacy = true
                }
                delay(if (legacy) (if (_chat.value?.busy == true) 2_000 else 4_000) else pause)   // old way: busy 2 s, else 4 s
            }
        }
    }

    /**
     * Answers Claude's question screen key by key: a number picks (and moves on), a multi-choice
     * list is toggled then Down to its Submit row, "something else" types the text.
     */
    fun answerAsk(qs: List<AskQuestion>, picks: List<Set<Int>>, others: List<String>) {
        val session = _chatSession.value ?: return
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
                        api.chatSend(session, other)
                        delay(800)
                    }
                    else -> keys += ((picks[qi].firstOrNull() ?: 0) + 1).toString()
                }
            }
            flush()
            delay(800)
            var h = api.chatHistory(session)
            // A "Review your answers" screen after several questions: the first entry is Submit.
            if (h.screen?.contains("Submit answers") == true) {
                api.keys(session, "Enter")
                delay(500)
                h = api.chatHistory(session)
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
        tools.action("Loading permissions…") { _permissions.value = api.permissionsGet(session) }
    }
    /** One change (see permissionChange); the screen shows the file as the server saved it. */
    fun changePermissions(json: String) {
        val session = _chatSession.value ?: return
        tools.action("Saving…") {
            api.permissionsSet(session, json)
            _permissions.value = api.permissionsGet(session)
        }
    }

    /** A slash command like /model: it never shows as a message, so it isn't tracked as pending. */
    fun chatCommand(text: String) {
        val session = _chatSession.value ?: return
        tools.action("Sending $text…") {
            api.chatSend(session, text)
            delay(1500)
            setChat(session, api.chatHistory(session))
        }
    }

    /**
     * Clears the box and shows the message as pending at once; the SSH round trip takes seconds. A failure puts the text back.
     * Ready attachments go along as ` [attached: path]` after the text (in pick order) and leave the strip; a failure puts them back.
     * Not while an attachment is still uploading (the Send button is off then too).
     */
    fun sendChat(text: String, onSent: () -> Unit, onFail: () -> Unit = {}) {
        val session = _chatSession.value ?: return
        val items = _attachments.value
        if (AttachQueue.active(items) > 0) { tools.say("Wait until the attachments have uploaded."); return }
        val sent = items.filter { it.state == AttachState.Ready && it.path != null }
        val sentIds = sent.mapTo(HashSet()) { it.id }
        val message = composeMessage(text, sent.mapNotNull { it.path })
        val t = message.trim()
        if (t.isEmpty()) return
        if (t == "/permissions") { onSent(); openPermissions(); return }   // our own screen instead of the terminal menu
        val tracked = !t.startsWith("/")   // a slash command never shows up as a message, so it would stay "queued" for good
        val clearing = t == "/clear" || t == "/reset" || t == "/new"
        if (clearing) {
            _chat.value?.messages?.lastOrNull { it.id.isNotEmpty() }?.id?.let { clearedAfter[session] = it }
            _chat.value = _chat.value?.copy(messages = emptyList())
        }
        onSent()
        if (sent.isNotEmpty()) updateAttachments { AttachQueue.removeAll(it, sentIds) }
        if (tracked) _chatPending.value = _chatPending.value + t
        tools.action("Sending…", onError = { e ->
            if (tracked) _chatPending.value = _chatPending.value.toMutableList().also { it.remove(t) }
            if (clearing) clearedAfter.remove(session)   // the clear didn't go out: show the chat again on the next refresh
            // Back in the strip, ahead of anything picked since (unless the chat changed meanwhile).
            if (sent.isNotEmpty() && _chatSession.value == session) updateAttachments { sent + it.filterNot { a -> a.id in sentIds } }
            onFail()
            tools.report(e)
        }) {
            api.chatSend(session, message)
            delay(800)
            setChat(session, api.chatHistory(session))
        }
    }

    /** Asks session [to] a question through this chat: Claude here relays it (ListAgents + SendMessage). False when nothing was sent. */
    fun askSession(to: String, question: String, onSent: () -> Unit = {}): Boolean {
        val from = _chatSession.value ?: return false
        if (to == from) return false
        val msg = askMessage(from, to, question)
        if (msg.isEmpty()) return false
        sendChat(msg, onSent)
        return true
    }

    /** Drops a message stuck as "queued" (it only hides it here; nothing is unsent). */
    fun dismissPending(text: String) { _chatPending.value = _chatPending.value.toMutableList().also { it.remove(text) } }

    /** New history; drop pending messages the conversation now shows. */
    private fun setChat(session: String, c: ChatData) {
        if (session != _chatSession.value) return   // a late reply from the chat we just left
        _chat.value = c.withoutCleared(clearedAfter, session)
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
                    try {
                        val path = api.upload(session, name, b64).path
                        updateAttachments { AttachQueue.finish(it, id, path) }
                        return@withPermit
                    } catch (e: ApiException) {
                        failure = e
                        if (attempt < 3) delay(800)
                    }
                }
                val e = failure ?: return@withPermit
                updateAttachments { AttachQueue.fail(it, id, friendly(e).first) }
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
        tools.action("Stopping…") { api.chatInterrupt(session) }
    }

    /** Answer a question in the chat's session (keys, like the log's). */
    fun chatKey(key: String) {
        val session = _chatSession.value ?: return
        tools.action("Sending $key…") { api.keys(session, key); _chat.value = api.chatHistory(session) }
    }

    /** Another server: session names repeat across servers, so nothing of the old one's chats may stay. */
    fun clearServerState() {
        _chatLog.value = null
        closeChat()
        queuedBySession.clear()
        draftPrefs.edit { it.clear() }
    }
}

/** [c] without the messages up to the /clear marker for [session] (the marker is forgotten once the history no longer has it). */
internal fun ChatData.withoutCleared(marks: MutableMap<String, String>, session: String): ChatData {
    val id = marks[session] ?: return this
    val i = messages.indexOfLast { it.id == id }
    if (i < 0) { marks.remove(session); return this }
    return copy(messages = messages.drop(i + 1))
}

/** Pause after a chat-watch answer: slots busy = 4 s; a change = 1.5 s before the next watch; nothing new = straight back. */
internal fun watchDelayMs(d: ChatData): Long = if (d.retry) 4_000 else if (d.changed) 1_500 else 0

/** The longest chat message the app sends. */
private const val ASK_MAX = 20000

/** The message that makes Claude in session [from] relay [question] to session [to]. Empty when the question is blank; the whole text stays under [ASK_MAX]. */
internal fun askMessage(from: String, to: String, question: String): String {
    val q = question.trim()
    if (q.isEmpty()) return ""
    fun build(text: String) = "Ask the session \"$to\" this on my behalf. " +
        "Use ListAgents to find the live session whose tmux session or project is \"$to\" (not this one), then SendMessage it. " +
        "First line of the message: \"Question from Jonathan via session $from\". " +
        "Second line: \"Reason: Jonathan asked me to relay this question.\" Then the question. " +
        "If it isn't listed, or the send is held for approval, tell me that and stop. " +
        "When the reply arrives, show it to me in full.\nQuestion: $text"
    return build(q.take(ASK_MAX - build("").length))
}
