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
import life.mygig.clauderc.api.ClusterAccount
import life.mygig.clauderc.api.TokenWindow
import life.mygig.clauderc.api.UsageWindow
import life.mygig.clauderc.api.Updates
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.padding
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt
import life.mygig.clauderc.ui.MainViewModel
import life.mygig.clauderc.ui.components.CardBox
import life.mygig.clauderc.ui.openUrl
import life.mygig.clauderc.ui.theme.BadRed
import life.mygig.clauderc.ui.theme.WarnAmber

private val MODES = listOf("acceptEdits", "plan", "bypassPermissions")
private fun modeHint(m: String) = when (m) {
    "acceptEdits" -> "edits files; runs only commands the project lists in .cluster-allowed-tools"
    "plan" -> "read-only: research and plans"
    "bypassPermissions" -> "runs anything without asking"
    else -> m
}

/** cLaudeCluster: every Claude account you're connected to, with what's left of its usage, plus the workers the main Claude hands work to. */
@Composable
fun ClusterSection(vm: MainViewModel) {
    val groups by vm.cluster.collectAsState()
    val status by vm.status.collectAsState()
    val login by vm.workerLogin.collectAsState()
    val runs by vm.workerRuns.collectAsState()
    val busy by vm.busy.collectAsState()
    var add by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var confirmRemove by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val ready = (status?.scriptApi ?: 0) >= Updates.MIN_SCRIPT_API
    LaunchedEffect(ready) { if (ready) vm.loadCluster() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("cLaudeCluster", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Usage per account. Attach workers to a chat with 👥 in that chat; the main Claude then hands them work.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                OutlinedButton(onClick = { add = true }, enabled = ready && busy == null) { Text("+ Add account") }
                TextButton(onClick = { vm.loadCluster() }, enabled = ready && busy == null) { Text("Refresh") }
            }
        }
        if (!ready) Text("Server scripts need an update (Connections tab).", style = MaterialTheme.typography.bodySmall)
        else if (groups == null) Text("Loading…", style = MaterialTheme.typography.bodySmall)
        groups?.forEach { g ->
            if (groups!!.size > 1) Text(if (g.active) "This server (${g.host})" else g.host, style = MaterialTheme.typography.labelLarge)
            if (g.accounts == null) Text(g.error.ifBlank { "Couldn't reach this server." }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            g.accounts?.forEach { a ->
                AccountCard(vm, a, canManage = g.active, onRemove = { confirmRemove = a.name })
            }
            if (!g.active) TextButton(onClick = { vm.removeFromCluster(g.serverId) }) { Text("Remove ${g.host} from cluster") }
        }
    }

    if (add) {
        AlertDialog(
            onDismissRequest = { add = false },
            confirmButton = {
                TextButton(
                    onClick = { vm.addWorker(name, role); name = ""; role = ""; add = false },
                    enabled = WORKER_NAME_RE.matches(name) && busy == null,
                ) { Text("Add and sign in") }
            },
            dismissButton = { TextButton(onClick = { add = false }) { Text("Cancel") } },
            title = { Text("Add a Claude account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("It becomes a worker on this server. Next you sign in to the account in your browser.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = name, onValueChange = { name = it.trim() }, label = { Text("Name (research, coding, ui…)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), isError = name.isNotEmpty() && !WORKER_NAME_RE.matches(name),
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    )
                    OutlinedTextField(value = role, onValueChange = { role = it }, label = { Text("What it's for") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("To use another server's account instead, add that server (top bar) and it offers to join.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }

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
private fun AccountCard(vm: MainViewModel, a: ClusterAccount, canManage: Boolean, onRemove: () -> Unit) {
    val idle = vm.busy.collectAsState().value == null   // a double tap would repeat the action (Mode would skip a step)
    val worker = a.name != "main"
    CardBox {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                (if (worker) a.name else "Main account") + (a.plan?.let { " · $it" } ?: "") + if (a.signedIn) "" else " · not signed in",
                style = MaterialTheme.typography.titleSmall,
            )
            a.email?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (a.role.isNotBlank()) Text(a.role, style = MaterialTheme.typography.bodySmall)
            if (a.signedIn) {
                val u = a.usage
                if (u == null) {
                    Text(
                        if (a.usageError == "expired") "Usage shows again after this account's next run." else "Usage unavailable.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    UsageBar("5-hour", u.fiveHour)
                    UsageBar("Weekly", u.sevenDay)
                }
                a.tokens?.let { t ->
                    Text(
                        "Tokens on this server · 5 h: ${tokenText(t.fiveHour)} · 7 days: ${tokenText(t.sevenDay)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (worker && canManage) {
                Text("Mode: ${a.mode} (${modeHint(a.mode)})", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = { vm.workerLoginStart(a.name) }, enabled = idle) { Text(if (a.signedIn) "Re-sign in" else "Sign in") }
                    OutlinedButton(onClick = { vm.setWorker(a.name, "mode", MODES[(MODES.indexOf(a.mode) + 1) % MODES.size]) }, enabled = idle) { Text("Mode") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { vm.showWorkerRuns(a.name) }) { Text("Tasks") }
                    TextButton(onClick = onRemove, enabled = idle) { Text("Remove") }
                }
            }
        }
    }
}

@Composable
private fun UsageBar(label: String, w: UsageWindow) {
    val used = (w.pct ?: return).coerceIn(0.0, 100.0)
    // The bar fills as usage is spent, so a full bar means nothing is left.
    Column {
        Text("$label: ${used.roundToInt()}% used" + (resetsIn(w.resetsAt)?.let { " · resets in $it" } ?: ""), style = MaterialTheme.typography.labelMedium)
        LinearProgressIndicator(
            progress = { (used / 100).toFloat() }, modifier = Modifier.fillMaxWidth(),
            color = when { used >= 90 -> BadRed; used >= 75 -> WarnAmber; else -> MaterialTheme.colorScheme.primary },
        )
    }
}

private fun short(n: Long) = when { n >= 1_000_000_000 -> "%.1fB".format(n / 1e9); n >= 1_000_000 -> "%.1fM".format(n / 1e6); n >= 1_000 -> "${n / 1000}k"; else -> "$n" }
private fun tokenText(w: TokenWindow) = "${short(w.input)} in, ${short(w.out)} out, ${short(w.cached)} cached"

private fun resetsIn(iso: String?): String? {
    val mins = try { Duration.between(Instant.now(), Instant.parse(iso ?: return null)).toMinutes() } catch (_: Exception) { return null }
    return when { mins <= 0 -> "now"; mins < 90 -> "$mins min"; mins < 48 * 60 -> "${(mins + 30) / 60} h"; else -> "${(mins + 720) / 1440} days" }
}

private val MODE_LABELS = listOf("acceptEdits" to "Edit files", "plan" to "Read-only", "bypassPermissions" to "Full access")

/** The chat's Cluster button: which accounts this chat can hand work to, and what each is for here. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ChatClusterSheet(vm: MainViewModel, onClose: () -> Unit) {
    val workers by vm.chatWorkers.collectAsState()
    val tasks by vm.chatTasks.collectAsState()
    val busy by vm.busy.collectAsState()
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("cLaudeCluster", style = MaterialTheme.typography.titleLarge)
            Text(
                "Pick the accounts this chat can hand work to. Each task runs on its own branch; the main Claude reviews it and merges it.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (tasks.isNotEmpty()) {
                Text("Open work", style = MaterialTheme.typography.titleSmall)
                CardBox {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        tasks.forEach { t ->
                            Column {
                                Text(t.task, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text(
                                    t.worker + " · " + if (t.status == "running") "running" else "waiting for the main Claude to merge or discard" +
                                        (t.branch?.let { " ($it)" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            val ws = workers
            if (ws == null) Text("Loading…", style = MaterialTheme.typography.bodySmall)
            else if (ws.isEmpty()) Text("No other accounts yet. Add one in the Claude tab › Accounts.", style = MaterialTheme.typography.bodySmall)
            ws?.forEach { w ->
                CardBox {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(w.name, style = MaterialTheme.typography.titleSmall)
                                if (!w.signedIn) Text("Not signed in (Claude tab › Accounts)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                            androidx.compose.material3.Switch(
                                checked = w.attached, onCheckedChange = { vm.attachWorker(w.name, it) },
                                enabled = busy == null && (w.signedIn || w.attached),
                            )
                        }
                        if (w.attached) {
                            var role by remember(w.role) { mutableStateOf(w.role) }
                            OutlinedTextField(
                                value = role, onValueChange = { role = it }, label = { Text("What it does here") }, singleLine = true,
                                modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused && role != w.role) vm.assignWorker(w.name, "role", role) },
                                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { if (role != w.role) vm.assignWorker(w.name, "role", role) }),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                MODE_LABELS.forEach { (m, label) ->
                                    androidx.compose.material3.FilterChip(selected = w.mode == m, onClick = { vm.assignWorker(w.name, "mode", m) }, label = { Text(label) }, enabled = busy == null)
                                }
                            }
                            Text(modeHint(w.mode), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
