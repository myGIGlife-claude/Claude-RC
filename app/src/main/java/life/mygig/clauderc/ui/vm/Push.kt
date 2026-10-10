package life.mygig.clauderc.ui.vm

import android.app.Application
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import life.mygig.clauderc.api.ApiException
import life.mygig.clauderc.api.LauncherApi
import life.mygig.clauderc.api.Session
import life.mygig.clauderc.notify.Push

/**
 * Instant push alerts (server hooks → Firebase). [editSessions] changes the shared session list
 * (a session's "finished" switch shows at once and goes back if the server refuses).
 */
class PushController(
    private val app: Application,
    private val api: LauncherApi,
    private val tools: VmTools,
    private val editSessions: ((List<Session>) -> List<Session>) -> Unit,
) {
    private val _pushReady = MutableStateFlow(false)
    /** This phone is registered for instant alerts (the server has push set up). */
    val pushReady = _pushReady.asStateFlow()
    init { tools.scope.launch { _pushReady.value = Push.active(app) } }

    /** Quietly (re)registers this phone; fails without a word when offline or the server has no push yet. */
    fun setupPush() = tools.scope.launch {
        _pushReady.value = runCatching { Push.setup(app, api) }.getOrElse { Push.active(app) }
    }

    /** Saves the pasted Firebase key on the server (it registers the app itself), then registers this phone. */
    fun pushSetup(key: String) = tools.action("Setting up push alerts…") {
        val c = api.pushSetup(key)
        if (c.configured) { setupPush(); tools.say("Push alerts are set up for project ${c.projectId}. Tap \"Send a test push\" to try it, then Restart sessions so they pick up the hooks.") }
    }

    fun pushTest() = tools.action("Sending a test push…") {
        val sent = api.pushTest().sent
        tools.say(if (sent > 0) "Push sent to $sent phone(s): it should arrive in a moment." else "Nothing was sent. Push isn't fully set up on the server, or this phone isn't registered yet.")
    }

    /** A session's "finished" alerts on or off (questions always alert). */
    fun setSessionPush(name: String, on: Boolean) {
        editSessions { list -> list.map { if (it.name == name) it.copy(pushDone = on) else it } }
        tools.scope.launch {
            try {
                api.pushSession(name, on)
            } catch (e: ApiException) {
                editSessions { list -> list.map { if (it.name == name) it.copy(pushDone = !on) else it } }
                tools.report(e)
            }
        }
    }
}
