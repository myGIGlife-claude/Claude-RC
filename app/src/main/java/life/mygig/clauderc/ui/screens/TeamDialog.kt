package life.mygig.clauderc.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import life.mygig.clauderc.api.WORKER_NAME_RE
import life.mygig.clauderc.api.Worker
import life.mygig.clauderc.ui.MainViewModel
import life.mygig.clauderc.ui.components.CardBox
import life.mygig.clauderc.ui.openUrl

private val MODES = listOf("acceptEdits", "plan", "bypassPermissions")
private fun modeHint(m: String) = when (m) {
    "acceptEdits" -> "edits files, can't run commands"
    "plan" -> "read-only: research and plans"
    "bypassPermissions" -> "runs anything without asking"
    else -> m
}

/** Extra Claude accounts ("workers") the main Claude can hand work to through the clauderc-team MCP server. */
@Composable
fun TeamDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val workers by vm.workers.collectAsState()
    val login by vm.workerLogin.collectAsState()
    val runs by vm.workerRuns.collectAsState()
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var confirmRemove by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.loadWorkers() }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Team") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Other Claude accounts of yours. In the main chat, tell Claude which kind of work goes to which worker " +
                        "(for example \"research goes to research, UI goes to ui\") and it hands tasks over for you.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (workers == null) Text("Loading…")
                workers?.forEach { w -> WorkerCard(vm, w, onRemove = { confirmRemove = w.name }) }
                if (workers?.isEmpty() == true) Text("No workers yet.")
                Text("Add a worker", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = name, onValueChange = { name = it.trim() }, label = { Text("Name (research, coding, ui…)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), isError = name.isNotEmpty() && !WORKER_NAME_RE.matches(name),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                )
                OutlinedTextField(
                    value = role, onValueChange = { role = it }, label = { Text("What it's for") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { vm.addWorker(name, role); name = ""; role = "" },
                    enabled = WORKER_NAME_RE.matches(name) && vm.busy.collectAsState().value == null, modifier = Modifier.fillMaxWidth(),
                ) { Text("Add worker") }
            }
        },
    )

    login?.let { (who, url) ->
        AlertDialog(
            onDismissRequest = { vm.closeWorkerLogin(); code = "" },
            confirmButton = {
                TextButton(onClick = { vm.workerLoginCode(who, code); code = "" }, enabled = code.length >= 4) { Text("Submit code") }
            },
            dismissButton = { TextButton(onClick = { vm.closeWorkerLogin(); code = "" }) { Text("Cancel") } },
            title = { Text("Sign in $who") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Use the Claude account meant for this worker (sign out of other accounts in your browser first, or use a private tab).")
                    OutlinedButton(onClick = { openUrl(context, url.url) }, modifier = Modifier.fillMaxWidth()) { Text("Open link") }
                    OutlinedTextField(
                        value = code, onValueChange = { code = it.trim() }, label = { Text("Code") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    )
                }
            },
        )
    }

    runs?.let { (who, list) ->
        AlertDialog(
            onDismissRequest = vm::closeWorkerRuns,
            confirmButton = { TextButton(onClick = vm::closeWorkerRuns) { Text("Close") } },
            title = { Text("$who: recent tasks") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (list.isEmpty()) Text("Nothing delegated yet.")
                    list.forEach { r ->
                        CardBox {
                            Column(Modifier.fillMaxWidth()) {
                                Text("${r.status} · ${r.task}", style = MaterialTheme.typography.labelMedium)
                                Text(r.reply ?: r.error ?: "Running…", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
        )
    }

    confirmRemove?.let { who ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            confirmButton = { TextButton(onClick = { vm.removeWorker(who); confirmRemove = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Keep") } },
            title = { Text("Remove $who?") },
            text = { Text("Signs the account out on the server and forgets its past tasks.") },
        )
    }
}

@Composable
private fun WorkerCard(vm: MainViewModel, w: Worker, onRemove: () -> Unit) {
    val idle = vm.busy.collectAsState().value == null   // a double tap would repeat the action (Mode would skip a step)
    CardBox {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(w.name + if (w.signedIn) "" else " · not signed in", style = MaterialTheme.typography.titleSmall)
            if (w.role.isNotBlank()) Text(w.role, style = MaterialTheme.typography.bodySmall)
            Text("Mode: ${w.mode} (${modeHint(w.mode)})", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { vm.workerLoginStart(w.name) }, enabled = idle) { Text(if (w.signedIn) "Re-sign in" else "Sign in") }
                OutlinedButton(onClick = { vm.setWorker(w.name, "mode", MODES[(MODES.indexOf(w.mode) + 1) % MODES.size]) }, enabled = idle) { Text("Mode") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { vm.showWorkerRuns(w.name) }) { Text("Tasks") }
                TextButton(onClick = onRemove, enabled = idle) { Text("Remove") }
            }
        }
    }
}
