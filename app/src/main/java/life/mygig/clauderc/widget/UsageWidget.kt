package life.mygig.clauderc.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import java.time.Instant

/** Usage: how much of each Claude account's 5 hour and 7 day limit is used; tap opens cLaudeCluster. */
class UsageWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val first = WidgetStore.read(context)
        provideContent { UsageContent(rememberSnapshot(context, first)) }
    }
}

class UsageWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UsageWidget()
    override fun onEnabled(context: Context) { super.onEnabled(context); onWidgetEnabled(context) }
    override fun onDisabled(context: Context) { super.onDisabled(context); onWidgetDisabled(context) }
}

@Composable
private fun UsageContent(snap: WidgetSnapshot) {
    val ctx = LocalContext.current
    val now = remember(snap) { System.currentTimeMillis() }
    val open = openApp(ctx, WidgetLink.OPEN_CLUSTER)
    Column(modifier = GlanceModifier.widgetContainer()) {
        Text("Usage", modifier = GlanceModifier.fillMaxWidth().height(32.dp).clickable(open), style = titleStyle, maxLines = 1)
        when {
            !snap.configured -> WidgetMessage("Open cLaudeRC to connect a server", null, open, GlanceModifier.defaultWeight())
            snap.usage.isEmpty() -> WidgetMessage("No usage yet", "Usage appears after the app has loaded your accounts", open, GlanceModifier.defaultWeight())
            else -> {
                val stale = isStale(snap.usageAt, now)
                val nowInstant = Instant.ofEpochMilli(now)
                LazyColumn(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
                    items(snap.usage.take(MAX_USAGE_ACCOUNTS)) { u ->
                        Column(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 8.dp).clickable(open)) {
                            Text(u.account, style = nameStyle(if (stale) WidgetColors.muted else WidgetColors.fg), maxLines = 1)
                            UsageLine("5 h", u.fiveHourPct, u.fiveHourResetsAt, nowInstant)
                            UsageLine("7 d", u.sevenDayPct, u.sevenDayResetsAt, nowInstant)
                        }
                    }
                }
            }
        }
        if (snap.configured) WidgetFooter(snap.usageAt, now)
    }
}

/** One window: label, a thin bar of the USED share (full means nothing left), the percent, and when it resets. */
@Composable
private fun UsageLine(label: String, pct: Double?, resetsAt: String?, now: Instant) {
    val used = barPct(pct) ?: return
    val level = barLevel(used)
    val color = levelColor(level)
    Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = GlanceModifier.width(28.dp), style = smallStyle(), maxLines = 1)
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
    resetsInText(resetsAt, now)?.let {
        Text(it, modifier = GlanceModifier.padding(start = 28.dp), style = smallStyle(), maxLines = 1)
    }
}
