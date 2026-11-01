package life.mygig.clauderc.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import life.mygig.clauderc.api.ChatData
import life.mygig.clauderc.ui.MainViewModel
import life.mygig.clauderc.ui.components.RowChevron

/** Which model alias the chat is on, read from the model id Claude reports (null when it can't be told, e.g. "Plan+Build"). */
internal fun modelAlias(modelId: String): String? = listOf("opus", "sonnet", "haiku").firstOrNull { modelId.contains(it, ignoreCase = true) }

internal fun modeLabel(mode: String): String = when (mode) { "auto" -> "Auto"; "plan" -> "Plan"; "edits" -> "Accept edits"; "bypass" -> "Bypass"; else -> "Ask first" }

/**
 * Everything you can change about this chat in one place: model, permission mode, reading aloud, permissions, workers.
 * Replaces the row of small chips and icons that used to crowd the chat header.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatControlsSheet(
    vm: MainViewModel, chat: ChatData, canChange: Boolean, speak: Boolean, onSpeak: (Boolean) -> Unit,
    showWorkers: Boolean, onWorkers: () -> Unit, onClose: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("This chat", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            SheetLabel("Model")
            val models = listOf("opus" to "Opus", "sonnet" to "Sonnet", "haiku" to "Haiku", "opusplan" to "Plan+Build")
            val current = modelAlias(chat.model)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                models.forEachIndexed { i, (alias, label) ->
                    SegmentedButton(
                        selected = alias == current, enabled = canChange,
                        onClick = { vm.chats.chatCommand("/model $alias") },
                        shape = SegmentedButtonDefaults.itemShape(i, models.size),
                        label = { Text(label, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
            SheetLabel("Mode")
            SheetGroup {
                SheetRow("Mode: ${modeLabel(chat.mode)}", "Tap to switch to the next mode", enabled = canChange, clickLabel = "Switch mode", onClick ={ vm.chats.chatKey("BTab") }) { Text("Switch ⇄", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) }
            }
            SheetGroup {
                // The row is the switch (one stop); without a callback a Switch drops its 48dp, so it is kept here.
                SheetRow("Read replies aloud", "Uses the natural voice if you turned it on in Settings", toggled = speak, onClick = { onSpeak(!speak) }) {
                    Switch(checked = speak, onCheckedChange = null, modifier = Modifier.minimumInteractiveComponentSize().clearAndSetSemantics {})
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SheetRow("Permissions", "What Claude may do without asking", clickLabel = "Open permissions", onClick = { onClose(); vm.chats.openPermissions() }) { RowChevron() }
                if (showWorkers) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SheetRow("Workers & servers", "Accounts and servers this chat can use", clickLabel = "Open workers and servers", onClick = { onClose(); onWorkers() }) { RowChevron() }
                }
            }
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun SheetGroup(content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) { Column { content() } }
}

@Composable
private fun SheetRow(title: String, subtitle: String, enabled: Boolean = true, clickLabel: String? = null, toggled: Boolean? = null, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    val tap = if (toggled != null) Modifier.toggleable(value = toggled, enabled = enabled, role = Role.Switch) { onClick() }
        else Modifier.clickable(enabled = enabled, onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
    Row(
        Modifier.fillMaxWidth().then(tap).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}
