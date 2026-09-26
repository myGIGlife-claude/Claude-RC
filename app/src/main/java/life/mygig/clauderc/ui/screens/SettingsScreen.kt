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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import life.mygig.clauderc.BuildConfig
import life.mygig.clauderc.data.AppSettings
import life.mygig.clauderc.data.ThemeMode
import life.mygig.clauderc.ssh.HostKeyInfo
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

    val portNum = port.toIntOrNull()?.takeIf { it in 1..65535 }
    val canSave = host.isNotBlank() && user.isNotBlank() && portNum != null && busy == null

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (firstRun) {
            Text("Set up your server", style = MaterialTheme.typography.headlineSmall)
            Text(
                "1. Copy this phone's public key below.\n" +
                    "2. On the server run: ./install-launcher-key.sh '<key>'\n" +
                    "3. Enter the server details and tap Connect.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Section("This phone's key")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectionContainer {
                    Text(publicKey, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { copy(context, "Public key", publicKey) }) { Text("Copy") }
                    OutlinedButton(onClick = {
                        copy(context, "Install command", "./install-launcher-key.sh '$publicKey'")
                    }) { Text("Copy install cmd") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { share(context, publicKey) }) { Text("Share") }
                    TextButton(onClick = { confirmRegen = true }) { Text("Regenerate") }
                }
                Text(
                    "The private key never leaves this phone. It is encrypted with the Android Keystore.",
                    style = MaterialTheme.typography.bodySmall,
                )
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
            OutlinedTextField(
                value = user, onValueChange = { user = it.trim() },
                label = { Text("Username") }, singleLine = true,
                modifier = Modifier.weight(0.65f),
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
                OutlinedButton(onClick = { vm.refreshStatus() }) { Text("Test connection") }
                TextButton(onClick = { vm.forgetHostKey() }) { Text("Forget host key") }
            }
        }

        if (!firstRun) {
            HorizontalDivider()
            Section("App")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("App lock")
                    Text(
                        "Fingerprint or PIN before creating, stopping or logging in",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = s.appLock, onCheckedChange = { on ->
                    guard.run("Change app lock") { vm.setAppLock(on) }
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
            text = { Text("The old key stops working. You'll need to run install-launcher-key.sh again with the new key.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRegen = false
                    guard.run("Regenerate SSH key") { vm.regenerateKey() }
                }) { Text("Regenerate") }
            },
            dismissButton = { TextButton(onClick = { confirmRegen = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String) {
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

private fun copy(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "Share public key").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
