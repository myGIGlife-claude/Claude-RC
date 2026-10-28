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
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
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

/** Sessions: who needs you, who is working; a row opens that session's chat. */
class SessionsWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val first = WidgetStore.read(context)
        provideContent { SessionsContent(rememberSnapshot(context, first)) }
    }
}

class SessionsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SessionsWidget()
    override fun onEnabled(context: Context) { super.onEnabled(context); onWidgetEnabled(context) }
    override fun onDisabled(context: Context) { super.onDisabled(context); onWidgetDisabled(context) }
}

@Composable
private fun SessionsContent(snap: WidgetSnapshot) {
    val ctx = LocalContext.current
    val now = remember(snap) { System.currentTimeMillis() }
    // Narrow widgets drop the state word; the dot's description says it instead.
    val showWord = LocalSize.current.width >= 170.dp
    val waiting = snap.sessions.count { it.waiting }
    Column(modifier = GlanceModifier.widgetContainer()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().height(32.dp).clickable(openApp(ctx, WidgetLink.OPEN_SESSIONS)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Sessions", style = titleStyle, maxLines = 1)
            if (snap.configured && snap.sessions.isNotEmpty()) {
                Text("${snap.sessions.size}", modifier = GlanceModifier.padding(start = 6.dp), style = nameStyle(WidgetColors.muted))
            }
            Spacer(modifier = GlanceModifier.defaultWeight())
            if (snap.configured && waiting > 0) {
                Box(modifier = GlanceModifier.background(ImageProvider(R.drawable.widget_badge_bg)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text("$waiting waiting", style = smallStyle(WidgetColors.warn), maxLines = 1)
                }
            }
        }
        when {
            !snap.configured -> WidgetMessage(
                "Open cLaudeRC to connect a server", null,
                openApp(ctx, WidgetLink.OPEN_SESSIONS), GlanceModifier.defaultWeight(),
            )
            snap.sessions.isEmpty() -> WidgetMessage(
                "No sessions running", "Tap to start one in cLaudeRC",
                openApp(ctx, WidgetLink.OPEN_SESSIONS), GlanceModifier.defaultWeight(),
            )
            else -> {
                val stale = isStale(snap.sessionsAt, now)
                LazyColumn(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
                    items(sessionRows(snap.sessions)) { s -> SessionRow(ctx, s, stale, showWord) }
                }
            }
        }
        if (snap.configured) WidgetFooter(snap.sessionsAt, now)
    }
}

@Composable
private fun SessionRow(ctx: Context, s: WidgetSession, stale: Boolean, showWord: Boolean) {
    val dot = s.dot()
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(48.dp).clickable(openApp(ctx, WidgetLink.OPEN_SESSION, s.name)),
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
