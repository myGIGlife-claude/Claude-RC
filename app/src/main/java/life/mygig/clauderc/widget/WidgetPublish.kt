package life.mygig.clauderc.widget

import android.content.Context
import java.io.IOException
import life.mygig.clauderc.api.ClusterAccount
import life.mygig.clauderc.api.Session

/*
 * The one mapping from fresh server data to the widget snapshot, used by the app (after its own
 * refreshes) and by WidgetRefreshWorker. A fetch that failed (null) keeps the old data: offline or
 * a locked phone must never blank the widgets.
 */

/** [sessions] fetched at [now]; null = the fetch failed, nothing changes. */
fun WidgetSnapshot.withSessions(sessions: List<Session>?, now: Long): WidgetSnapshot =
    if (sessions == null) this
    else copy(sessions = sessions.map { it.toWidget() }.forWidget(), sessionsAt = now, configured = true)

/** The active server's cluster [accounts] fetched at [now]; null = the fetch failed, nothing changes. */
fun WidgetSnapshot.withUsage(accounts: List<ClusterAccount>?, now: Long): WidgetSnapshot =
    if (accounts == null) this
    else copy(usage = accounts.mapNotNull { it.toWidgetUsage() }, usageAt = now, configured = true)

/**
 * Whether [new] is worth a write (and a widget redraw): the content changed, or the stored data is
 * over a minute old. The app refreshes sessions every 15 s while the Sessions tab is open.
 */
fun worthWriting(old: WidgetSnapshot, new: WidgetSnapshot): Boolean =
    old.copy(sessionsAt = 0, usageAt = 0) != new.copy(sessionsAt = 0, usageAt = 0) ||
        new.sessionsAt - old.sessionsAt > 60_000 || new.usageAt - old.usageAt > 60_000

suspend fun publishSessions(ctx: Context, sessions: List<Session>) = publish(ctx) { it.withSessions(sessions, System.currentTimeMillis()) }

suspend fun publishUsage(ctx: Context, accounts: List<ClusterAccount>) = publish(ctx) { it.withUsage(accounts, System.currentTimeMillis()) }

/** Best effort: a disk error leaves the widgets as they were, it never reaches the app's screens. */
private suspend fun publish(ctx: Context, change: (WidgetSnapshot) -> WidgetSnapshot) {
    try {
        val old = WidgetStore.read(ctx)
        if (worthWriting(old, change(old))) WidgetStore.update(ctx, change)
    } catch (_: IOException) {
    }
}
