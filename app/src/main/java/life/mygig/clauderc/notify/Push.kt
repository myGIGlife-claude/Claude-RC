package life.mygig.clauderc.notify

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import life.mygig.clauderc.api.LauncherApi

/**
 * Instant alerts: the server's Claude hooks send a Firebase push the moment a
 * session needs an answer or finishes. The server hands over its (public)
 * Firebase ids; they are saved here so Firebase can start on its own when a
 * push arrives and the app isn't running.
 */
object Push {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("push", Context.MODE_PRIVATE)

    /** True once this phone is registered with the server: the 15-minute check then stays quiet about sessions. */
    fun active(ctx: Context) = prefs(ctx).getBoolean("registered", false)

    /** Start Firebase from the saved ids (every process start). */
    fun start(ctx: Context) {
        val p = prefs(ctx)
        val app = p.getString("app_id", null) ?: return
        if (FirebaseApp.getApps(ctx).isNotEmpty()) return
        runCatching {
            FirebaseApp.initializeApp(
                ctx,
                FirebaseOptions.Builder()
                    .setApplicationId(app)
                    .setApiKey(p.getString("api_key", "") ?: "")
                    .setProjectId(p.getString("project_id", "") ?: "")
                    .setGcmSenderId(p.getString("sender_id", "") ?: "")
                    .build(),
            )
        }
    }

    /** Ask the server for its Firebase ids, start Firebase, and register this phone's token. Returns whether push is on. */
    suspend fun setup(ctx: Context, api: LauncherApi): Boolean {
        val c = api.pushConfig()
        val p = prefs(ctx)
        if (!c.configured) {
            p.edit().putBoolean("registered", false).apply()
            return false
        }
        p.edit().putString("app_id", c.appId).putString("api_key", c.apiKey)
            .putString("project_id", c.projectId).putString("sender_id", c.senderId).apply()
        start(ctx)
        api.pushRegister(token())
        p.edit().putBoolean("registered", true).apply()
        return true
    }

    private suspend fun token(): String = suspendCancellableCoroutine { k ->
        FirebaseMessaging.getInstance().token.addOnCompleteListener { t ->
            if (t.isSuccessful) k.resume(t.result) else k.resumeWithException(t.exception ?: IllegalStateException("No push token"))
        }
    }
}
