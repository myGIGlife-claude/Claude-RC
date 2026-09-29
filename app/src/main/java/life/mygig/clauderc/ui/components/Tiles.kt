package life.mygig.clauderc.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import life.mygig.clauderc.ui.theme.BadRed
import life.mygig.clauderc.ui.theme.OffGrey
import life.mygig.clauderc.ui.theme.OkGreen
import life.mygig.clauderc.ui.theme.WarnAmber

/** True on narrow screens such as a foldable's cover display: use short labels there. */
@Composable
fun isNarrow(): Boolean = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 400

/** A button label that never wraps. */
@Composable
fun OneLine(text: String) = Text(text, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)

/** A tile's health: the coloured dot. */
enum class Health { OK, WARN, BAD, OFF, NONE }

fun Health.color(): Color = when (this) {
    Health.OK -> OkGreen
    Health.WARN -> WarnAmber
    Health.BAD -> BadRed
    Health.OFF, Health.NONE -> OffGrey
}

@Composable
fun StatusDot(health: Health, modifier: Modifier = Modifier) {
    if (health == Health.NONE) {
        Box(modifier.size(10.dp).border(1.dp, OffGrey, CircleShape))
    } else {
        Box(modifier.size(10.dp).background(health.color(), CircleShape))
    }
}

/** Small upper-case heading above a group of tiles. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(top = 6.dp),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A two-per-row tile: dot, name, one line of detail. Warn tiles get the amber look. */
@Composable
fun InfoTile(
    title: String,
    subtitle: String?,
    health: Health,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val warn = health == Health.WARN
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier.fillMaxWidth().heightIn(min = 72.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (warn) Color(0xFF2A2418) else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = when {
            warn -> BorderStroke(1.dp, Color(0xFF5C4A24))
            selected -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            else -> null
        },
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusDot(health)
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (health) {
                        Health.WARN -> WarnAmber
                        Health.BAD -> Color(0xFFF09A9D)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A rounded card for groups of rows. */
@Composable
fun CardBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Box(Modifier.padding(14.dp)) { content() }
    }
}

/** label → value rows inside a CardBox. */
@Composable
fun InfoRows(rows: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { (k, v) ->
            Row {
                Text(k, modifier = Modifier.weight(0.38f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(v, modifier = Modifier.weight(0.62f), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
