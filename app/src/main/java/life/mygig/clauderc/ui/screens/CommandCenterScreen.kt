package life.mygig.clauderc.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import life.mygig.clauderc.api.Updates
import life.mygig.clauderc.ui.LocalGuard
import life.mygig.clauderc.ui.MainViewModel
import life.mygig.clauderc.ui.Tab

/**
 * Claude Code housekeeping on the server: doctor, updates, plugins,
 * marketplaces and MCP servers. Runs `claude <args>` from a fixed allowlist.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommandCenterScreen(vm: MainViewModel) {
    val guard = LocalGuard.current
    val busy by vm.busy.collectAsState()
    val status by vm.status.collectAsState()
    val result by vm.ccResult.collectAsState()
    var plugin by rememberSaveable { mutableStateOf("") }
    var marketplace by rememberSaveable { mutableStateOf("") }
    var custom by rememberSaveable { mutableStateOf("") }
    val idle = busy == null
    // Read-only commands run straight away; anything that changes the server asks for App lock.
    val look = { args: String -> vm.claudeCommand(args) }
    val change = { args: String -> guard.run("Run claude $args") { vm.claudeCommand(args) } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if ((status?.scriptApi ?: 0) < Updates.COMMAND_CENTER_API) {
            Text(
                "Update the server scripts first (the card at the top of Status).",
                color = MaterialTheme.colorScheme.error,
            )
            OutlinedButton(onClick = { vm.selectTab(Tab.STATUS) }) { Text("Go to Status") }
            return@Column
        }

        Section("Check")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { look("doctor") }, enabled = idle) { Text("Doctor") }
            OutlinedButton(onClick = { look("--version") }, enabled = idle) { Text("Version") }
            OutlinedButton(onClick = { change("update") }, enabled = idle) { Text("Update Claude Code") }
        }

        Section("Plugins and skills")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { look("plugin list") }, enabled = idle) { Text("Installed plugins") }
            OutlinedButton(onClick = { look("plugin marketplace list") }, enabled = idle) { Text("Marketplaces") }
            OutlinedButton(onClick = { change("plugin marketplace update") }, enabled = idle) { Text("Update marketplaces") }
        }
        Field(plugin, "Plugin (name@marketplace)") { plugin = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { change("plugin install $plugin") }, enabled = idle && plugin.isNotBlank()) { Text("Install") }
            OutlinedButton(onClick = { change("plugin update $plugin") }, enabled = idle && plugin.isNotBlank()) { Text("Update") }
            OutlinedButton(onClick = { change("plugin uninstall $plugin") }, enabled = idle && plugin.isNotBlank()) { Text("Uninstall") }
        }
        Field(marketplace, "Marketplace (owner/repo or URL)") { marketplace = it }
        Button(onClick = { change("plugin marketplace add $marketplace") }, enabled = idle && marketplace.isNotBlank()) {
            Text("Add marketplace")
        }
        Text(
            "Running sessions pick up new plugins after a restart (Sessions › Restart).",
            style = MaterialTheme.typography.bodySmall,
        )

        Section("MCP servers")
        OutlinedButton(onClick = { look("mcp list") }, enabled = idle) { Text("List MCP servers") }

        Section("Other claude command")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("claude", fontFamily = FontFamily.Monospace)
            OutlinedTextField(
                value = custom, onValueChange = { custom = it.replace("\n", " ") },
                singleLine = true, modifier = Modifier.weight(1f),
                placeholder = { Text("plugin details name") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            )
        }
        Button(onClick = { change(custom) }, enabled = idle && custom.isNotBlank()) { Text("Run") }
        Text(
            "Allowed: doctor, update, --version, plugin …, plugin marketplace …, mcp list / get / remove.",
            style = MaterialTheme.typography.bodySmall,
        )

        result?.let { (args, r) ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("claude $args", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelLarge)
                    Text(
                        if (r.exitCode == 0) "Done (exit 0)" else "Exit code ${r.exitCode}",
                        color = if (r.exitCode == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    SelectionContainer(Modifier.horizontalScroll(rememberScrollState())) {
                        Text(r.output.ifBlank { "(no output)" }, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                    // Sessions only load plugins and MCP servers when they start.
                    val changes = Regex("^(plugins? (install|i|uninstall|remove|enable|disable|update)|mcp remove)\\b")
                    if (r.exitCode == 0 && changes.containsMatchIn(args)) {
                        Text("Running sessions pick this up after a restart.", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { vm.selectTab(Tab.SESSIONS) }) { Text("Go to Sessions to restart") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Field(value: String, label: String, onChange: (String) -> Unit) = OutlinedTextField(
    value = value, onValueChange = { onChange(it.trim()) },
    label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
)
