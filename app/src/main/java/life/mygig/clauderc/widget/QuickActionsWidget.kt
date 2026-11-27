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
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.Text
import life.mygig.clauderc.R

/** Quick actions: up to six chosen shortcuts into the app, as a toolbar or a grid depending on the size. */
class QuickActionsWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val first = loadWidgetState(context, id)
        provideContent { QuickActionsContent(rememberWidgetState(context, id, first).config) }
    }

    override suspend fun onDelete(context: Context, glanceId: GlanceId) = forgetWidget(context, glanceId)
}

class QuickActionsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickActionsWidget()
    override fun onEnabled(context: Context) { super.onEnabled(context); onWidgetEnabled(context) }
    override fun onDisabled(context: Context) { super.onDisabled(context); onWidgetDisabled(context) }
}

@DrawableRes
internal fun actionIcon(id: String): Int = when (id) {
    "new_repo" -> R.drawable.ic_widget_new_repo
    "add_key" -> R.drawable.ic_widget_key
    "new_chat" -> R.drawable.ic_widget_chat
    "usage" -> R.drawable.ic_widget_usage
    else -> R.drawable.ic_widget_sessions
}

@Composable
private fun QuickActionsContent(config: WidgetConfig) {
    val ctx = LocalContext.current
    val size = LocalSize.current
    val actions = sanitizeActions(config.actions)
    val lay = actionsLayout(size.width.value, size.height.value, actions.size)
    Column(modifier = GlanceModifier.widgetContainer(lay.padding.dp), verticalAlignment = Alignment.CenterVertically) {
        actions.take(lay.maxItems).chunked(lay.columns).forEach { row ->
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                row.forEach { a -> QuickButton(ctx, a, lay.labels, GlanceModifier.defaultWeight().fillMaxHeight()) }
                // Keep a short last row aligned with the grid.
                repeat(lay.columns - row.size) { Spacer(modifier = GlanceModifier.defaultWeight()) }
            }
        }
    }
}

@Composable
private fun QuickButton(ctx: Context, a: QuickAction, showLabel: Boolean, modifier: GlanceModifier) {
    Column(
        modifier = modifier.clickable(openApp(ctx, a.open)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = GlanceModifier.size(40.dp).background(ImageProvider(R.drawable.widget_button_bg)),
            contentAlignment = Alignment.Center,
        ) {
            Image(ImageProvider(actionIcon(a.id)), contentDescription = if (showLabel) null else a.label, modifier = GlanceModifier.size(22.dp))
        }
        if (showLabel) Text(a.label, modifier = GlanceModifier.padding(top = 4.dp), style = smallStyle(WidgetColors.fg), maxLines = 1)
    }
}
