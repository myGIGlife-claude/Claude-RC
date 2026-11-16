package life.mygig.clauderc.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/** Standard worker designations. `delegate(role=...)` matches a part of the role text, so these exact words are what the main Claude routes by. */
val ROLE_PRESETS = listOf(
    "code writing", "code reviewer", "security audit", "ui design",
    "image creation", "video creation", "research", "testing", "documentation",
)

/** Adds [preset] to a comma-separated role text unless it is already there (case-insensitive). */
fun addRole(current: String, preset: String): String {
    val parts = current.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.any { it.equals(preset, ignoreCase = true) }) return parts.joinToString(", ")
    return (parts + preset).joinToString(", ")
}

/** Role text field with a drop-down of the standard designations; typing a custom role still works. [onPick] runs when an item is chosen from the list. */
@Composable
fun RoleField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier.fillMaxWidth(), onPick: (String) -> Unit = {}) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = value, onValueChange = onValueChange, label = { Text(label) }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = { open = true }) { Icon(Icons.Filled.ArrowDropDown, contentDescription = "Pick a role") }
            },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ROLE_PRESETS.forEach { r ->
                DropdownMenuItem(text = { Text(r) }, onClick = { val v = addRole(value, r); onValueChange(v); onPick(v); open = false })
            }
            DropdownMenuItem(text = { Text("Clear") }, onClick = { onValueChange(""); onPick(""); open = false })
        }
    }
}
