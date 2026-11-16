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

/** What is waiting for a PIN: turning the whole queue on, or starring one chat. */
sealed interface QueuePinAsk {
    data object QueueOn : QueuePinAsk
    data class Star(val session: String) : QueuePinAsk
}

/**
 * The auto-continue queue (docs/queue-design.md): status, the kill switch, stars and the log.
 * Turning things on needs the chat PIN: [savedPin] gives the one already in memory or the phone's
 * vault, else [pinAsk] is set and the screen asks ([submitPin]). Turning off never asks.
 * [editSessions] changes the shared session list so a star shows at once.
 */
class QueueController(
    private val api: LauncherApi,
    private val tools: VmTools,
    private val scriptApi: () -> Int,
    private val savedPin: () -> String?,
    private val editSessions: ((List<Session>) -> List<Session>) -> Unit,
) {
    private val _status = MutableStateFlow<QueueStatus?>(null)
    val status = _status.asStateFlow()
    private val _pinAsk = MutableStateFlow<QueuePinAsk?>(null)
    val pinAsk = _pinAsk.asStateFlow()
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
        withPin(QueuePinAsk.QueueOn)
    }

    fun setStar(session: String, on: Boolean) {
        if (!on) { runStar(session, false, null); return }
        withPin(QueuePinAsk.Star(session))
    }

    fun submitPin(pin: String) {
        val ask = _pinAsk.value ?: return
        _pinAsk.value = null
        run(ask, pin)
    }

    fun cancelPin() { _pinAsk.value = null }

    fun loadLog() {
        tools.action("Loading the queue log…") { _log.value = api.queueLog(200).lines.asReversed() }
    }

    fun clearLog() { _log.value = null }

    private fun withPin(ask: QueuePinAsk) {
        val pin = savedPin()
        if (pin == null) _pinAsk.value = ask else run(ask, pin)
    }

    private fun run(ask: QueuePinAsk, pin: String) = when (ask) {
        QueuePinAsk.QueueOn -> tools.action("Turning the queue on…") { _status.value = api.queueOn(pin) }
        is QueuePinAsk.Star -> runStar(ask.session, true, pin)
    }

    private fun runStar(session: String, on: Boolean, pin: String?) {
        tools.action(if (on) "Adding to the queue…" else "Removing from the queue…") {
            api.queueStar(session, on, pin)
            editSessions { list -> list.map { if (it.name == session) it.copy(starred = on) else it } }
            _status.value = api.queueStatus()
        }
    }

    fun clearServerState() {
        _status.value = null
        _pinAsk.value = null
        _log.value = null
    }
}
