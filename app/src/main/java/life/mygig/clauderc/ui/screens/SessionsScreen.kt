package life.mygig.clauderc.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import life.mygig.clauderc.api.Updates
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import life.mygig.clauderc.api.Session
import life.mygig.clauderc.ui.LocalGuard
import life.mygig.clauderc.ui.MainViewModel
import life.mygig.clauderc.ui.formatUptime
import life.mygig.clauderc.ui.openInClaude

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(vm: MainViewModel) {
    val sessions by vm.sessions.collectAsState()
    val refreshing by vm.sessionsRefreshing.collectAsState()
    val guard = LocalGuard.current
    val context = LocalContext.current
    var confirmStop by remember { mutableStateOf<Session?>(null) }
    var confirmRestart by remember { mutableStateOf<Session?>(null) }
    val settings by vm.settings.collectAsState()
    var showRun by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refreshSessions() }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { vm.refreshSessions() }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text("", Modifier.padding(top = 2.dp)) }
            // A shell on the server: only offered when App lock guards the app.
            if (settings?.appLock == true) {
                item {
                    OutlinedButton(onClick = { showRun = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Run a command on the server")
                    }
                }
            }
            if (sessions.isEmpty()) {
                item { Text(if (refreshing) "Loading…" else "No Claude sessions running.") }
            }
            items(sessions, key = { it.name }) { s ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(s.project, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Up ${formatUptime(s.uptimeSeconds)}" + if (s.attached) " · attached" else " · detached",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (s.dir.isNotBlank()) Text(s.dir, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            Button(onClick = { openInClaude(context) }) { Text("Open in Claude") }
                            OutlinedButton(onClick = { vm.loadTail(s.name) }) { Text("Tail") }
                            TextButton(onClick = { confirmRestart = s }) { Text("Restart") }
                            TextButton(onClick = { confirmStop = s }) { Text("Stop") }
                        }
                    }
                }
            }
        }
    }

    if (showRun && settings?.appLock == true) RunCommandDialog(vm) { showRun = false }

    confirmRestart?.let { s ->
        AlertDialog(
            onDismissRequest = { confirmRestart = null },
            title = { Text("Restart ${s.project}?") },
            text = {
                Text(
                    "Stops this Claude session and starts it again in the same folder, so it picks up new " +
                        "plugins, skills and MCP servers. The conversation in it starts fresh.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestart = null
                    guard.run("Restart ${s.project}") { vm.restartSession(s.name) }
                }) { Text("Restart") }
            },
            dismissButton = { TextButton(onClick = { confirmRestart = null }) { Text("Cancel") } },
        )
    }

    confirmStop?.let { s ->
        AlertDialog(
            onDismissRequest = { confirmStop = null },
            title = { Text("Stop ${s.project}?") },
            text = { Text("This ends the Claude Remote Control session on the server.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmStop = null
                    guard.run("Stop ${s.project}") { vm.stopProject(s.name) }
                }) { Text("Stop") }
            },
            dismissButton = { TextButton(onClick = { confirmStop = null }) { Text("Cancel") } },
        )
    }
}

private const val ENABLE_RUN = "echo 'ALLOW_RUN=1' >> ~/.config/claude-launcher/config"

/** Type or paste a command (e.g. one Claude asked you to run), with a sudo password if it needs one. */
@Composable
private fun RunCommandDialog(vm: MainViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val guard = LocalGuard.current
    val busy by vm.busy.collectAsState()
    val status by vm.status.collectAsState()
    val result by vm.runResult.collectAsState()
    var command by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val close = { vm.clearRunResult(); onClose() }
    val st = status
    AlertDialog(
        onDismissRequest = { if (busy == null) close() },
        title = { Text("Run a command") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    st != null && st.scriptApi < Updates.RUN_API ->
                        Text("Update the server scripts first (the card at the top of Status).", color = MaterialTheme.colorScheme.error)
                    st != null && !st.runEnabled -> {
                        Text(
                            "Running commands from the phone is off on this server. To allow it, run this " +
                                "on the server once, then pull to refresh Status:",
                        )
                        SelectionContainer {
                            Text(ENABLE_RUN, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = {
                            copy(context, "Enable run", ENABLE_RUN)
                            vm.say("Copied. Paste it into a terminal on the server.")
                        }) { Text("Copy command") }
                    }
                    else -> {
                        OutlinedTextField(
                            value = command, onValueChange = { command = it },
                            label = { Text("Command") }, minLines = 3, maxLines = 8,
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                        )
                        OutlinedTextField(
                            value = password, onValueChange = { password = it },
                            label = { Text("sudo password (only if it uses sudo)") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = { guard.run("Run a command on the server") { vm.runCommand(command, password) } },
                            enabled = busy == null && command.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Run") }
                        Text(
                            "Runs in your home folder with bash, up to 2 minutes, no keyboard input " +
                                "(interactive prompts won't work). The password goes on stdin only.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        result?.let { r ->
                            Text(
                                if (r.exitCode == 0) "Done (exit 0)" else "Exit code ${r.exitCode}",
                                color = if (r.exitCode == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelLarge,
                            )
                            SelectionContainer(Modifier.heightIn(max = 320.dp).horizontalScroll(rememberScrollState())) {
                                Text(r.output.ifBlank { "(no output)" }, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = close) { Text("Close") } },
    )
}
