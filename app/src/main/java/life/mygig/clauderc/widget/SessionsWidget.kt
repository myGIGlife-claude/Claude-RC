package life.mygig.clauderc.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import life.mygig.clauderc.R

/** Sessions: who needs you, who is working (all, active only or picked ones); a row opens that session's chat. */
class SessionsWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val first = loadWidgetState(context, id)
        provideContent { SessionsContent(rememberWidgetState(context, id, first)) }
    }

    override suspend fun onDelete(context: Context, glanceId: GlanceId) = forgetWidget(context, glanceId)
}

class SessionsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SessionsWidget()
    override fun onEnabled(context: Context) { super.onEnabled(context); onWidgetEnabled(context) }
    override fun onDisabled(context: Context) { super.onDisabled(context); onWidgetDisabled(context) }
}

@Composable
private fun SessionsContent(state: WidgetState) {
    val (snap, config) = state
    val ctx = LocalContext.current
    val now = remember(snap) { System.currentTimeMillis() }
    val size = LocalSize.current
    val lay = sessionsLayout(size.width.value, size.height.value, config.rowCap())
    val rows = config.pickSessions(snap.sessions)
    val open = openApp(ctx, WidgetLink.OPEN_SESSIONS)
    Column(modifier = GlanceModifier.widgetContainer(lay.padding.dp)) {
        SessionsHeader(rows, snap.configured, open)
        when {
            lay.rowsPerColumn == 0 -> {}   // very short: the header (count, waiting badge) is all that fits
            !snap.configured -> WidgetMessage("Open cLaudeRC to connect a server", null, open, GlanceModifier.defaultWeight())
            rows.isEmpty() -> WidgetMessage(emptySessionsText(config.filter), "Tap to open your sessions", open, GlanceModifier.defaultWeight())
            else -> {
                val stale = isStale(snap.sessionsAt, now)
                val shown = rows.take(lay.maxItems)
                // Waiting first, so the first column holds what matters most; columns are filled top to bottom.
                val perColumn = (shown.size + lay.columns - 1) / lay.columns
                Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                    shown.chunked(perColumn).forEachIndexed { i, column ->
                        if (i > 0) Spacer(modifier = GlanceModifier.width(COLUMN_GAP.dp))
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            column.forEach { s -> SessionRow(ctx, s, stale, lay.showWord) }
                        }
                    }
                }
            }
        }
        if (lay.footer && snap.configured) WidgetFooter(snap.sessionsAt, now)
    }
}

@Composable
private fun SessionsHeader(rows: List<WidgetSession>, configured: Boolean, open: Action) {
    val waiting = rows.count { it.waiting }
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(HEADER_H.dp).clickable(open),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Sessions", style = titleStyle, maxLines = 1)
        if (configured && rows.isNotEmpty()) {
            Text("${rows.size}", modifier = GlanceModifier.padding(start = 6.dp), style = nameStyle(WidgetColors.muted), maxLines = 1)
        }
        Spacer(modifier = GlanceModifier.defaultWeight())
        if (configured && waiting > 0) {
            Box(modifier = GlanceModifier.background(ImageProvider(R.drawable.widget_badge_bg)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text("$waiting waiting", style = smallStyle(WidgetColors.warn), maxLines = 1)
            }
        }
    }
}

@Composable
private fun SessionRow(ctx: Context, s: WidgetSession, stale: Boolean, showWord: Boolean) {
    val dot = s.dot()
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(ROW_H.dp).clickable(openApp(ctx, WidgetLink.OPEN_SESSION, s.name)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            ImageProvider(R.drawable.widget_dot),
            contentDescription = if (showWord) null else stateWord(dot),
            modifier = GlanceModifier.size(10.dp),
            colorFilter = ColorFilter.tint(dotColor(dot)),
        )
        Spacer(modifier = GlanceModifier.width(10.dp))
        // Stale data is dimmed: names in the muted color.
        Text(
            s.project,
            modifier = GlanceModifier.defaultWeight(),
            style = nameStyle(if (stale) WidgetColors.muted else WidgetColors.fg),
            maxLines = 1,
        )
        if (showWord) Text(stateWord(dot), modifier = GlanceModifier.padding(start = 8.dp), style = smallStyle(), maxLines = 1)
    }
}
