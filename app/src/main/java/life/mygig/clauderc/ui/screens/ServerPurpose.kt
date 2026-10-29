package life.mygig.clauderc.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import life.mygig.clauderc.data.PURPOSE_MAX
import life.mygig.clauderc.data.PURPOSE_PRESETS
import life.mygig.clauderc.data.Server
import life.mygig.clauderc.data.cleanPurpose

/** "What is this server for?": free text plus preset chips. A chip fills the field; tapping the chosen chip again clears it. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PurposeField(value: String, onChange: (String) -> Unit, enabled: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it.filterNot(Char::isISOControl).take(PURPOSE_MAX)) },
            enabled = enabled,
            label = { Text("What is this server for? (optional)") },
            placeholder = { Text("e.g. Web hosting") },
            supportingText = { Text("${value.length}/$PURPOSE_MAX") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PURPOSE_PRESETS.forEach { p ->
                val chosen = cleanPurpose(value) == p
                FilterChip(
                    selected = chosen,
                    onClick = { onChange(if (chosen) "" else p) },
                    enabled = enabled,
                    label = { Text(p) },
                )
            }
        }
    }
}

/** Edits one server's purpose; [onSave] gets the cleaned text ("" clears it). */
@Composable
fun PurposeDialog(server: Server, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable(server.id) { mutableStateOf(server.purpose) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 420.dp),
        title = { Text("What is this server for?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    server.host,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PurposeField(text, { text = it })
                Text(
                    "Saved in the app, not on the server. It shows next to the server wherever servers are listed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(cleanPurpose(text)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
