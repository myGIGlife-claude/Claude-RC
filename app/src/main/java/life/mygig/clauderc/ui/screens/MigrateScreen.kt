package life.mygig.clauderc.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import life.mygig.clauderc.api.MigratePlan
import life.mygig.clauderc.api.MigrateStatus
import life.mygig.clauderc.data.Server
import life.mygig.clauderc.ui.MainViewModel
import life.mygig.clauderc.ui.MigrateSignInPrompt
import life.mygig.clauderc.ui.MigrateSignOut
import life.mygig.clauderc.ui.MigrateStep
import life.mygig.clauderc.ui.MigrateUi
import life.mygig.clauderc.ui.components.CardBox
import life.mygig.clauderc.ui.components.Health
import life.mygig.clauderc.ui.components.InfoRows
import life.mygig.clauderc.ui.components.SectionLabel
import life.mygig.clauderc.ui.components.StatusDot
import life.mygig.clauderc.ui.openUrl
import life.mygig.clauderc.ui.theme.BadRed
import life.mygig.clauderc.ui.theme.OkGreen

/** The wizard's visible steps (DONE shares the last one). */
private val MIGRATE_STEPS = listOf(
    MigrateStep.CHOOSE to "Choose servers",
    MigrateStep.CHECK to "Check",
    MigrateStep.STOP_SESSIONS to "Stop sessions",
    MigrateStep.CREATE_USER to "Create the user",
    MigrateStep.TRANSFER to "Transfer",
    MigrateStep.RESTORE to "Restore",
    MigrateStep.SIGN_IN to "Sign in again",
    MigrateStep.SIGN_OUT to "Sign out of the old server",
    MigrateStep.REBOOT to "Reboot",
    MigrateStep.VERIFY to "Verify",
)

private fun migrateServerLabel(s: Server) = "${s.host} (${s.user})"

/** Move everything from one server to another. The state lives in the view model, so it survives rotation. */
@Composable
fun MigrateScreen(vm: MainViewModel) {
    val ui by vm.migrate.collectAsState()
    val settings by vm.settings.collectAsState()
    val servers = settings?.servers.orEmpty().filter { it.isConfigured }
    val scroll = rememberScrollState()
    LaunchedEffect(ui.step) { scroll.scrollTo(0) }
    val done = ui.step == MigrateStep.DONE
    val index = if (done) MIGRATE_STEPS.size else MIGRATE_STEPS.indexOfFirst { it.first == ui.step } + 1
    val title = if (done) "Done" else MIGRATE_STEPS.firstOrNull { it.first == ui.step }?.second.orEmpty()
    Dialog(onDismissRequest = { vm.migrateClose() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        DialogKeyboardFix()
        NoAutofill()
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { vm.migrateClose() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close") }
                    Column(Modifier.weight(1f)) {
                        Text("Migrate", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        Text(
                            "Step $index of ${MIGRATE_STEPS.size}: $title",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                LinearProgressIndicator(progress = { index / MIGRATE_STEPS.size.toFloat() }, modifier = Modifier.fillMaxWidth())
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (ui.busy) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(ui.log.lastOrNull() ?: "Working…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    ui.error?.let { MigrateError(it, ui.busy, { vm.migrateReset() }) { vm.migrateRetry() } }
                    ui.loginKeyPem?.let { MigrateLoginKey(vm, it, ui.loginKeyFor) }
                    when (ui.step) {
                        MigrateStep.CHOOSE -> MigrateChoose(vm, ui, servers)
                        MigrateStep.CHECK -> MigrateCheck(vm, ui)
                        MigrateStep.STOP_SESSIONS -> MigrateStopSessions(vm, ui)
                        MigrateStep.CREATE_USER -> MigrateCreateUser(vm, ui)
                        MigrateStep.TRANSFER, MigrateStep.RESTORE -> MigrateProgress(vm, ui)
                        MigrateStep.SIGN_IN -> MigrateSignInStep(vm, ui)
                        MigrateStep.SIGN_OUT -> MigrateSignOutStep(vm, ui)
                        MigrateStep.REBOOT -> MigrateReboot(vm, ui)
                        MigrateStep.VERIFY, MigrateStep.DONE -> MigrateVerify(vm, ui)
                    }
                }
            }
        }
    }
}

// ---- Shared pieces ---------------------------------------------------------------

@Composable
private fun MigrateNotice(container: Color, content: Color, body: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = container, contentColor = content) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = body)
    }
}

@Composable
private fun MigrateWarning(body: @Composable ColumnScope.() -> Unit) =
    MigrateNotice(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, body)

@Composable
private fun MigrateError(text: String, busy: Boolean, onStartOver: (() -> Unit)? = null, onRetry: () -> Unit) {
    MigrateNotice(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer) {
        SelectionContainer { Text(text, style = MaterialTheme.typography.bodyMedium) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onRetry, enabled = !busy) { Text("Try again") }
            if (onStartOver != null) OutlinedButton(onClick = onStartOver, enabled = !busy) { Text("Start over") }
        }
    }
}

@Composable
private fun MigrateHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ---- 1. Choose ---------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MigrateServerPicker(label: String, selected: Server?, servers: List<Server>, enabled: Boolean, onPick: (Server) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it && enabled }) {
        OutlinedTextField(
            value = selected?.let { migrateServerLabel(it) }.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            servers.forEach { srv ->
                DropdownMenuItem(text = { Text(migrateServerLabel(srv)) }, onClick = { open = false; onPick(srv) })
            }
        }
    }
}

@Composable
private fun MigrateChoose(vm: MainViewModel, ui: MigrateUi, servers: List<Server>) {
    var userName by rememberSaveable { mutableStateOf(ui.userName) }
    val from = ui.from
    val to = ui.to
    val same = from != null && to != null && from.id == to.id
    Text("Move everything from one server to another: sessions, projects, settings and logins.", style = MaterialTheme.typography.bodyMedium)
    if (servers.size < 2) MigrateHint("The new server isn't in the app yet: add it first (host, user, trust its key), then come back here.")
    OutlinedButton(
        onClick = { vm.migrateClose(); vm.addServer() },
        enabled = !ui.busy, modifier = Modifier.fillMaxWidth(),
    ) { Text("Add the new server") }
    MigrateServerPicker("Move from", from, servers, !ui.busy) { vm.migrateSetServers(it.id, to?.id.orEmpty()) }
    MigrateServerPicker("Move to", to, servers, !ui.busy) { vm.migrateSetServers(from?.id.orEmpty(), it.id) }
    if (same) Text("Pick two different servers.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(
        value = userName,
        onValueChange = { userName = it; vm.migrateSetUser(it) },
        label = { Text("User to run Claude as on the new server") },
        singleLine = true,
        isError = !ui.userNameValid,
        enabled = !ui.busy,
        supportingText = {
            Text(
                if (ui.userNameValid) "Created on the new server if it doesn't exist. Needs sudo there."
                else "Lowercase letters, digits, '_' and '-', up to 31, starting with a letter.",
            )
        },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
    )
    Button(
        onClick = { vm.migrateStart() },
        enabled = !ui.busy && from != null && to != null && !same && ui.userNameValid && userName.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Next") }
}

// ---- 2. Check ----------------------------------------------------------------------

@Composable
private fun MigrateCheck(vm: MainViewModel, ui: MigrateUi) {
    val plan: MigratePlan? = ui.plan
    if (plan == null) {
        Text("Checking ${ui.from?.host.orEmpty()}…", style = MaterialTheme.typography.bodyMedium)
    } else {
        val logins = buildList {
            if (plan.logins.claude) add("Claude")
            addAll(plan.logins.workers)
        }
        CardBox {
            InfoRows(
                listOf(
                    "Sessions" to "${plan.sessions.size}",
                    "Size" to "about ${plan.estimateMb} MB",
                    "Sign in again" to logins.joinToString(", ").ifBlank { "none" },
                    "Services" to plan.services.joinToString(", ").ifBlank { "none" },
                ),
            )
        }
        val onlyHere = plan.repos.filter { it.state != "clean" && it.rescued }
        if (onlyHere.isNotEmpty()) {
            SectionLabel("Work that exists only on the old server")
            CardBox {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    onlyHere.forEach { r ->
                        Column {
                            Text(r.dir, style = MaterialTheme.typography.bodyMedium)
                            Text(r.state + " · copied", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        val skipped = plan.repos.filter { it.skippedReason.isNotBlank() }
        if (plan.dockerVolumes.isNotEmpty() || plan.otherDirs.isNotEmpty() || skipped.isNotEmpty()) {
            MigrateWarning {
                Text("Not copied", fontWeight = FontWeight.SemiBold)
                if (plan.dockerVolumes.isNotEmpty()) Text("Docker volumes: " + plan.dockerVolumes.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                if (plan.otherDirs.isNotEmpty()) Text("Other folders: " + plan.otherDirs.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                skipped.forEach { Text("${it.dir}: ${it.skippedReason}", style = MaterialTheme.typography.bodySmall) }
                Text("Move these by hand if you need them.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { vm.migrateEdit() }, enabled = !ui.busy, modifier = Modifier.weight(1f)) { Text("Back") }
        Button(onClick = { vm.migrateConfirmPlan() }, enabled = !ui.busy && plan != null, modifier = Modifier.weight(1.6f)) { Text("Looks right, continue") }
    }
}

// ---- 3. Stop sessions --------------------------------------------------------------

@Composable
private fun MigrateStopSessions(vm: MainViewModel, ui: MigrateUi) {
    Text(
        "Sessions running on ${ui.from?.host.orEmpty()} have to stop before anything is copied. Idle ones are stopped for you.",
        style = MaterialTheme.typography.bodyMedium,
    )
    if (ui.sessions.isEmpty()) {
        Text(if (ui.busy) "Checking…" else "No session is running.", style = MaterialTheme.typography.bodyMedium)
    }
    ui.sessions.forEach { s ->
        CardBox {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusDot(if (s.waiting) Health.BAD else if (s.busy) Health.WARN else Health.OK)
                    Text(s.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                }
                when {
                    s.waiting -> MigrateHint("Waiting for your answer. Answer it (or stop it), then tap Wait. Stopping it now drops the question.")
                    s.busy -> MigrateHint("Working right now. Let it finish and tap Wait, or stop it and lose what it is doing.")
                    else -> MigrateHint("Idle: stopping…")
                }
                if (s.busy || s.waiting) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.migrateRefreshSessions() }, enabled = !ui.busy) { Text("Wait") }
                        TextButton(onClick = { vm.migrateStopSession(s.name) }, enabled = !ui.busy) {
                            Text("Stop anyway", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
    OutlinedButton(onClick = { vm.migrateStopIdle() }, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) { Text("Refresh") }
}

// ---- 4. Create the user, 9. Reboot (both need sudo on the new server) -------------

/**
 * What sudo on the new server allows, and the button that needs it. The password stays in this
 * composable's own state (never saved, never in the view model's state).
 */
@Composable
private fun MigrateSudo(vm: MainViewModel, ui: MigrateUi, actionLabel: String, onAction: (String) -> Unit, extra: @Composable () -> Unit = {}) {
    var password by remember { mutableStateOf("") }
    // The flow checks sudo itself on entering this step; check again once it is idle and still has no answer.
    LaunchedEffect(ui.busy) {
        if (!ui.busy && ui.sudoMode == null && ui.error == null) vm.migrateCheckSudo()
    }
    val host = ui.to?.host.orEmpty()
    when (val mode = ui.sudoMode) {
        null -> {
            Text(if (ui.busy) "Checking sudo on $host…" else "sudo on $host isn't checked yet.", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { vm.migrateCheckSudo() }, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) { Text("Check sudo") }
        }
        "none" -> {
            MigrateWarning {
                Text(
                    "This user can't use sudo on the new server. Sign in as a user that can, or ask for sudo to be allowed, " +
                        "or move into a folder of the current user instead.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            OutlinedButton(onClick = { vm.migrateCheckSudo() }, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) { Text("Check again") }
            extra()
        }
        else -> {
            val needsPassword = mode == "password_needed" || mode == "password_ok"
            if (needsPassword) {
                MigrateHint(
                    if (mode == "password_ok") "The sudo password works. It is only sent for this step and never saved."
                    else "sudo on $host needs a password. It is only sent for this step and never saved.",
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("sudo password on $host") }, singleLine = true, enabled = !ui.busy,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                )
                if (mode == "password_needed") {
                    OutlinedButton(
                        onClick = { vm.migrateCheckSudo(password) },
                        enabled = !ui.busy && password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Check password") }
                }
            } else {
                MigrateHint(if (mode == "root") "Signed in as root on $host: no password needed." else "sudo works without a password on $host.")
            }
            Button(
                onClick = { onAction(if (needsPassword) password else "") },
                enabled = !ui.busy && (!needsPassword || password.isNotBlank()),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(actionLabel) }
            extra()
        }
    }
}

@Composable
private fun MigrateCreateUser(vm: MainViewModel, ui: MigrateUi) {
    val host = ui.to?.host.orEmpty()
    Text(
        "Claude will run as ${ui.userName} on $host, and that user doesn't exist there yet. Creating it installs Claude Code and takes a few minutes.",
        style = MaterialTheme.typography.bodyMedium,
    )
    MigrateCheckRow("Also make a key to log in from a computer (ssh)", ui.makeLoginKey, !ui.busy) { vm.migrateSetLoginKey(it) }
    MigrateHint(
        "The app already reaches ${ui.userName} with the phone's own key. This extra key is for your PC: you get the file to save right after the user is made.",
    )
    MigrateSudo(vm, ui, "Create ${ui.userName} on $host", { pw -> vm.migrateCreateUser(pw) })
}

/** The private login key made for the new user: the user saves the file (the app keeps no copy). */
@Composable
private fun MigrateLoginKey(vm: MainViewModel, pem: String, forWho: String) {
    val context = LocalContext.current
    var note by remember { mutableStateOf<String?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            note = runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(pem.toByteArray()) } ?: error("no output") }
                .fold({ "Saved." }, { "Couldn't save: ${it.message}" })
        }
    }
    MigrateWarning {
        Text("Your login key for $forWho", style = MaterialTheme.typography.titleMedium)
        Text(
            "Save this key file now: it is shown only here and the app doesn't keep it. On your computer: chmod 600 the file, then ssh -i <file> $forWho",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { save.launch("clauderc-${forWho.substringBefore('@')}.key") }, modifier = Modifier.weight(1f)) { Text("Save key file") }
            OutlinedButton(
                onClick = { copy(context, "ssh key", pem); note = "Copied." },
                modifier = Modifier.weight(1f),
            ) { Text("Copy") }
        }
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        TextButton(onClick = { vm.migrateLoginKeySaved() }) { Text("I saved it, hide this") }
    }
}

// ---- 5. Transfer, 6. Restore -------------------------------------------------------

private fun migratePhaseText(phase: String) = when (phase) {
    "export" -> "Packing on the old server"
    "transfer" -> "Sending to the new server"
    "restore" -> "Restoring on the new server"
    "done" -> "Done"
    "failed" -> "Failed"
    else -> "Starting"
}

@Composable
private fun MigrateProgress(vm: MainViewModel, ui: MigrateUi) {
    // Starts the transfer once the user step is over (the flow can still be finishing when this appears).
    // After that the flow moves on by itself; a failure waits for "Try again".
    LaunchedEffect(ui.step, ui.busy) {
        if (ui.step == MigrateStep.TRANSFER && !ui.busy && ui.error == null) vm.migrateTransfer()
    }
    val st: MigrateStatus? = ui.status
    CardBox {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (ui.step == MigrateStep.RESTORE && st?.phase.isNullOrBlank()) "Restoring on the new server" else migratePhaseText(st?.phase.orEmpty()),
                style = MaterialTheme.typography.titleMedium,
            )
            if (st != null && st.message.isNotBlank()) Text(st.message, style = MaterialTheme.typography.bodyMedium)
            if (st != null && st.pct > 0) {
                LinearProgressIndicator(progress = { st.pct.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth())
                Text("${st.pct}%", style = MaterialTheme.typography.labelMedium)
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (st != null && st.bytes > 0) {
                MigrateHint("%.1f MB".format(st.bytes / 1_048_576.0))
            }
        }
    }
    MigrateHint("This takes a while for big servers. You can leave the phone: it carries on, and Migrate picks up where it is when you open it again.")
    val lines = ui.log.takeLast(12)
    if (lines.isNotEmpty()) {
        val logScroll = rememberScrollState()
        LaunchedEffect(ui.log.size) { logScroll.scrollTo(logScroll.maxValue) }
        CardBox {
            Text(
                lines.joinToString("\n"),
                fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                modifier = Modifier.heightIn(max = 220.dp).verticalScroll(logScroll),
            )
        }
    }
}

// ---- 7. Sign in --------------------------------------------------------------------

@Composable
private fun MigrateSignInStep(vm: MainViewModel, ui: MigrateUi) {
    var confirmSkip by remember { mutableStateOf(false) }
    val prompt = ui.signInPrompt
    Text(
        "Claude logins aren't copied (two servers using one login would sign each other out). Sign in again on ${ui.to?.host.orEmpty()}.",
        style = MaterialTheme.typography.bodyMedium,
    )
    if (ui.signInTargets.isEmpty()) Text("Nothing to sign in.", style = MaterialTheme.typography.bodyMedium)
    ui.signInTargets.forEach { t ->
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (t.done) "✓" else "•", color = if (t.done) OkGreen else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                Text(if (t.main) "Claude (main account)" else t.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { vm.migrateSignInStart(t.name, t.main) }, enabled = !ui.busy && prompt == null) {
                    Text(if (t.done) "Sign in again" else "Sign in")
                }
            }
        }
    }
    if (prompt != null) MigrateSignInCard(vm, prompt, ui.busy)
    Button(
        onClick = { vm.migrateSignInFinish(false) },
        enabled = !ui.busy && prompt == null && ui.signInTargets.all { it.done },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Continue") }
    if (ui.signInTargets.any { !it.done }) {
        TextButton(onClick = { confirmSkip = true }, enabled = !ui.busy && prompt == null, modifier = Modifier.fillMaxWidth()) { Text("Skip the rest") }
    }
    if (confirmSkip) {
        AlertDialog(
            onDismissRequest = { confirmSkip = false },
            title = { Text("Skip the rest?") },
            text = { Text("Those accounts stay signed out on the new server until you sign in there (Claude tab › Accounts).") },
            confirmButton = { TextButton(onClick = { confirmSkip = false; vm.migrateSignInFinish(true) }) { Text("Skip") } },
            dismissButton = { TextButton(onClick = { confirmSkip = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MigrateSignInCard(vm: MainViewModel, prompt: MigrateSignInPrompt, busy: Boolean) {
    val context = LocalContext.current
    // Not saved: a new page means a new code.
    var code by remember(prompt.login.url) { mutableStateOf("") }
    CardBox {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Sign in ${if (prompt.main) "Claude" else prompt.name}", style = MaterialTheme.typography.titleSmall)
            Text("1. Open the link and approve the login.", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { openUrl(context, prompt.login.url) }, modifier = Modifier.fillMaxWidth()) { Text("Open link") }
            Text("2. Paste the code it shows you.", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = code, onValueChange = { code = it.trim() },
                label = { Text("Code") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { vm.migrateSignInCancel() }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = { vm.migrateSignInCode(prompt.name, prompt.main, code) },
                    enabled = !busy && code.length >= 4,
                    modifier = Modifier.weight(1f),
                ) { Text("Done") }
            }
        }
    }
}

// ---- 8. Sign out of the old server -------------------------------------------------

@Composable
private fun MigrateCheckRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange, enabled = enabled)
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MigrateSignOutStep(vm: MainViewModel, ui: MigrateUi) {
    // Everything on by default; remembered across rotation so the user's own choices aren't reset.
    var initialised by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!initialised) {
            initialised = true
            vm.migrateSetSignOut(MigrateSignOut(claude = true, workers = true, github = true, autostart = true))
        }
    }
    val c = ui.signOutChoices
    val host = ui.from?.host.orEmpty()
    Text("The new server works. Now clean up $host so the two don't compete.", style = MaterialTheme.typography.bodyMedium)
    CardBox {
        Column {
            MigrateCheckRow("Sign out of Claude on the old server", c.claude, !ui.busy) { vm.migrateSetSignOut(c.copy(claude = it)) }
            MigrateCheckRow("Sign out of the worker accounts", c.workers, !ui.busy) { vm.migrateSetSignOut(c.copy(workers = it)) }
            MigrateCheckRow("Remove the GitHub login there", c.github, !ui.busy) { vm.migrateSetSignOut(c.copy(github = it)) }
            MigrateCheckRow("Stop its sessions from restarting", c.autostart, !ui.busy) { vm.migrateSetSignOut(c.copy(autostart = it)) }
        }
    }
    Text(
        "This only removes the logins from the OLD server; nothing is deleted from the new one.",
        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
    )
    Button(
        onClick = { vm.migrateSignOut(c.items()) },
        enabled = !ui.busy && c.items().isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Sign out and continue") }
    OutlinedButton(onClick = { vm.migrateSignOut(emptyList()) }, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) { Text("Skip") }
}

// ---- 9. Reboot ---------------------------------------------------------------------

@Composable
private fun MigrateReboot(vm: MainViewModel, ui: MigrateUi) {
    val host = ui.to?.host.orEmpty()
    Text(
        "A reboot of $host is optional: it proves everything comes back by itself (sessions restart, logins stay). The app waits up to 5 minutes for it.",
        style = MaterialTheme.typography.bodyMedium,
    )
    MigrateSudo(
        vm, ui, "Reboot the new server", { pw -> vm.migrateReboot(pw) },
        extra = { OutlinedButton(onClick = { vm.migrateSkipReboot() }, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) { Text("Skip reboot") } },
    )
    // Until sudo is known the extra button isn't shown above, but skipping must always be possible.
    if (ui.sudoMode == null) {
        OutlinedButton(onClick = { vm.migrateSkipReboot() }, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) { Text("Skip reboot") }
    }
}

// ---- 10. Verify, done --------------------------------------------------------------

/** What can be fixed from here: GitHub on the new server (a token), then the repos that couldn't be cloned. */
@Composable
private fun MigrateFixes(vm: MainViewModel, ui: MigrateUi, failed: List<String>) {
    val github = "gh" in failed
    val repos = "repos" in failed || "remotes" in failed
    if (!github && !repos) return
    var token by rememberSaveable { mutableStateOf("") }
    CardBox {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (github) {
                Text("Sign in to GitHub on the new server", style = MaterialTheme.typography.titleSmall)
                MigrateHint("Paste a GitHub token (the app's Settings has New token). It is sent once and saved on the new server only.")
                OutlinedTextField(
                    value = token, onValueChange = { token = it },
                    label = { Text("GitHub token") }, singleLine = true, enabled = !ui.busy,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                )
                Button(
                    onClick = { vm.migrateGithubSignIn(token); token = "" },
                    enabled = !ui.busy && token.isNotBlank(), modifier = Modifier.fillMaxWidth(),
                ) { Text("Sign in to GitHub") }
            }
            if (repos) {
                MigrateHint(
                    if (github) "After GitHub works, bring back the repos that couldn't be cloned."
                    else "Some repos are missing or can't reach GitHub. Try cloning them again.",
                )
                OutlinedButton(onClick = { vm.migrateCloneMissing() }, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Clone the missing repos")
                }
            }
        }
    }
}

@Composable
private fun MigrateVerify(vm: MainViewModel, ui: MigrateUi) {
    val v = ui.verify
    val done = ui.step == MigrateStep.DONE
    if (v == null) {
        Text(if (ui.busy) "Checking the new server…" else "Not checked yet.", style = MaterialTheme.typography.bodyMedium)
    } else {
        val bad = v.items.count { !it.ok }
        if (v.ok) {
            MigrateNotice(OkGreen.copy(alpha = 0.18f), MaterialTheme.colorScheme.onSurface) {
                Text("Everything is moved: 100%", style = MaterialTheme.typography.headlineSmall, color = OkGreen, fontWeight = FontWeight.Bold)
                Text("${ui.to?.host.orEmpty()} has your sessions, projects and settings.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            MigrateWarning {
                Text(if (bad == 1) "1 thing needs attention" else "$bad things need attention", style = MaterialTheme.typography.titleMedium)
                Text("Fix what is listed (or try again), then check again.", style = MaterialTheme.typography.bodySmall)
            }
        }
        CardBox {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                v.items.forEach { item ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (item.ok) "✓" else "✗", color = if (item.ok) OkGreen else BadRed, fontWeight = FontWeight.Bold)
                        Column(Modifier.weight(1f)) {
                            Text(item.id.replace('_', ' '), style = MaterialTheme.typography.bodyMedium)
                            if (item.detail.isNotBlank()) {
                                Text(item.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
    if (v != null) MigrateFixes(vm, ui, v.items.filter { !it.ok }.map { it.id })
    ui.setupEntry?.let { s ->
        CardBox {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Two entries for ${s.host}", style = MaterialTheme.typography.titleSmall)
                MigrateHint("The list now has ${s.user}@${s.host} (the one you set up with) and ${ui.to?.user.orEmpty()}@${s.host} (the new user). Remove the first once you don't need it.")
                OutlinedButton(onClick = { vm.migrateRemoveSetupEntry() }, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Remove ${s.user}@${s.host} from the list")
                }
            }
        }
    }
    if (v?.ok != true) {
        Button(onClick = { vm.migrateVerify() }, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) { Text("Check again") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { vm.migrateClose() }, modifier = Modifier.weight(1f)) { Text("Close") }
        OutlinedButton(onClick = { vm.migrateReset() }, modifier = Modifier.weight(1f)) { Text("Start over") }
    }
}
