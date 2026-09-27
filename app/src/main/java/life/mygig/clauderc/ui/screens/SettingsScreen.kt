package life.mygig.clauderc.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import life.mygig.clauderc.BuildConfig
import life.mygig.clauderc.api.Updates
import life.mygig.clauderc.data.AppSettings
import life.mygig.clauderc.data.Server
import life.mygig.clauderc.data.ThemeMode
import life.mygig.clauderc.ssh.HostKeyInfo
import kotlinx.coroutines.launch
import life.mygig.clauderc.ui.LocalAppLock
import life.mygig.clauderc.ui.LocalGuard
import life.mygig.clauderc.ui.MainViewModel

@Composable
fun SettingsScreen(vm: MainViewModel, s: AppSettings, firstRun: Boolean) {
    val context = LocalContext.current
    val guard = LocalGuard.current
    val publicKey by vm.publicKey.collectAsState()
    val busy by vm.busy.collectAsState()

    // Blank by default: nothing about any server ships with the app.
    var host by rememberSaveable(s.host) { mutableStateOf(s.host) }
    var port by rememberSaveable(s.port) { mutableStateOf(s.port.toString()) }
    var user by rememberSaveable(s.user) { mutableStateOf(s.user) }
    var confirmRegen by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<Server?>(null) }
    val scope = rememberCoroutineScope()
    val appLock = LocalAppLock.current
    // Hidden by default; revealing asks for fingerprint/PIN when App lock is on.
    // Not saveable on purpose: leaving the screen hides them again.
    var showKey by remember { mutableStateOf(false) }
    var showUser by remember { mutableStateOf(s.user.isBlank()) }

    val portNum = port.toIntOrNull()?.takeIf { it in 1..65535 }
    val canSave = host.isNotBlank() && user.isNotBlank() && portNum != null && busy == null

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (firstRun) {
            Text("Add your server", style = MaterialTheme.typography.headlineSmall)
            Text(
                "1. On your Linux server, open a terminal as the user Claude should run as.\n" +
                    "2. Run this one command. It installs the cLaudeRC scripts and session " +
                    "auto-restore (asks for sudo once) and lets this phone in:",
                style = MaterialTheme.typography.bodyMedium,
            )
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectionContainer {
                        Text(
                            if (publicKey.isBlank()) "No key yet. Tap Regenerate." else Updates.installCommand(publicKey),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = publicKey.isNotBlank(), onClick = {
                            copy(context, "Install command", Updates.installCommand(publicKey))
                        }) { Text("Copy command") }
                        OutlinedButton(enabled = publicKey.isNotBlank(), onClick = {
                            share(context, Updates.installCommand(publicKey))
                        }) { Text("Share") }
                        if (publicKey.isBlank()) TextButton(onClick = { vm.regenerateKey() }) { Text("Regenerate") }
                    }
                }
            }
            Text(
                "3. When it finishes it prints the Host, Port and Username. Enter them below " +
                    "and tap Connect.\n" +
                    "4. Check the fingerprint the app shows is one of those it printed, then tap Trust.\n" +
                    "The server needs curl, jq, tmux, git and flock.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // On first run the setup card above already carries the key.
        if (!firstRun) {
            Section("This phone's key")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (publicKey.isBlank()) {
                            Text(
                                "No key yet. Tap Regenerate to create one.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                        } else if (showKey) {
                            SelectionContainer(Modifier.weight(1f)) {
                                Text(publicKey, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            Text(
                                "ssh-ed25519 ••••••••••••",
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        TextButton(enabled = publicKey.isNotBlank(), onClick = {
                            if (showKey) showKey = false else guard.run("Show this phone's key") { showKey = true }
                        }) { Text(if (showKey) "Hide" else "Show") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = publicKey.isNotBlank(), onClick = {
                            guard.run("Copy this phone's key") { copy(context, "Public key", publicKey) }
                        }) { Text("Copy") }
                        OutlinedButton(enabled = publicKey.isNotBlank(), onClick = {
                            guard.run("Copy this phone's key") {
                                copy(context, "Install command", Updates.installCommand(publicKey))
                            }
                        }) { Text("Copy install command") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = publicKey.isNotBlank(), onClick = {
                            guard.run("Share this phone's key") { share(context, publicKey) }
                        }) { Text("Share") }
                        TextButton(onClick = { confirmRegen = true }) { Text("Regenerate") }
                    }
                    Text(
                        "The private key never leaves this phone. It is encrypted with the Android Keystore.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

        }

        Section("Server")
        OutlinedTextField(
            value = host, onValueChange = { host = it.trim() },
            label = { Text("Host") }, placeholder = { Text("your-server.example.com") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = port, onValueChange = { port = it.filter(Char::isDigit).take(5) },
                label = { Text("Port") }, singleLine = true, isError = portNum == null,
                modifier = Modifier.weight(0.35f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            // Masked once entered; Show (behind App lock) reveals and allows editing.
            OutlinedTextField(
                value = user, onValueChange = { user = it.trim() },
                label = { Text("Username") }, singleLine = true,
                readOnly = !showUser,
                visualTransformation = if (showUser) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = {
                        if (showUser) showUser = false else guard.run("Show username") { showUser = true }
                    }) {
                        Icon(
                            if (showUser) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showUser) "Hide username" else "Show username",
                        )
                    }
                },
                modifier = Modifier.weight(0.65f).onFocusChanged { f ->
                    if (!f.isFocused && user.isNotBlank()) showUser = false
                },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            )
        }
        Button(
            onClick = { vm.saveServerAndProbe(host, portNum ?: 22, user) },
            enabled = canSave,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (s.hostKeyBlob.isEmpty()) "Connect" else "Save & re-check host key") }

        if (s.hostKeyFingerprint.isNotEmpty()) {
            Text("Pinned host key (${s.hostKeyType})", style = MaterialTheme.typography.labelLarge)
            SelectionContainer {
                Text(s.hostKeyFingerprint, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.testConnection() }, enabled = busy == null) { Text("Test connection") }
                TextButton(onClick = { vm.forgetHostKey() }) { Text("Forget host key") }
            }
        }

        if (!firstRun) {
            HorizontalDivider()
            Section("Servers")
            s.servers.filter { it.isConfigured }.forEach { srv ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${srv.user}@${srv.host}:${srv.port}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        if (srv.id == s.activeId) Text("Current", style = MaterialTheme.typography.labelSmall)
                    }
                    if (srv.id != s.activeId) TextButton(onClick = { vm.switchServer(srv.id) }) { Text("Use") }
                    TextButton(onClick = { confirmRemove = srv }) { Text("Remove") }
                }
            }
            OutlinedButton(onClick = { vm.addServer() }) { Text("Add server") }

            HorizontalDivider()
            Section("App")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("App lock")
                    Text(
                        "Fingerprint or PIN to open the app, and before creating, stopping, " +
                            "logging in or showing your key and username",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = s.appLock, onCheckedChange = { on ->
                    val lock = appLock ?: return@Switch
                    if (on && !lock.canAuthenticate()) {
                        vm.say("Set up a screen lock or fingerprint on this phone first.")
                        return@Switch
                    }
                    // Both directions need the fingerprint/PIN, so it can't be
                    // switched off by someone else, or on without a working unlock.
                    scope.launch {
                        if (lock.unlock(if (on) "Turn on app lock" else "Turn off app lock")) {
                            vm.markUnlocked()
                            vm.setAppLock(on)
                        }
                    }
                })
            }
            Text("Theme")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = s.theme == m,
                        onClick = { vm.setTheme(m) },
                        shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                    ) { Text(m.name.lowercase().replaceFirstChar { it.uppercase() }) }
                }
            }
        }
        Spacer(Modifier.padding(4.dp))
        Text(
            "cLaudeRC ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodySmall,
        )
    }

    if (confirmRegen) {
        AlertDialog(
            onDismissRequest = { confirmRegen = false },
            title = { Text("Regenerate key?") },
            text = { Text("The old key stops working on every server. Run Copy install command on each server again.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRegen = false
                    guard.run("Regenerate SSH key") { vm.regenerateKey() }
                }) { Text("Regenerate") }
            },
            dismissButton = { TextButton(onClick = { confirmRegen = false }) { Text("Cancel") } },
        )
    }

    confirmRemove?.let { srv ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove ${srv.host}?") },
            text = {
                Text(
                    "The app forgets this server. This phone's key stays allowed on it until you run " +
                        "~/bin/install-launcher-key.sh --remove there.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmRemove = null; vm.removeServer(srv.id) }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel") } },
        )
    }
}

@Composable
internal fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
fun HostKeyDialog(vm: MainViewModel, info: HostKeyInfo, previous: String) {
    val changed = previous.isNotEmpty() && previous != info.fingerprint
    AlertDialog(
        onDismissRequest = { vm.acceptHostKey(false) },
        title = { Text(if (changed) "Host key CHANGED" else "Trust this server?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (changed) {
                    Text(
                        "The server now shows a different key than the one pinned. Only accept if you " +
                            "reinstalled the server or changed its SSH host keys.",
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text("Pinned: $previous", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
                Text("Key type: ${info.type}")
                Text(info.fingerprint, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                Text(
                    "Compare with the output of this on the server:\n" +
                        "ssh-keygen -lf /etc/ssh/ssh_host_${info.type.removePrefix("ssh-").substringBefore('-')}_key.pub",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = { vm.acceptHostKey(true) }) { Text("Trust") } },
        dismissButton = { TextButton(onClick = { vm.acceptHostKey(false) }) { Text("Cancel") } },
    )
}

internal fun copy(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "Share public key").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
