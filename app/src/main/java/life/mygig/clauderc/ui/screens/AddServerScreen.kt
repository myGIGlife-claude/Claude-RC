package life.mygig.clauderc.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import life.mygig.clauderc.api.ServerSetup
import life.mygig.clauderc.ui.MainViewModel
import life.mygig.clauderc.ui.components.CardBox
import life.mygig.clauderc.ui.components.SectionLabel
import life.mygig.clauderc.ui.theme.Term
import life.mygig.clauderc.ui.vm.AddServerStep

/**
 * Add server › Connect with a password: host, port, user and password in, a set-up server out.
 * The passwords stay in this screen's memory only (`remember`, never saveable state, drafts or backups),
 * go to the controller once, and the fields are cleared right after.
 */
@Composable
fun AddServerScreen(vm: MainViewModel) {
    val flow = vm.addServerFlow
    val ui by flow.ui.collectAsStateWithLifecycle()
    if (!ui.open) return

    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("22") }
    var user by rememberSaveable { mutableStateOf("") }
    var purpose by rememberSaveable { mutableStateOf("") }   // not a secret: saved on the server entry
    var password by remember { mutableStateOf("") }
    var sameSudo by remember { mutableStateOf(true) }
    var sudoPassword by remember { mutableStateOf("") }
    var showOutput by remember { mutableStateOf(false) }
    val step = ui.step
    val close = {
        password = ""; sudoPassword = ""
        if (step == AddServerStep.RUNNING) flow.cancel() else flow.close()
    }

    Dialog(
        onDismissRequest = close,
        // No screenshots or Recents preview while a password may be on screen.
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, securePolicy = SecureFlagPolicy.SecureOn),
    ) {
        DialogKeyboardFix()
        NoAutofill()   // an autofill service would offer to save the password
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = close) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = if (step == AddServerStep.RUNNING) "Cancel setup" else "Back")
                    }
                }
                // Wide windows (a foldable opened): one centred column, like the rest of the app.
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Column(
                        Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            when (step) {
                                AddServerStep.RUNNING -> "Setting up ${ui.host}"
                                AddServerStep.DONE -> "Server added"
                                AddServerStep.FAILED -> "Setup didn't finish"
                                else -> "Connect with a password"
                            },
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        when (step) {
                            AddServerStep.FORM, AddServerStep.PROBING, AddServerStep.CONFIRM_KEY -> {
                                val probing = step == AddServerStep.PROBING
                                Text(
                                    "Enter the server's SSH user and password. The app installs what it needs, then lets this phone's key in; " +
                                        "after that the key is the only sign-in the app uses.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                SectionLabel("Server")
                                OutlinedTextField(
                                    value = host, onValueChange = { host = it.trim() }, enabled = !probing,
                                    label = { Text("Host") }, placeholder = { Text("your-server.example.com") },
                                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = port, onValueChange = { port = it.filter(Char::isDigit).take(5) }, enabled = !probing,
                                        label = { Text("Port") }, singleLine = true, modifier = Modifier.weight(0.35f),
                                        isError = port.toIntOrNull()?.takeIf { it in 1..65535 } == null,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    )
                                    OutlinedTextField(
                                        value = user, onValueChange = { user = it.trim() }, enabled = !probing,
                                        label = { Text("User name") }, singleLine = true, modifier = Modifier.weight(0.65f),
                                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                                    )
                                }
                                PurposeField(purpose, { purpose = it }, enabled = !probing)
                                SectionLabel("Password")
                                OutlinedTextField(
                                    value = password, onValueChange = { password = it.replace("\n", "") }, enabled = !probing,
                                    label = { Text("SSH password") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = sameSudo, onCheckedChange = { sameSudo = it; if (it) sudoPassword = "" }, enabled = !probing)
                                    Text("Use the same password for sudo", style = MaterialTheme.typography.bodyMedium)
                                }
                                if (!sameSudo) {
                                    OutlinedTextField(
                                        value = sudoPassword, onValueChange = { sudoPassword = it.replace("\n", "") }, enabled = !probing,
                                        label = { Text("sudo password (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                                        visualTransformation = PasswordVisualTransformation(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                                    )
                                }
                                Text(
                                    "The password is used once, kept only in memory, never saved.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                CardBox {
                                    Text(
                                        "The server user needs sudo rights for the full setup. Without sudo the install still works, " +
                                            "but boot-restore (claude-autostart) and package installs are skipped.",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                ui.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                                if (probing) {
                                    LinearProgressIndicator(Modifier.fillMaxWidth())
                                    Text("Contacting ${ui.host}…", style = MaterialTheme.typography.bodySmall)
                                    FilledTonalButton(onClick = { flow.cancel() }) { Text("Cancel") }
                                } else {
                                    Button(
                                        onClick = { flow.connect(host, port, user, purpose) },
                                        enabled = host.isNotBlank() && user.isNotBlank() && password.isNotEmpty() && ui.triesLeft > 0,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) { Text("Connect") }
                                }
                            }
                            AddServerStep.RUNNING -> {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                Text(
                                    "Installing tools, Claude Code and the cLaudeRC scripts. This can take a few minutes.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                SetupOutput(ServerSetup.visible(ui.log), follow = true)
                                FilledTonalButton(onClick = { flow.cancel() }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                            }
                            AddServerStep.DONE, AddServerStep.FAILED -> {
                                val failed = step == AddServerStep.FAILED
                                ui.message?.let {
                                    Text(it, style = MaterialTheme.typography.bodyLarge, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                                }
                                if (ui.notes.isNotEmpty()) {
                                    CardBox {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            ui.notes.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                                        }
                                    }
                                }
                                if (ui.log.isNotBlank()) {
                                    if (failed || showOutput) SetupOutput(ServerSetup.visible(ui.log), follow = false)
                                    else TextButton(onClick = { showOutput = true }) { Text("Show the server's output") }
                                }
                                if (failed) {
                                    if (ui.triesLeft > 0) Button(onClick = { flow.backToForm() }, modifier = Modifier.fillMaxWidth()) { Text("Back to the form") }
                                    FilledTonalButton(onClick = close, modifier = Modifier.fillMaxWidth()) { Text("Close") }
                                } else {
                                    Button(onClick = close, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Trust-on-first-use stays a person's decision: the fingerprint is shown every time, even on a retry.
        if (step == AddServerStep.CONFIRM_KEY) {
            ui.hostKey?.let { info ->
                AlertDialog(
                    onDismissRequest = { flow.rejectKey() },
                    title = { Text("Trust this server?") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (ui.sameAsTrusted) Text("Same key you trusted a moment ago.", style = MaterialTheme.typography.bodySmall)
                            Text("Key type: ${info.type}")
                            SelectionContainer { Text(info.fingerprint, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                            Text(
                                "Compare with the output of this on the server:\n" +
                                    "ssh-keygen -lf /etc/ssh/ssh_host_${info.type.removePrefix("ssh-").substringBefore('-')}_key.pub",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val pw = password
                            val sudoPw = if (sameSudo) password else sudoPassword
                            password = ""; sudoPassword = ""
                            flow.run(pw, sudoPw)
                        }) { Text("Trust and set up") }
                    },
                    dismissButton = { TextButton(onClick = { flow.rejectKey() }) { Text("Cancel") } },
                )
            }
        }
    }
}

/** The server's output, terminal style; [follow] keeps the newest line in view. */
@Composable
private fun SetupOutput(text: String, follow: Boolean) {
    val scroll = rememberScrollState()
    if (follow) LaunchedEffect(text.length) { scroll.scrollTo(scroll.maxValue) }
    SelectionContainer {
        Text(
            text.ifBlank { "Waiting for the server…" },
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = Term.Fg,
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 360.dp)
                .background(Term.Field, RoundedCornerShape(14.dp)).verticalScroll(scroll).padding(12.dp),
        )
    }
}
