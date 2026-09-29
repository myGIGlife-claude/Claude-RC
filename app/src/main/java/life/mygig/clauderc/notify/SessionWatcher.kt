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
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import life.mygig.clauderc.BuildConfig
import life.mygig.clauderc.MainActivity
import life.mygig.clauderc.R
import life.mygig.clauderc.api.LauncherApi
import life.mygig.clauderc.api.Updates
import life.mygig.clauderc.data.SettingsStore
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
        val prefs = ctx.getSharedPreferences("session_watch", Context.MODE_PRIVATE)
        runCatching { Updates.fetch().appVersionCode }.getOrNull()
            ?.takeIf { it > BuildConfig.VERSION_CODE && it != prefs.getInt("told_build", 0) }
            ?.let { code ->
                notify(ctx, "app-update", "cLaudeRC build ${code - 100} is ready", "Open cLaudeRC and tap Install.")
                prefs.edit().putInt("told_build", code).apply()
            }
        val keys = SshKeyManager(ctx)
        val api = LauncherApi(config = { store.current().toServerConfig() }, identity = { keys.identity() })
        // Offline, or the phone is locked (the key can't be used then): try next time.
        val sessions = runCatching { api.sessions().sessions }.getOrNull() ?: return Result.success()
        val before = prefs.getStringSet("state", emptySet()).orEmpty().associate { it.substringBefore('|') to it.substringAfter('|') }
        sessions.forEach { x ->
            val was = before[x.name].orEmpty()
            when {
                x.waiting && !was.contains("w") -> notify(ctx, x.name, "${x.project} needs an answer", "Claude is asking something. Open cLaudeRC to answer.")
                !x.busy && was.contains("b") && !x.waiting -> notify(ctx, x.name, "${x.project} finished", x.preview.lines().lastOrNull { it.isNotBlank() }?.trim() ?: "Claude is done.")
            }
        }
        prefs.edit().putStringSet("state", sessions.map { it.name + "|" + (if (it.waiting) "w" else "") + (if (it.busy) "b" else "") }.toSet()).apply()
        return Result.success()
    }

    companion object {
        private const val WORK = "session-watch"
        private const val CHANNEL = "sessions"

        fun allowed(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<SessionWatcher>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun cancel(ctx: Context) = WorkManager.getInstance(ctx).cancelUniqueWork(WORK)

        private fun channel(ctx: Context) {
            ctx.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CHANNEL, "Claude sessions", NotificationManager.IMPORTANCE_DEFAULT))
        }

        private fun notify(ctx: Context, id: String, title: String, text: String) {
            if (!allowed(ctx)) return
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            @Suppress("MissingPermission")
            NotificationManagerCompat.from(ctx).notify(id.hashCode(), n)
        }
    }
}
