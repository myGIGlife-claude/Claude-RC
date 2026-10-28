package life.mygig.clauderc.widget

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
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
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.Text
import life.mygig.clauderc.R

/** Quick add: four shortcuts into the app. A toolbar when wide and short, a 2 by 2 grid when about square. */
class QuickAddWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { QuickAddContent() }
    }
}

class QuickAddWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddWidget()
    override fun onEnabled(context: Context) { super.onEnabled(context); onWidgetEnabled(context) }
    override fun onDisabled(context: Context) { super.onDisabled(context); onWidgetDisabled(context) }
}

private class Shortcut(@DrawableRes val icon: Int, val label: String, val open: String)

private val SHORTCUTS = listOf(
    Shortcut(R.drawable.ic_widget_new_repo, "New repo", WidgetLink.OPEN_NEW_REPO),
    Shortcut(R.drawable.ic_widget_key, "Add key", WidgetLink.OPEN_KEYS),
    Shortcut(R.drawable.ic_widget_chat, "New chat", WidgetLink.OPEN_NEW_CHAT),
    Shortcut(R.drawable.ic_widget_sessions, "Sessions", WidgetLink.OPEN_SESSIONS),
)

@Composable
private fun QuickAddContent() {
    val ctx = LocalContext.current
    val size = LocalSize.current
    val grid = size.height >= 120.dp && size.width < 220.dp
    // Labels need about 72dp of height per button row; below that the icons carry a content description instead.
    val showLabel = (if (grid) size.height / 2 else size.height) >= 72.dp
    Column(modifier = GlanceModifier.widgetContainer(padding = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        val rows = if (grid) SHORTCUTS.chunked(2) else listOf(SHORTCUTS)
        rows.forEach { row ->
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                row.forEach { s ->
                    QuickButton(s, openApp(ctx, s.open), showLabel, GlanceModifier.defaultWeight().fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun QuickButton(s: Shortcut, action: Action, showLabel: Boolean, modifier: GlanceModifier) {
    Column(
        modifier = modifier.clickable(action),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = GlanceModifier.size(40.dp).background(ImageProvider(R.drawable.widget_button_bg)),
            contentAlignment = Alignment.Center,
        ) {
            Image(ImageProvider(s.icon), contentDescription = if (showLabel) null else s.label, modifier = GlanceModifier.size(22.dp))
        }
        if (showLabel) Text(s.label, modifier = GlanceModifier.padding(top = 4.dp), style = smallStyle(WidgetColors.fg), maxLines = 1)
    }
}
