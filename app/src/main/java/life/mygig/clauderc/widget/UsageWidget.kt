package life.mygig.clauderc.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import java.time.Instant

/** Usage: how much of each chosen Claude account's 5 hour (and 7 day) limit is used; tap opens cLaudeCluster. */
class UsageWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val first = loadWidgetState(context, id)
        provideContent { UsageContent(rememberWidgetState(context, id, first)) }
    }

    override suspend fun onDelete(context: Context, glanceId: GlanceId) = forgetWidget(context, glanceId)
}

class UsageWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UsageWidget()
    override fun onEnabled(context: Context) { super.onEnabled(context); onWidgetEnabled(context) }
    override fun onDisabled(context: Context) { super.onDisabled(context); onWidgetDisabled(context) }
}

@Composable
private fun UsageContent(state: WidgetState) {
    val (snap, config) = state
    val ctx = LocalContext.current
    val now = remember(snap) { System.currentTimeMillis() }
    val size = LocalSize.current
    val picked = config.pickAccounts(snap.usage)
    val lay = usageLayout(size.width.value, size.height.value, picked.size, config.showWeekly)
    val accounts = if (lay.accounts == 1) picked.mostUsedFirst().take(1) else picked.take(lay.accounts)
    val stale = isStale(snap.usageAt, now)
    val nowInstant = Instant.ofEpochMilli(now)
    val open = openApp(ctx, WidgetLink.OPEN_CLUSTER)
    val top = accounts.firstOrNull()
    Column(modifier = GlanceModifier.widgetContainer(lay.padding.dp).clickable(open)) {
        if (lay.nameInHeader && snap.configured && top != null) {
            // Short: the most used account in the header, its 5 h bar under it.
            UsageHeader("Usage · ${top.account}")
            if (lay.bars > 0) UsageLine("5 h", top.fiveHourPct, null, nowInstant)
        } else UsageBody(snap, lay, accounts, stale, now, nowInstant, open)
    }
}

@Composable
private fun ColumnScope.UsageBody(
    snap: WidgetSnapshot, lay: UsageLayout, accounts: List<WidgetUsage>, stale: Boolean, now: Long, nowInstant: Instant, open: Action,
) {
    UsageHeader("Usage")
    when {
        !snap.configured -> WidgetMessage("Open cLaudeRC to connect a server", null, open, GlanceModifier.defaultWeight())
        accounts.isEmpty() -> WidgetMessage("No usage yet", "Usage appears after the app has loaded your accounts", open, GlanceModifier.defaultWeight())
        else -> Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            accounts.chunked(lay.columns).forEach { line ->
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    line.forEachIndexed { i, u ->
                        if (i > 0) Spacer(modifier = GlanceModifier.width(COLUMN_GAP.dp))
                        AccountBlock(u, lay, stale, nowInstant, GlanceModifier.defaultWeight())
                    }
                    // A short last line keeps the column widths.
                    repeat(lay.columns - line.size) {
                        Spacer(modifier = GlanceModifier.width(COLUMN_GAP.dp))
                        Spacer(modifier = GlanceModifier.defaultWeight())
                    }
                }
            }
        }
    }
    if (lay.footer && snap.configured) WidgetFooter(snap.usageAt, now)
}

@Composable
private fun UsageHeader(title: String) {
    Text(title, modifier = GlanceModifier.fillMaxWidth().height(HEADER_H.dp), style = titleStyle, maxLines = 1)
}

@Composable
private fun AccountBlock(u: WidgetUsage, lay: UsageLayout, stale: Boolean, now: Instant, modifier: GlanceModifier) {
    Column(modifier = modifier.padding(bottom = USAGE_GAP.dp)) {
        Text(
            u.account,
            modifier = GlanceModifier.height(USAGE_NAME_H.dp),
            style = nameStyle(if (stale) WidgetColors.muted else WidgetColors.fg),
            maxLines = 1,
        )
        UsageLine("5 h", u.fiveHourPct, u.fiveHourResetsAt.takeIf { lay.resets }, now)
        if (lay.weekly) UsageLine("7 d", u.sevenDayPct, u.sevenDayResetsAt.takeIf { lay.resets }, now)
    }
}

/** One window: label, a thin bar of the USED share (full means nothing left), the percent, and when it resets. */
@Composable
private fun UsageLine(label: String, pct: Double?, resetsAt: String?, now: Instant) {
    val used = barPct(pct)
    Row(modifier = GlanceModifier.fillMaxWidth().height(USAGE_LINE_H.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = GlanceModifier.width(28.dp), style = smallStyle(), maxLines = 1)
        if (used == null) {
            Text("no data", modifier = GlanceModifier.defaultWeight(), style = smallStyle(), maxLines = 1)
        } else {
            val level = barLevel(used)
            val color = levelColor(level)
            LinearProgressIndicator(
                progress = used / 100f,
                modifier = GlanceModifier.defaultWeight().height(6.dp),
                color = color,
                backgroundColor = WidgetColors.line,
            )
            // The percent carries the warn/bad color too (older Android versions may not tint the bar).
            Text(
                "$used%",
                modifier = GlanceModifier.width(40.dp),
                style = smallStyle(if (level != BarLevel.OK) color else WidgetColors.muted, TextAlign.End),
                maxLines = 1,
            )
        }
    }
    // Always takes its row when reset hints are on, so the layout math holds; empty when the time is unknown.
    if (resetsAt != null) {
        Text(
            resetsInText(resetsAt, now).orEmpty(),
            modifier = GlanceModifier.height(USAGE_RESET_H.dp).padding(start = 28.dp),
            style = smallStyle(),
            maxLines = 1,
        )
    }
}
