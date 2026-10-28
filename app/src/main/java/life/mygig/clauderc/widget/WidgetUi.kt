// Privacy: widgets sit on the home screen, so they show only project names, status and usage numbers, never chat or preview text.
package life.mygig.clauderc.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import life.mygig.clauderc.MainActivity
import life.mygig.clauderc.R
import life.mygig.clauderc.data.widgetStore

/** The calm-look tokens (docs/mockups/ui-refresh.html), light and dark; drawables use the same values from widget_colors.xml. */
internal object WidgetColors {
    val fg = ColorProvider(day = Color(0xFF1C1B18), night = Color(0xFFF1EFE9))
    val muted = ColorProvider(day = Color(0xFF5C5952), night = Color(0xFFB9B6AD))
    val accent = ColorProvider(day = Color(0xFFB85C3E), night = Color(0xFFD97757))
    val ok = ColorProvider(day = Color(0xFF1E8E5A), night = Color(0xFF4CC38A))
    val warn = ColorProvider(day = Color(0xFF8A5A00), night = Color(0xFFF2C46D))
    val bad = ColorProvider(day = Color(0xFFC93A3F), night = Color(0xFFE5484D))
    val idle = ColorProvider(day = Color(0xFF8C8880), night = Color(0xFF6B6862))
    val line = ColorProvider(day = Color(0xFFDAD6CC), night = Color(0xFF2A2B30))
}

internal fun dotColor(dot: Dot): ColorProvider = when (dot) {
    Dot.WAITING -> WidgetColors.warn
    Dot.BUSY -> WidgetColors.ok
    Dot.IDLE -> WidgetColors.idle
}

internal fun levelColor(level: BarLevel): ColorProvider = when (level) {
    BarLevel.OK -> WidgetColors.accent
    BarLevel.WARN -> WidgetColors.warn
    BarLevel.BAD -> WidgetColors.bad
}

internal val titleStyle = TextStyle(color = WidgetColors.fg, fontSize = 16.sp, fontWeight = FontWeight.Medium)
internal fun nameStyle(color: ColorProvider = WidgetColors.fg, align: TextAlign? = null) =
    TextStyle(color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = align)
internal fun smallStyle(color: ColorProvider = WidgetColors.muted, align: TextAlign? = null) =
    TextStyle(color = color, fontSize = 11.sp, textAlign = align)

/** Rounded s1 surface with the system widget radius, padded 12dp (Quick add's short toolbar uses less). */
internal fun GlanceModifier.widgetContainer(padding: Dp = 12.dp): GlanceModifier =
    fillMaxSize().appWidgetBackground().background(ImageProvider(R.drawable.widget_background)).cornerRadius(R.dimen.widget_radius).padding(padding)

/** Opens the app at [open] (a WidgetLink.OPEN_* value); MainActivity checks the extras before using them. */
internal fun openApp(ctx: Context, open: String, session: String? = null): Action =
    actionStartActivity(
        Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(WidgetLink.EXTRA_OPEN, open)
            .apply { if (session != null) putExtra(WidgetLink.EXTRA_SESSION, session) },
    )

/**
 * The stored snapshot, starting from [first] and following later writes: a widget's Glance session can outlive one
 * update, and a redraw during it recomposes instead of calling provideGlance again.
 */
@Composable
internal fun rememberSnapshot(ctx: Context, first: WidgetSnapshot): WidgetSnapshot =
    produceState(first) { ctx.widgetStore.data.collect { value = WidgetStore.read(ctx) } }.value

/** A centered message filling the space (not configured / nothing to show); tapping it opens the app. */
@Composable
internal fun WidgetMessage(title: String, hint: String?, onClick: Action, modifier: GlanceModifier) {
    Column(
        modifier = modifier.fillMaxWidth().clickable(onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = nameStyle(align = TextAlign.Center))
        if (hint != null) {
            Text(hint, modifier = GlanceModifier.padding(top = 4.dp), style = smallStyle(align = TextAlign.Center))
        }
    }
}

/** Data age (with the "phone locked or offline?" hint once stale) and a refresh button that asks the worker to fetch now. */
@Composable
internal fun WidgetFooter(at: Long, now: Long) {
    val stale = isStale(at, now)
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            footerText(at, now),
            modifier = GlanceModifier.defaultWeight(),
            style = smallStyle(if (stale) WidgetColors.warn else WidgetColors.muted),
            maxLines = 2,
        )
        Box(
            modifier = GlanceModifier.size(40.dp).clickable(actionRunCallback<RefreshWidgetsAction>()),
            contentAlignment = Alignment.Center,
        ) {
            Image(ImageProvider(R.drawable.ic_widget_refresh), contentDescription = "Refresh", modifier = GlanceModifier.size(18.dp))
        }
    }
}
