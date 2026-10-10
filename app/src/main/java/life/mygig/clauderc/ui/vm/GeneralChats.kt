package life.mygig.clauderc.ui.vm

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import life.mygig.clauderc.api.ApiException
import life.mygig.clauderc.api.GeneralChat
import life.mygig.clauderc.api.LauncherApi
import life.mygig.clauderc.api.Updates

/**
 * General chats: Claude conversations not tied to a repo, saved on the server (running or stopped).
 * A chat runs as session "chat-<id>"; [open] always starts it on the server before the chat view opens.
 * [scriptApi] is null until the server's status is known; [openChat] opens the usual chat overlay.
 */
class GeneralChatsController(
    private val api: LauncherApi,
    private val tools: VmTools,
    private val scriptApi: () -> Int?,
    private val openChat: (String) -> Unit,
    private val refreshSessions: () -> Unit,
) {
    private val _chats = MutableStateFlow<List<GeneralChat>>(emptyList())
    /** Newest activity first. */
    val chats = _chats.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()
    /** The first list has arrived (so an empty list means "no chats", not "loading"). */
    private val _loaded = MutableStateFlow(false)
    val loaded = _loaded.asStateFlow()
    /** The chat being started by [open], for the row's spinner. */
    private val _opening = MutableStateFlow<String?>(null)
    val opening = _opening.asStateFlow()

    /** Sessions tab: 0 = General chat, 1 = Code chat; kept while the app runs (the screen saves it too). */
    private val _tab = MutableStateFlow(0)
    val tab = _tab.asStateFlow()
    fun selectTab(t: Int) { _tab.value = t }

    private var listJob: Job? = null
    private var openJob: Job? = null

    /** False only when the server is known to be older than the general chat actions. */
    fun supported(): Boolean = scriptApi()?.let { it >= Updates.MIN_SCRIPT_API } ?: true

    fun refresh() {
        if (_refreshing.value || !supported()) return
        _refreshing.value = true
        listJob = tools.scope.launch {
            try {
                _chats.value = sortGeneralChats(api.generalList().chats)
                _loaded.value = true
            } catch (e: ApiException) {
                tools.report(e)
            } finally {
                _refreshing.value = false
            }
        }
    }

    /** Creates a chat on the server and opens it. */
    fun newChat() = tools.action("Starting a new chat…") {
        val r = api.generalNew()
        openChat(r.session.ifBlank { chatSession(r.id) })
        refresh()
        refreshSessions()
    }

    /** Starts the chat if it is stopped (same conversation), then opens it. */
    fun open(chat: GeneralChat) {
        if (_opening.value != null) return
        _opening.value = chat.id
        openJob = tools.scope.launch {
            try {
                val r = api.generalOpen(chat.id)
                openChat(r.session.ifBlank { chatSession(chat.id) })
                refresh()
                refreshSessions()
            } catch (e: ApiException) {
                tools.report(e)
            } finally {
                _opening.value = null
            }
        }
    }

    fun rename(id: String, title: String) = tools.action("Renaming the chat…") {
        api.generalRename(id, title)
        _chats.value = _chats.value.map { if (it.id == id) it.copy(title = title.trim()) else it }
        refresh()
    }

    fun stop(id: String) = tools.action("Stopping the chat…") {
        api.generalStop(id)
        tools.say("Chat stopped. It stays saved.")
        refresh()
        refreshSessions()
    }

    fun delete(id: String) = tools.action("Deleting the chat…") {
        api.generalDelete(id)
        _chats.value = _chats.value.filterNot { it.id == id }
        tools.say("Chat deleted")
        refresh()
        refreshSessions()
    }

    /** Another server: none of this server's chats may stay on screen. */
    fun clearServerState() {
        listJob?.cancel()
        openJob?.cancel()
        _refreshing.value = false
        _opening.value = null
        _chats.value = emptyList()
        _loaded.value = false
    }
}

/** The session name a general chat runs as. */
fun chatSession(id: String) = "chat-$id"

/** Newest activity first (a chat never updated counts from when it was created). */
fun sortGeneralChats(chats: List<GeneralChat>): List<GeneralChat> =
    chats.sortedByDescending { if (it.updatedAt > 0) it.updatedAt else it.createdAt }

/** What a row shows as the title. */
fun generalTitle(chat: GeneralChat): String = chat.title.trim().ifEmpty { "Untitled chat" }

/** "just now", "5 minutes ago", "2 hours ago", "yesterday", "3 days ago"…; "" if [then] is unknown. Times in epoch seconds (milliseconds are accepted too). */
fun relativeTime(then: Long, now: Long): String {
    if (then <= 0) return ""
    fun secs(t: Long) = if (t > 100_000_000_000L) t / 1000 else t
    val d = secs(now) - secs(then)
    fun ago(n: Long, unit: String) = "$n $unit" + (if (n == 1L) "" else "s") + " ago"
    return when {
        d < 60 -> "just now"
        d < 3_600 -> ago(d / 60, "minute")
        d < 86_400 -> ago(d / 3_600, "hour")
        d < 2 * 86_400 -> "yesterday"
        d < 30 * 86_400 -> ago(d / 86_400, "day")
        d < 365 * 86_400 -> ago(d / (30 * 86_400), "month")
        else -> ago(d / (365 * 86_400), "year")
    }
}
