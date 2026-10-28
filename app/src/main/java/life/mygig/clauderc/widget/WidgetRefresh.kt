package life.mygig.clauderc.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll

/** Redraws every placed widget from the snapshot. */
suspend fun refreshWidgets(ctx: Context) {
    SessionsWidget().updateAll(ctx)
    QuickAddWidget().updateAll(ctx)
    UsageWidget().updateAll(ctx)
}

/** The footer's refresh button: the worker fetches now and its snapshot write redraws the widgets. */
class RefreshWidgetsAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetRefreshWorker.runNow(context)
    }
}

private val RECEIVERS = listOf(SessionsWidgetReceiver::class.java, QuickAddWidgetReceiver::class.java, UsageWidgetReceiver::class.java)

/** The first widget of a kind was placed: keep the background refresh going and fetch now. */
internal fun onWidgetEnabled(ctx: Context) {
    WidgetRefreshWorker.schedule(ctx)
    WidgetRefreshWorker.runNow(ctx)
}

/** The last widget of a kind was removed: stop the background refresh only if none of the three is left. */
internal fun onWidgetDisabled(ctx: Context) {
    val mgr = AppWidgetManager.getInstance(ctx)
    if (RECEIVERS.none { mgr.getAppWidgetIds(ComponentName(ctx, it)).isNotEmpty() }) WidgetRefreshWorker.cancel(ctx)
}
