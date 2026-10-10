package life.mygig.clauderc.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import life.mygig.clauderc.BuildConfig
import life.mygig.clauderc.MainActivity
import life.mygig.clauderc.R
import life.mygig.clauderc.api.LauncherApi
import life.mygig.clauderc.api.Updates
import life.mygig.clauderc.data.SettingsStore
import life.mygig.clauderc.data.sessionWatchStore
import life.mygig.clauderc.ssh.SshKeyManager

/**
 * Every ~15 minutes (Android's minimum for background work) checks the sessions
 * and notifies when one starts waiting on a question or finishes working, and
 * once per new app build.
 * Only runs when the user turned notifications on and Android allowed them.
 */
class SessionWatcher(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val store = SettingsStore(ctx)
        val s = store.current()
        if (!s.notify || !s.isConfigured || !allowed(ctx)) return Result.success()
        channel(ctx)
        // A new app build: say so once per build.
        val prefs = ctx.sessionWatchStore
        val toldBuild = prefs.data.first()[TOLD_BUILD] ?: 0
        runCatching { Updates.fetch().appVersionCode }.getOrNull()
            ?.takeIf { it > BuildConfig.VERSION_CODE && it != toldBuild }
            ?.let { code ->
                notify(ctx, "app-update", "cLaudeRC build ${code - 100} is ready", "Open cLaudeRC and tap Install.")
                prefs.edit { it[TOLD_BUILD] = code }
            }
        val keys = SshKeyManager(ctx)
        val api = LauncherApi(config = { store.current().toServerConfig() }, identity = { keys.identity() })
        // The server pushes these instantly once push is set up; polling would only repeat them.
        if (Push.active(ctx)) return Result.success()
        // Offline, or the phone is locked (the key can't be used then): try next time.
        val sessions = runCatching { api.sessions().sessions }.getOrNull() ?: return Result.success()
        val before = prefs.data.first()[STATE].orEmpty().associate { it.substringBefore('|') to it.substringAfter('|') }
        sessions.forEach { x ->
            val was = before[x.name].orEmpty()
            when {
                x.waiting && !was.contains("w") -> notify(ctx, x.name, "${x.project} needs an answer", "Session ${x.project} is awaiting a response that is needed before it can continue.")
                !x.busy && was.contains("b") && !x.waiting -> notify(ctx, x.name, "${x.project} finished", x.preview.lines().lastOrNull { it.isNotBlank() }?.trim() ?: "Claude is done.", FINISHED)
            }
        }
        prefs.edit { p -> p[STATE] = sessions.map { it.name + "|" + (if (it.waiting) "w" else "") + (if (it.busy) "b" else "") }.toSet() }
        return Result.success()
    }

    companion object {
        private const val WORK = "session-watch"
        // The keys the old SharedPreferences file used, so its values carry over.
        private val TOLD_BUILD = intPreferencesKey("told_build")
        private val STATE = stringSetPreferencesKey("state")
        // A channel's importance can't be raised once created, so the pop-up version has a new id.
        internal const val ALERTS = "alerts"       // a session needs an answer (pops up)
        internal const val FINISHED = "finished"   // a session finished

        fun allowed(ctx: Context) = NotificationManagerCompat.from(ctx).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<SessionWatcher>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun cancel(ctx: Context) = WorkManager.getInstance(ctx).cancelUniqueWork(WORK)

        private fun channel(ctx: Context) {
            val m = ctx.getSystemService(NotificationManager::class.java)
            m.createNotificationChannel(NotificationChannel(ALERTS, "Needs your answer", NotificationManager.IMPORTANCE_HIGH))
            m.createNotificationChannel(NotificationChannel(FINISHED, "Session finished", NotificationManager.IMPORTANCE_DEFAULT))
        }

        fun test(ctx: Context) = notify(ctx, "test", "cLaudeRC test", "If you can read this, session notifications work.")

        internal fun notify(ctx: Context, id: String, title: String, text: String, channel: String = ALERTS) {
            if (!allowed(ctx)) return
            channel(ctx)
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(ctx, channel)
                .setSmallIcon(R.drawable.ic_stat_clauderc)
                .setColor(0xFFD97757.toInt())
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setContentTitle(title)
                .setContentText(text)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(
                    NotificationCompat.Builder(ctx, channel)
                        .setSmallIcon(R.drawable.ic_stat_clauderc)
                        .setContentTitle("cLaudeRC")
                        .setContentText(if (channel == FINISHED) "A session finished" else "A session needs you")
                        .build(),
                )
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            @Suppress("MissingPermission")
            NotificationManagerCompat.from(ctx).notify(id.hashCode(), n)
        }
    }
}
