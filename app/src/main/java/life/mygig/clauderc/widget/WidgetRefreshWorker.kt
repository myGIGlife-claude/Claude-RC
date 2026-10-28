package life.mygig.clauderc.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import life.mygig.clauderc.api.LauncherApi
import life.mygig.clauderc.data.SettingsStore
import life.mygig.clauderc.ssh.SshKeyManager

/**
 * Every ~15 minutes while a widget is on the home screen (the widgets' receivers schedule and
 * cancel it): fetches the sessions and the cluster usage into the widget snapshot.
 * Each part is fetched on its own; one that fails keeps its old data.
 */
class WidgetRefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val store = SettingsStore(ctx)
        if (!store.current().isConfigured) {
            WidgetStore.update(ctx) { it.copy(configured = false) }
            return Result.success()
        }
        val keys = SshKeyManager(ctx)
        val api = LauncherApi(config = { store.current().toServerConfig() }, identity = { keys.identity() })
        // Offline, or the phone is locked (the key can't be used then): that part stays as it was.
        val sessions = runCatching { api.sessions().sessions }.getOrNull()
        val accounts = runCatching { api.cluster().accounts }.getOrNull()
        val now = System.currentTimeMillis()
        // Written even when both failed: the redraw updates the widgets' "x min ago".
        WidgetStore.update(ctx) { it.withSessions(sessions, now).withUsage(accounts, now) }
        return Result.success()
    }

    companion object {
        private const val WORK = "widget-refresh"
        private const val WORK_NOW = "widget-refresh-now"
        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES).setConstraints(online).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun cancel(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork(WORK)
            WorkManager.getInstance(ctx).cancelUniqueWork(WORK_NOW)
        }

        /**
         * A widget's refresh button: one run as soon as there is a network. Not expedited: below
         * Android 12 that runs as a foreground service, which needs a notification for a few seconds' work.
         */
        fun runNow(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<WidgetRefreshWorker>().setConstraints(online).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(WORK_NOW, ExistingWorkPolicy.REPLACE, req)
        }
    }
}
