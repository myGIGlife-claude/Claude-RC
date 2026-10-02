package life.mygig.clauderc.notify

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import life.mygig.clauderc.api.LauncherApi
import life.mygig.clauderc.data.SettingsStore
import life.mygig.clauderc.ssh.SshKeyManager

/** Shows the server's push as a notification (data-only, so the Settings switch decides). */
class PushService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val d = message.data
        val s = runBlocking { SettingsStore(applicationContext).current() }
        if (!s.notify || !SessionWatcher.allowed(applicationContext)) return
        val channel = if (d["channel"] == "finished") SessionWatcher.FINISHED else SessionWatcher.ALERTS
        SessionWatcher.notify(applicationContext, d["session"] ?: "push", d["title"] ?: "cLaudeRC", d["body"] ?: "", channel)
    }

    /** Google changed this phone's token: tell the server (if the SSH key is locked, the next app start does it). */
    override fun onNewToken(token: String) {
        val ctx = applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val store = SettingsStore(ctx)
                val keys = SshKeyManager(ctx)
                val api = LauncherApi(config = { store.current().toServerConfig() }, identity = { keys.identity() })
                if (store.current().notify && Push.active(ctx)) api.pushRegister(token)
            }
        }
    }
}
