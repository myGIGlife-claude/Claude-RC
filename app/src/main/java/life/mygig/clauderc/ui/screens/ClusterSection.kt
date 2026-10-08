package life.mygig.clauderc.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Checkbox
import androidx.compose.ui.text.input.KeyboardType
import life.mygig.clauderc.api.HOST_NAME_RE
import life.mygig.clauderc.api.HOST_USER_RE
import life.mygig.clauderc.api.hostAddressOk
import life.mygig.clauderc.api.HOST_MAX_PORTS
import life.mygig.clauderc.api.parseHostPorts
import androidx.compose.material3.Switch
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import life.mygig.clauderc.api.WORKER_NAME_RE
import life.mygig.clauderc.api.ClusterAccount
import life.mygig.clauderc.api.ClusterConfig
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
    val groups by vm.cluster.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val login by vm.workerLogin.collectAsStateWithLifecycle()
    val keyFor by vm.workerKey.collectAsStateWithLifecycle()
    var apiKey by remember { mutableStateOf("") }
    val runs by vm.workerRuns.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val config by vm.clusterConfig.collectAsStateWithLifecycle()
    var add by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("claude") }
    var code by remember { mutableStateOf("") }
    var confirmRemove by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val ready = (status?.scriptApi ?: 0) >= Updates.MIN_SCRIPT_API
    val hostAddOpen by vm.hostAddOpen.collectAsStateWithLifecycle()
    val hostSetup by vm.hostSetup.collectAsStateWithLifecycle()
    var confirmRemoveHost by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(ready) { if (ready) { vm.loadCluster(); vm.loadHosts() } }

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
        config?.let { ClusterSettingsCard(vm, it, idle = busy == null) }
        groups?.forEach { g ->
            if (groups!!.size > 1) Text(if (g.active) "This server (${g.host})" else g.host, style = MaterialTheme.typography.labelLarge)
            if (g.accounts == null) Text(g.error.ifBlank { "Couldn't reach this server." }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            g.accounts?.forEach { a ->
                AccountCard(vm, a, canManage = g.active, onRemove = { confirmRemove = a.name })
            }
            if (!g.active) TextButton(onClick = { vm.removeFromCluster(g.serverId) }) { Text("Remove ${g.host} from cluster") }
        }
        if (ready) HostsCard(vm, idle = busy == null, onRemove = { confirmRemoveHost = it })
    }

    val setup = hostSetup
    if (setup != null) HostSetupDialog(vm, setup.first, setup.second)
    else if (hostAddOpen) HostAddDialog(vm)

    confirmRemoveHost?.let { who ->
        AlertDialog(
            onDismissRequest = { confirmRemoveHost = null },
            confirmButton = { TextButton(onClick = { vm.removeHost(who); confirmRemoveHost = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmRemoveHost = null }) { Text("Keep") } },
            title = { Text("Remove $who?") },
            text = {
                Text(
                    "Forgets $who here and detaches it from every chat. The key stays authorized on that server " +
                        "until you remove it there (~/.ssh/authorized_keys).",
                )
            },
        )
    }

    if (add) {
        AlertDialog(
            onDismissRequest = { add = false },
            confirmButton = {
                TextButton(
                    onClick = { vm.addWorker(name, role, kind); name = ""; role = ""; kind = "claude"; add = false },
                    enabled = WORKER_NAME_RE.matches(name) && busy == null,
                ) { Text("Add and sign in") }
            },
            dismissButton = { TextButton(onClick = { add = false }) { Text("Cancel") } },
            title = { Text("Add an account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("It becomes a worker on this server. Next you sign in to the account in your browser.", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((k, label) in listOf("claude" to "Claude", "codex" to "ChatGPT", "gemini" to "Gemini"))
                            FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(label) })
                    }
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

    keyFor?.let { who ->
        AlertDialog(
            onDismissRequest = { vm.closeWorkerKey(); apiKey = "" },
            confirmButton = { TextButton(onClick = { vm.saveWorkerKey(who, apiKey); apiKey = "" }, enabled = apiKey.length >= 20 && busy == null) { Text("Save key") } },
            dismissButton = { TextButton(onClick = { vm.closeWorkerKey(); apiKey = "" }) { Text("Cancel") } },
            title = { Text("Gemini key for $who") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Google no longer lets the Gemini command-line tool sign in with a personal account. Paste an API key from aistudio.google.com/apikey instead.")
                    OutlinedButton(onClick = { openUrl(context, "https://aistudio.google.com/apikey") }, modifier = Modifier.fillMaxWidth()) { Text("Open Google AI Studio") }
                    OutlinedTextField(
                        value = apiKey, onValueChange = { apiKey = it.trim() }, label = { Text("API key") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    )
                }
            },
        )
    }

    login?.let { (who, url) ->
        AlertDialog(
            onDismissRequest = { vm.closeWorkerLogin(); code = "" },
            confirmButton = {
                TextButton(onClick = { vm.workerLoginCode(who, code); code = "" }, enabled = url.deviceCode != null || code.length >= 4) {
                    Text(if (url.deviceCode != null) "I've signed in" else "Submit code")
                }
            },
            dismissButton = { TextButton(onClick = { vm.closeWorkerLogin(); code = "" }) { Text("Cancel") } },
            title = { Text("Sign in $who") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Use the account meant for this worker (sign out of other accounts in your browser first, or use a private tab).")
                    OutlinedButton(onClick = { openUrl(context, url.url) }, modifier = Modifier.fillMaxWidth()) { Text("Open link") }
                    if (url.pasteUrl) Text(
                        "Sign in on the page. It ends on a page that won't load (localhost): copy its address from the browser bar and paste it below.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (url.deviceCode != null) {
                        Text("Enter this code on the page, then tap “I've signed in”:", style = MaterialTheme.typography.bodySmall)
                        Text(url.deviceCode, style = MaterialTheme.typography.headlineSmall)
                    } else
                    OutlinedTextField(
                        value = code, onValueChange = { code = it.trim() }, label = { Text(if (url.pasteUrl) "Address of the page" else "Code") },
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
private fun ClusterSettingsCard(vm: MainViewModel, c: ClusterConfig, idle: Boolean) {
    CardBox {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Cluster settings", style = MaterialTheme.typography.titleSmall)
            Text("Parallel tasks per chat", style = MaterialTheme.typography.labelLarge)
            Stepper(c.maxParallel.toString(), idle, canDown = c.maxParallel > 1, canUp = c.maxParallel < 10,
                onDown = { vm.setClusterConfig("max_parallel", (c.maxParallel - 1).toString()) },
                onUp = { vm.setClusterConfig("max_parallel", (c.maxParallel + 1).toString()) })
            Text("How many tasks one chat may hand to workers at the same time.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Hand work back to the main Claude near the limit", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                androidx.compose.material3.Switch(checked = c.handback, onCheckedChange = { vm.setClusterConfig("handback", it.toString()) }, enabled = idle)
            }
            Text(
                "When a worker's 5-hour usage reaches the percentage below, the main Claude stops delegating and does the work itself.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (c.handback) {
                Stepper("Hand back at ${c.handbackPct}%", idle, canDown = c.handbackPct > 50, canUp = c.handbackPct < 100,
                    onDown = { vm.setClusterConfig("handback_pct", (c.handbackPct - 5).coerceAtLeast(50).toString()) },
                    onUp = { vm.setClusterConfig("handback_pct", (c.handbackPct + 5).coerceAtMost(100).toString()) })
            }
        }
    }
}

/** Hosting servers: web servers chats can work on (docs/hosts-design.md). */
@Composable
private fun HostsCard(vm: MainViewModel, idle: Boolean, onRemove: (String) -> Unit) {
    val st by vm.status.collectAsStateWithLifecycle()
    val authTarget by vm.hostAuthTarget.collectAsStateWithLifecycle()
    authTarget?.let { HostAuthorizeDialog(vm, it) }
    val hosts by vm.hosts.collectAsStateWithLifecycle()
    CardBox {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Hosting servers", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { vm.openHostAdd() }, enabled = idle) { Text("+ Add host") }
            }
            Text(
                "Web servers chats can work on: attach one with 👥 in a chat. Claude runs commands there in its own folder.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val hs = hosts
            if (hs == null) Text("Loading…", style = MaterialTheme.typography.bodySmall)
            else if (hs.isEmpty()) Text("No servers yet.", style = MaterialTheme.typography.bodySmall)
            hs?.forEach { h ->
                Column {
                    Text(h.name, style = MaterialTheme.typography.labelLarge)
                    Text(
                        "${h.user}@${h.address}" + (if (h.port != 22) ":${h.port}" else "") +
                            if (h.auth == "password") " · key installed with a password" else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Done once (and every step went through): say so instead of offering it again; "Run set-up again" stays in the menu.
                    val setupDone = h.setup?.let { it.ok && "harden" in it.steps } == true
                    if (setupDone) Text(
                        "Set up ✓ (" + h.setup!!.steps.joinToString(", ") + ")",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                    )
                    var menu by remember { mutableStateOf(false) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { vm.testHost(h.name) }, enabled = idle) { Text("Test", maxLines = 1) }
                        if (!setupDone) TextButton(onClick = { vm.openHostSetup(h.name, h.port) }, enabled = idle) { Text("Set up", maxLines = 1) }
                        Box {
                            IconButton(onClick = { menu = true }, enabled = idle) { Icon(Icons.Filled.MoreVert, contentDescription = "More for ${h.name}") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                if ((st?.scriptApi ?: 0) >= 44) DropdownMenuItem(text = { Text("Add my key") }, onClick = { menu = false; vm.openHostAuthorize(h.name) })
                                if (setupDone) DropdownMenuItem(text = { Text("Run set-up again") }, onClick = { menu = false; vm.openHostSetup(h.name, h.port) })
                                DropdownMenuItem(text = { Text("Remove") }, onClick = { menu = false; onRemove(h.name) })
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Add host: an optional public key to generate first (for providers), Check reads the host key to confirm, then a private key
 *  (pasted or a file), a one-time password or the generated key. Save moves on to [HostSetupDialog]. */
@Composable
private fun HostAddDialog(vm: MainViewModel) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val probe by vm.hostProbe.collectAsStateWithLifecycle()
    val error by vm.hostError.collectAsStateWithLifecycle()
    val hostKey by vm.hostKey.collectAsStateWithLifecycle()
    val draft = remember { vm.hostDraft() }   // what was typed before the app was left (no secrets)
    var name by rememberSaveable { mutableStateOf(draft.name) }
    var address by rememberSaveable { mutableStateOf(draft.address) }
    var port by rememberSaveable { mutableStateOf(draft.port) }
    var user by rememberSaveable { mutableStateOf(draft.user) }
    var confirmed by remember { mutableStateOf(false) }
    var auth by rememberSaveable { mutableStateOf(draft.auth) }
    LaunchedEffect(name, address, port, user, auth) { vm.saveHostDraft(MainViewModel.HostDraft(name, address, port, user, auth)) }
    var keyText by remember { mutableStateOf("") }
    var keyFile by remember { mutableStateOf<String?>(null) }   // base64 of a picked key file
    var password by remember { mutableStateOf("") }
    var fileError by remember { mutableStateOf<String?>(null) }
    // "Use existing key" at the top: the owner's own key, imported as the pending key (shown like a generated one).
    var importOpen by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var importFile by remember { mutableStateOf<String?>(null) }
    var importFileError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val pickKey = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val b64 = keyFileBase64(context, uri)
            if (b64 == null) {
                fileError = "That file couldn't be read (or it's over 20 KB)."
            } else {
                keyFile = b64
                keyText = ""
                fileError = null
            }
        }
    }
    val pickImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val b64 = keyFileBase64(context, uri)
            if (b64 == null) {
                importFileError = "That file couldn't be read (or it's over 20 KB)."
            } else {
                importFile = b64
                importText = ""
                importFileError = null
            }
        }
    }
    val portNum = port.toIntOrNull()
    val addressOk = hostAddressOk(address) && portNum != null && portNum in 1..65535
    val p = probe
    val generated = hostKey?.takeIf { it.first == name }?.second   // made (or imported) for this name only
    LaunchedEffect(generated) {
        if (generated != null) { importOpen = false; importText = ""; importFile = null; importFileError = null }
    }
    val importSecret = when {
        importFile != null -> importFile!!
        importText.isNotBlank() -> keyTextBase64(importText)
        else -> ""
    }
    // A key made or imported above is THE sign-in: no choice, no password to type.
    val mode = if (generated != null) "generated" else if (auth == "password") "password" else "key"
    val secret = when {
        mode == "generated" -> "-"
        mode == "password" -> password
        keyFile != null -> keyFile!!
        keyText.isNotBlank() -> keyTextBase64(keyText)
        else -> ""
    }
    val canSave = p != null && confirmed && HOST_NAME_RE.matches(name) && HOST_USER_RE.matches(user) && secret.isNotEmpty() && busy == null
    val scroll = rememberScrollState()
    LaunchedEffect(error) { if (error != null) scroll.animateScrollTo(scroll.maxValue) }   // the message is at the bottom of a tall dialog: bring it into view
    AlertDialog(
        onDismissRequest = { if (busy == null) vm.closeHostAdd(keepDraft = true) },
        confirmButton = {
            if (p == null) TextButton(onClick = { vm.probeHost(address, portNum ?: 22) }, enabled = addressOk && busy == null) { Text("Check") }
            else TextButton(onClick = { vm.addHost(name, address, portNum ?: 22, user, p.fingerprint, mode, secret) }, enabled = canSave) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = { vm.closeHostAdd() }) { Text("Cancel") } },
        title = { Text("Add a server") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NoAutofill()
                OutlinedTextField(
                    value = name, onValueChange = { if (it.trim() != name) vm.clearHostKey(); name = it.trim() }, label = { Text("Name (shop, blog…)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), isError = name.isNotEmpty() && !HOST_NAME_RE.matches(name),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                )
                if (generated == null) {
                    Text(
                        "Need a public key for your provider first (e.g. OVH)? Generate a new key or use one you have, copy its public key, then fill in the rest.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { importOpen = false; vm.generateHostKey(name) }, enabled = HOST_NAME_RE.matches(name) && busy == null, modifier = Modifier.weight(1f)) {
                            Text("Generate new key")
                        }
                        OutlinedButton(onClick = { importOpen = !importOpen }, enabled = busy == null, modifier = Modifier.weight(1f)) {
                            Text("Use existing key")
                        }
                    }
                    if (importOpen) {
                        OutlinedTextField(
                            value = importText, onValueChange = { importText = it; importFile = null }, label = { Text("Your private key (paste)") },
                            maxLines = 4, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                        )
                        OutlinedButton(onClick = { pickImport.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (importFile != null) "Key file chosen ✓" else "Or choose a key file")
                        }
                        importFileError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                        Text("Keys with a passphrase aren't supported. The key is kept on this server only and never shown again.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(
                            onClick = { vm.importHostKey(name, importSecret) },
                            enabled = HOST_NAME_RE.matches(name) && importSecret.isNotEmpty() && busy == null, modifier = Modifier.fillMaxWidth(),
                        ) { Text("Use this key") }
                    }
                } else {
                    SelectionContainer(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(8.dp)) {
                        Text(generated.publicKey, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(generated.fingerprint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        TextButton(onClick = { copy(context, "Public key", generated.publicKey) }) { Text("Copy") }
                    }
                    Text(
                        "Paste this into your provider's 'public SSH key' field (e.g. OVH when you install the server), or append it to ~/.ssh/authorized_keys for that user. Then fill in the rest, Check and Save.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = address, onValueChange = { address = it.trim(); confirmed = false; vm.clearHostProbe() }, label = { Text("Address") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), isError = address.isNotEmpty() && !hostAddressOk(address),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                )
                OutlinedTextField(
                    value = port, onValueChange = { port = it.filter { c -> c.isDigit() }.take(5); confirmed = false; vm.clearHostProbe() }, label = { Text("Port") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), isError = portNum == null || portNum !in 1..65535,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = user, onValueChange = { user = it.trim() }, label = { Text("User (a deploy user, not root)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), isError = user.isNotEmpty() && !HOST_USER_RE.matches(user),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                )
                if (p != null) {
                    Text("Host key (${p.keytype}):", style = MaterialTheme.typography.labelLarge)
                    Text(p.fingerprint, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Compare it with `ssh-keygen -lf` of the host key on that server. A different key means you're not talking to your server.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                        Text("This is my server's key")
                    }
                    if (confirmed) {
                        if (generated == null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = mode == "key", onClick = { auth = "key" }, label = { Text("Key") })
                            FilterChip(selected = mode == "password", onClick = { auth = "password" }, label = { Text("Password") })
                        }
                        if (mode == "generated") {
                            Text(
                                "Signs in with the key shown above (no password needed). Make sure its public key is on the server or in your provider's key box, then Save.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else if (mode == "key") {
                            OutlinedTextField(
                                value = keyText, onValueChange = { keyText = it; keyFile = null }, label = { Text("Private key (paste)") },
                                maxLines = 4, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                            )
                            OutlinedButton(onClick = { pickKey.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (keyFile != null) "Key file chosen ✓" else "Or choose a key file")
                            }
                            fileError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                            Text("Keys with a passphrase aren't supported. The key is kept on this server only and never shown again.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            OutlinedTextField(
                                value = password, onValueChange = { password = it.replace("\n", "") }, label = { Text("Password") },
                                singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                            )
                            Text("Used once to install this server's own key, then forgotten.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
    )
}

/** A picked private key file as base64 on one line, or null if it can't be read or is over 20 KB. */
private fun keyFileBase64(context: android.content.Context, uri: android.net.Uri): String? {
    val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
    if (bytes == null || bytes.isEmpty() || bytes.size > 20_000) return null
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
}

/** A pasted private key as base64 on one line (ssh wants the final newline). */
private fun keyTextBase64(text: String): String = Base64.encodeToString((text.trim() + "\n").toByteArray(), Base64.NO_WRAP)

/** "Set up this server": host-harden opens the ports and runs the chosen steps (idempotent, safe to re-run). */
@Composable
private fun HostSetupDialog(vm: MainViewModel, name: String, sshPort: Int) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val result by vm.hostHarden.collectAsStateWithLifecycle()
    val error by vm.hostSetupError.collectAsStateWithLifecycle()
    var harden by remember { mutableStateOf(true) }
    var tune by remember { mutableStateOf(true) }
    var web by remember { mutableStateOf(false) }
    var http by remember { mutableStateOf(true) }
    var https by remember { mutableStateOf(true) }
    var more by remember { mutableStateOf("") }
    val extra = parseHostPorts(more)
    val ports = ((if (http) listOf("80") else emptyList()) + (if (https) listOf("443") else emptyList()) + extra.orEmpty()).distinct()
    val portsOk = extra != null && ports.size <= HOST_MAX_PORTS
    val steps = listOfNotNull(if (harden) "harden" else null, if (tune) "optimize" else null, if (web) "web" else null)
    val r = result
    AlertDialog(
        onDismissRequest = { if (busy == null) vm.closeHostSetup() },
        confirmButton = {
            if (r != null) TextButton(onClick = { vm.closeHostSetup() }) { Text("Done") }
            else TextButton(onClick = { vm.hardenHost(name, ports, steps) }, enabled = portsOk && busy == null) { Text("Set up") }
        },
        dismissButton = { if (r == null) TextButton(onClick = { vm.closeHostSetup() }, enabled = busy == null) { Text("Skip") } },
        title = { Text("Set up $name") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (r == null) {
                    SetupSwitch("Harden the server (recommended)", harden, busy == null) { harden = it }
                    SetupSwitch("Tune it (time sync, log size, swap)", tune, busy == null) { tune = it }
                    SetupSwitch("Install web basics (Caddy, git, build tools)", web, busy == null) { web = it }
                    Text("Open ports", style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = true, onCheckedChange = null, enabled = false)
                        Text("SSH $sshPort (always)")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = http, onCheckedChange = { http = it }, enabled = busy == null)
                        Text("HTTP 80")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = https, onCheckedChange = { https = it }, enabled = busy == null)
                        Text("HTTPS 443")
                    }
                    OutlinedTextField(
                        value = more, onValueChange = { more = it.replace("\n", "") }, label = { Text("More ports (8080, 51820/udp)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), isError = !portsOk, enabled = busy == null,
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    )
                    if (!portsOk) Text("Comma separated, 1-65535, optional /tcp or /udp, up to $HOST_MAX_PORTS in all.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    if (busy != null) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Setting up… this can take a few minutes. If the new settings would lock the app out, the server undoes them.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    r.steps.forEach { s ->
                        val (mark, color) = when (s.status) {
                            "ok" -> "✓" to MaterialTheme.colorScheme.primary
                            "failed" -> "✗" to MaterialTheme.colorScheme.error
                            else -> "–" to MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Column {
                            Text("$mark ${s.name}", style = MaterialTheme.typography.labelLarge, color = color)
                            if (s.detail.isNotBlank()) Text(s.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (r.docker) Text(
                        "Docker is installed: its published ports bypass the firewall. Publish container ports on 127.0.0.1 (behind Caddy).",
                        style = MaterialTheme.typography.bodySmall, color = WarnAmber,
                    )
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
    )
}

@Composable
private fun SetupSwitch(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** A minus button, a label and a plus button in a row. */
@Composable
private fun Stepper(label: String, enabled: Boolean, canDown: Boolean, canUp: Boolean, onDown: () -> Unit, onUp: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onDown, enabled = enabled && canDown) { Text("−") }
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
        OutlinedButton(onClick = onUp, enabled = enabled && canUp) { Text("+") }
    }
}

@Composable
private fun AccountCard(vm: MainViewModel, a: ClusterAccount, canManage: Boolean, onRemove: () -> Unit) {
    val idle = vm.busy.collectAsStateWithLifecycle().value == null   // a double tap would repeat the action (Mode would skip a step)
    val worker = a.name != "main"
    CardBox {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                (if (worker) a.name else "Main account") + (if (a.kind == "codex") " · ChatGPT" else if (a.kind == "gemini") " · Gemini" else "") + (a.plan?.let { " · $it" } ?: "") + if (a.signedIn) "" else " · not signed in",
                style = MaterialTheme.typography.titleSmall,
            )
            a.email?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (a.role.isNotBlank()) Text(a.role, style = MaterialTheme.typography.bodySmall)
            if (a.signedIn) {
                val u = a.usage
                if (u == null) {
                    Text(
                        if (a.kind != "claude") "No usage numbers for this account."
                        else if (a.usageError == "expired") "Usage shows again after this account's next run." else "Usage unavailable.",
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
                    OutlinedButton(onClick = { vm.workerLoginStart(a.name, a.kind) }, enabled = idle) { Text(if (a.kind == "gemini") (if (a.signedIn) "Change key" else "Add key") else if (a.signedIn) "Re-sign in" else "Sign in") }
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
    val workers by vm.chatWorkers.collectAsStateWithLifecycle()
    val tasks by vm.chatTasks.collectAsStateWithLifecycle()
    val hosts by vm.chatHosts.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
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
            Text("Servers", style = MaterialTheme.typography.titleSmall)
            Text(
                "Web servers this chat's Claude may run commands on, in its own folder there.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val hs = hosts
            if (hs == null) Text("Loading…", style = MaterialTheme.typography.bodySmall)
            else if (hs.isEmpty()) Text("No servers yet. Add one in the Claude tab › Hosting servers.", style = MaterialTheme.typography.bodySmall)
            hs?.forEach { h ->
                CardBox {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(h.name, style = MaterialTheme.typography.titleSmall)
                            Text("${h.user}@${h.address}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        androidx.compose.material3.Switch(checked = h.attached, onCheckedChange = { vm.attachHost(h.name, it) }, enabled = busy == null)
                    }
                }
            }
        }
    }
}

/** "Add my key": paste the public key (the one-line .pub file) of a phone terminal app or a PC; the server adds it to the host account. */
@Composable
private fun HostAuthorizeDialog(vm: MainViewModel, name: String) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.hostAuthError.collectAsStateWithLifecycle()
    val done by vm.hostAuthDone.collectAsStateWithLifecycle()
    var key by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (busy == null) vm.closeHostAuthorize() },
        confirmButton = {
            if (done != null) TextButton(onClick = { vm.closeHostAuthorize() }) { Text("Done") }
            else TextButton(onClick = { vm.authorizeHostKey(name, key) }, enabled = key.trim().length >= 40 && busy == null) { Text("Add key") }
        },
        dismissButton = { if (done == null) TextButton(onClick = { vm.closeHostAuthorize() }) { Text("Cancel") } },
        title = { Text("Add my key to $name") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Paste the PUBLIC key of the app or computer you want to sign in from (one line starting with ssh-ed25519 or ssh-rsa, from the .pub file). " +
                        "It is added to this server's account so you can SSH in directly. Never paste a private key here.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = key, onValueChange = { key = it }, label = { Text("Public key") }, maxLines = 4, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                )
                done?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
    )
}
