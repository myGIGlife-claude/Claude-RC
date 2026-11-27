package life.mygig.clauderc.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import life.mygig.clauderc.api.QueueChip
import life.mygig.clauderc.api.QueueTone
import life.mygig.clauderc.api.queueUsageText
import life.mygig.clauderc.ui.MainViewModel
import life.mygig.clauderc.ui.components.CalmGroup
import life.mygig.clauderc.ui.components.Health
import life.mygig.clauderc.ui.components.StatusDot
import life.mygig.clauderc.ui.plural
import life.mygig.clauderc.ui.theme.statusColors

/** Health colour of a queue chip: amber needs you, green working, grey the rest (as `claudeHealth`). */
internal fun QueueTone.health(): Health = when (this) { QueueTone.WARN -> Health.WARN; QueueTone.OK -> Health.OK; QueueTone.IDLE -> Health.OFF }

/** A starred chat's state: a dot and a word, so colour is never the only clue. */
@Composable
internal fun QueueStateChip(chip: QueueChip, modifier: Modifier = Modifier) {
    val warn = chip.tone == QueueTone.WARN
    Surface(
        modifier, shape = RoundedCornerShape(8.dp),
        color = if (warn) MaterialTheme.statusColors.warnContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (warn) MaterialTheme.statusColors.onWarnContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            StatusDot(chip.tone.health())
            Text(chip.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

/** Top of the Code chat tab: the kill switch, usage numbers and the log. Caller checks `vm.queue.supported()`. */
@Composable
fun QueueRow(vm: MainViewModel) {
    val status by vm.queue.status.collectAsStateWithLifecycle()
    val on = status?.enabled == true
    val stars = status?.chats?.size ?: 0
    CalmGroup {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Auto-continue queue", style = MaterialTheme.typography.titleMedium)
                Text(
                    (if (on) "On" else "Off") + " · " + plural(stars, "starred chat", "starred chats") + " · " + (status?.usage?.let(::queueUsageText) ?: "loading…"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { vm.queue.loadLog() }) { Text("Log") }
            Switch(
                checked = on, onCheckedChange = { vm.queue.setEnabled(it) }, enabled = status != null,
                modifier = Modifier.semantics { contentDescription = "Auto-continue queue" },
            )
        }
    }
}

/** The queue's log sheet; shown wherever a queue action can start (Sessions tab, chat). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueDialogs(vm: MainViewModel) {
    val log by vm.queue.log.collectAsStateWithLifecycle()
    log?.let { lines ->
        ModalBottomSheet(onDismissRequest = { vm.queue.clearLog() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Queue log", style = MaterialTheme.typography.titleLarge)
                Text("Newest first. Names and events only, never chat text.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (lines.isEmpty()) Text("Nothing logged yet.", style = MaterialTheme.typography.bodyMedium)
                lines.forEach { l ->
                    Text(
                        listOf(l.ts.replace('T', ' ').removeSuffix("Z"), l.session.takeIf { it != "-" }.orEmpty(), l.event, l.detail).filter { it.isNotBlank() }.joinToString("  "),
                        style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}
