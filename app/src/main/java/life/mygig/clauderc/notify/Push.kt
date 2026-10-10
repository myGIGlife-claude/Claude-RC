package life.mygig.clauderc.notify

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import life.mygig.clauderc.api.LauncherApi
import life.mygig.clauderc.data.pushStore

/**
 * Instant alerts: the server's Claude hooks send a Firebase push the moment a
 * session needs an answer or finishes. The server hands over its (public)
 * Firebase ids; they are saved here so Firebase can start on its own when a
 * push arrives and the app isn't running.
 */
object Push {
    private val REGISTERED = booleanPreferencesKey("registered")
    private val APP_ID = stringPreferencesKey("app_id")
    private val API_KEY = stringPreferencesKey("api_key")
    private val PROJECT_ID = stringPreferencesKey("project_id")
    private val SENDER_ID = stringPreferencesKey("sender_id")

    /** True once this phone is registered with the server: the 15-minute check then stays quiet about sessions. */
    suspend fun active(ctx: Context): Boolean = ctx.pushStore.data.first()[REGISTERED] ?: false

    /** Start Firebase from the saved ids (every process start). Blocks, as the old prefs read did: Firebase must be up before a push is handled. */
    fun start(ctx: Context) {
        if (FirebaseApp.getApps(ctx).isNotEmpty()) return
        initFirebase(ctx, runBlocking { ctx.pushStore.data.first() })
    }

    private fun initFirebase(ctx: Context, p: Preferences) {
        val app = p[APP_ID] ?: return
        if (FirebaseApp.getApps(ctx).isNotEmpty()) return
        runCatching {
            FirebaseApp.initializeApp(
                ctx,
                FirebaseOptions.Builder()
                    .setApplicationId(app)
                    .setApiKey(p[API_KEY] ?: "")
                    .setProjectId(p[PROJECT_ID] ?: "")
                    .setGcmSenderId(p[SENDER_ID] ?: "")
                    .build(),
            )
        }
    }

    /** Ask the server for its Firebase ids, start Firebase, and register this phone's token. Returns whether push is on. */
    suspend fun setup(ctx: Context, api: LauncherApi): Boolean {
        val c = api.pushConfig()
        if (!c.configured) {
            ctx.pushStore.edit { it[REGISTERED] = false }
            return false
        }
        val p = ctx.pushStore.edit {
            it[APP_ID] = c.appId
            it[API_KEY] = c.apiKey
            it[PROJECT_ID] = c.projectId
            it[SENDER_ID] = c.senderId
        }
        initFirebase(ctx, p)
        api.pushRegister(token())
        ctx.pushStore.edit { it[REGISTERED] = true }
        return true
    }

    private suspend fun token(): String = suspendCancellableCoroutine { k ->
        FirebaseMessaging.getInstance().token.addOnCompleteListener { t ->
            if (t.isSuccessful) k.resume(t.result) else k.resumeWithException(t.exception ?: IllegalStateException("No push token"))
        }
    }
}
