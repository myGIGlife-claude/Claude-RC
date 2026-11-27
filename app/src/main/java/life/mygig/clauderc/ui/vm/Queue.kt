package life.mygig.clauderc.ui.vm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import life.mygig.clauderc.api.ApiException
import life.mygig.clauderc.api.LauncherApi
import life.mygig.clauderc.api.QUEUE_MIN_SCRIPT_API
import life.mygig.clauderc.api.QueueLogLine
import life.mygig.clauderc.api.QueueStatus
import life.mygig.clauderc.api.Session

/**
 * The auto-continue queue (docs/queue-design.md): status, the kill switch, stars and the log.
 * App lock is the protection: turning things on no longer asks for a chat PIN.
 * [editSessions] changes the shared session list so a star shows at once.
 */
class QueueController(
    private val api: LauncherApi,
    private val tools: VmTools,
    private val scriptApi: () -> Int,
    private val editSessions: ((List<Session>) -> List<Session>) -> Unit,
) {
    private val _status = MutableStateFlow<QueueStatus?>(null)
    val status = _status.asStateFlow()
    private val _log = MutableStateFlow<List<QueueLogLine>?>(null)
    val log = _log.asStateFlow()

    /** False on servers older than the queue actions (and until the server's status is in): the UI stays hidden. */
    fun supported(): Boolean = scriptApi() >= QUEUE_MIN_SCRIPT_API

    /** Quiet: this runs on a timer, so a failed read just keeps the old numbers. */
    fun refresh() {
        if (!supported()) return
        tools.scope.launch { try { _status.value = api.queueStatus() } catch (_: ApiException) {} }
    }

    fun setEnabled(on: Boolean) {
        if (!on) { tools.action("Turning the queue off…") { _status.value = api.queueOff() }; return }
        tools.action("Turning the queue on…") { _status.value = api.queueOn() }
    }

    fun setStar(session: String, on: Boolean) {
        tools.action(if (on) "Adding to the queue…" else "Removing from the queue…") {
            api.queueStar(session, on)
            editSessions { list -> list.map { if (it.name == session) it.copy(starred = on) else it } }
            _status.value = api.queueStatus()
        }
    }

    fun loadLog() {
        tools.action("Loading the queue log…") { _log.value = api.queueLog(200).lines.asReversed() }
    }

    fun clearLog() { _log.value = null }

    fun clearServerState() {
        _status.value = null
        _log.value = null
    }
}
