package life.mygig.clauderc.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import life.mygig.clauderc.data.*
import life.mygig.clauderc.ui.MainViewModel

@Composable
fun AppBackupSection(vm: MainViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pass by remember { mutableStateOf("") }; var again by remember { mutableStateOf("") }
    var dialog by remember { mutableStateOf("") }; var backupError by remember { mutableStateOf<String?>(null) }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var importBytes by remember { mutableStateOf<ByteArray?>(null) }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        val bytes = pendingBytes; pendingBytes = null
        if (uri != null && bytes != null) scope.launch { runCatching { withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Unable to write backup.") } }.onFailure { backupError = it.message ?: "Unable to save backup." } }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch { runCatching { withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Unable to read backup.") } }.onSuccess { importBytes = it; dialog = "import_pass" }.onFailure { backupError = it.message ?: "Unable to read backup." } }
    }
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Back up this app", style = MaterialTheme.typography.titleMedium)
        Text("Encrypted copy of your servers, app settings and phone key. Chat PIN is excluded.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { dialog = "export" }) { Text("Export encrypted file") }
            OutlinedButton(onClick = { importPicker.launch(arrayOf("application/octet-stream", "application/*", "*/*")) }) { Text("Import") }
        }
        backupError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }
    if (dialog == "export") AlertDialog(onDismissRequest = { dialog = ""; pass = ""; again = "" }, title = { Text("Encrypt app backup") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(pass, { pass = it }, label = { Text("Passphrase (8+ characters)") }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()); OutlinedTextField(again, { again = it }, label = { Text("Repeat passphrase") }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()); backupError?.let { Text(it, color = MaterialTheme.colorScheme.error) } } }, confirmButton = { TextButton(enabled = pass.length >= 8 && pass == again, onClick = { val p = pass; pass = ""; again = ""; dialog = ""; scope.launch { runCatching { val settings = vm.store.current(); val seed = vm.keys.exportSeed(); try { val payload = AppBackupPayload(settings.servers, settings.activeId, settings.appLock, settings.notify, settings.theme.name, Base64.getEncoder().encodeToString(seed)); AppBackup.encrypt(appBackupJson.encodeToString(payload).toByteArray(), p.toCharArray()) } finally { seed.fill(0) } }.onSuccess { pendingBytes = it; exportPicker.launch("clauderc-app-backup.clrcbk") }.onFailure { backupError = it.message ?: "Unable to create backup." } } }) { Text("Continue") } }, dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Cancel") } })
    if (dialog == "import_pass") AlertDialog(onDismissRequest = { dialog = ""; pass = "" }, title = { Text("Decrypt app backup") }, text = { Column { OutlinedTextField(pass, { pass = it }, label = { Text("Passphrase") }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()); backupError?.let { Text(it, color = MaterialTheme.colorScheme.error) } } }, confirmButton = { TextButton(enabled = pass.length >= 8, onClick = { val encrypted = importBytes; val p = pass; pass = ""; dialog = ""; scope.launch { runCatching { val plain = AppBackup.decrypt(encrypted!!, p.toCharArray()); appBackupJson.decodeFromString<AppBackupPayload>(plain.toString(Charsets.UTF_8)) }.onSuccess { importBytes = AppBackupPayload(it.servers, it.activeId, it.appLock, it.notify, it.theme, it.sshSeed).let { appBackupJson.encodeToString(it).toByteArray() }; dialog = "confirm_import" }.onFailure { backupError = "Could not decrypt or read backup. Check the passphrase and file."; dialog = "import_pass" } } }) { Text("Continue") } }, dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Cancel") } })
    if (dialog == "confirm_import") AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("Replace app data?") }, text = { Text("This replaces your servers, settings and SSH key.") }, confirmButton = { TextButton(onClick = { val payloadBytes = importBytes!!; dialog = ""; scope.launch { runCatching { val payload = appBackupJson.decodeFromString<AppBackupPayload>(payloadBytes.toString(Charsets.UTF_8)); vm.store.restoreBackup(payload.servers, payload.activeId, payload.appLock, payload.notify, runCatching { ThemeMode.valueOf(payload.theme) }.getOrDefault(ThemeMode.SYSTEM)); val seed = Base64.getDecoder().decode(payload.sshSeed); try { vm.keys.importSeed(seed) } finally { seed.fill(0) } }.onFailure { backupError = it.message ?: "Unable to restore backup." } } }) { Text("Replace") } }, dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Cancel") } })
}
