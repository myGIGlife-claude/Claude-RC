package life.mygig.clauderc.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import life.mygig.clauderc.ui.MainViewModel
import life.mygig.clauderc.ui.openUrl

@Composable
fun DriveSection(vm: MainViewModel) {
    val status by vm.driveStatus.collectAsStateWithLifecycle()
    val login by vm.driveLogin.collectAsStateWithLifecycle()
    val backups by vm.driveBackups.collectAsStateWithLifecycle()
    val progress by vm.driveProgress.collectAsStateWithLifecycle()
    val error by vm.driveError.collectAsStateWithLifecycle()
    val restore by vm.driveRestoreCommand.collectAsStateWithLifecycle()
    var phrase by remember { mutableStateOf("") }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var disconnect by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.driveRefresh() }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Google Drive backup", style = MaterialTheme.typography.titleMedium)
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            when {
                status == null -> Text("Checking Google Drive backup support…", style = MaterialTheme.typography.bodySmall)
                status?.configured == false -> {
                    Text("Add GOOGLE_DRIVE_CLIENT_ID and GOOGLE_DRIVE_CLIENT_SECRET in Custom API keys to connect.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { vm.showCustomKeys(true) }) { Text("Open Custom API keys") }
                }
                status?.connected != true -> {
                    Button(onClick = { vm.driveLoginStart() }) { Text("Connect Google Drive") }
                    login?.let {
                        Text("Open the Google page, enter this code and approve:", style = MaterialTheme.typography.bodySmall)
                        Text(it.code, style = MaterialTheme.typography.headlineSmall)
                        OutlinedButton(onClick = { openUrl(context, it.url) }) { Text("Open Google") }
                    }
                }
                else -> {
                    Text("Connected to Google Drive")
                    OutlinedTextField(phrase, { phrase = it }, label = { Text("Backup passphrase (12–200 characters)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                    Text("You need this passphrase to restore a backup. It is stored on your server only so scheduled backups can run.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { if (phrase.length in 12..200) { vm.driveSetPassphrase(phrase); phrase = "" } }, enabled = phrase.length in 12..200) { Text("Save passphrase") }
                    Button(onClick = vm::driveBackup) { Text("Back up now") }
                    progress?.takeIf { it.state == "running" }?.let { Text("${it.phase}: ${it.percent ?: 0}% — ${it.message}") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Daily backups", Modifier.weight(1f))
                        Switch(checked = status?.schedule == "daily", onCheckedChange = vm::driveSchedule)
                    }
                    backups.forEach { backup ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(backup.name, style = MaterialTheme.typography.bodySmall); Text("${backup.size} bytes", style = MaterialTheme.typography.labelSmall) }
                            TextButton(onClick = { vm.driveRestore(backup.id) }) { Text("Restore") }
                            TextButton(onClick = { deleteId = backup.id }) { Text("Delete") }
                        }
                    }
                    TextButton(onClick = { disconnect = true }) { Text("Disconnect") }
                }
            }
        }
    }
    restore?.let { cmd -> AlertDialog(onDismissRequest = vm::clearDriveRestore, title = { Text("Finish restore on the server") }, text = { Text(cmd) }, confirmButton = { TextButton(onClick = { copy(context, "Restore command", cmd); vm.clearDriveRestore() }) { Text("Copy") } }, dismissButton = { TextButton(onClick = vm::clearDriveRestore) { Text("Close") } }) }
    deleteId?.let { id -> AlertDialog(onDismissRequest = { deleteId = null }, title = { Text("Delete this backup?") }, confirmButton = { TextButton(onClick = { deleteId = null; vm.driveDelete(id) }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } }) }
    if (disconnect) AlertDialog(onDismissRequest = { disconnect = false }, title = { Text("Disconnect Google Drive?") }, text = { Text("Scheduled backups will stop. Existing backups remain in Drive.") }, confirmButton = { TextButton(onClick = { disconnect = false; vm.driveLogout() }) { Text("Disconnect") } }, dismissButton = { TextButton(onClick = { disconnect = false }) { Text("Cancel") } })
}
