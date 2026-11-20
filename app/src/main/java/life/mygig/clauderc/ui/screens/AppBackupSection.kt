package life.mygig.clauderc.ui.screens

import life.mygig.clauderc.ui.components.CardBox
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Base64
import com.google.android.gms.common.api.ApiException
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import life.mygig.clauderc.data.*
import life.mygig.clauderc.ui.LocalAppLock
import life.mygig.clauderc.ui.MainViewModel

@Composable
fun AppBackupSection(vm: MainViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val appLock = LocalAppLock.current
    var pass by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var dialog by remember { mutableStateOf("") }
    var backupError by remember { mutableStateOf<String?>(null) }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var importBytes by remember { mutableStateOf<ByteArray?>(null) }
    var importPayload by remember { mutableStateOf<AppBackupPayload?>(null) }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        val bytes = pendingBytes
        pendingBytes = null
        if (uri != null && bytes != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Unable to write backup.") } }
                .onFailure { backupError = it.message ?: "Unable to save backup." }
        }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Unable to read backup.") } }
                .onSuccess { importBytes = it; dialog = "import_pass" }
                .onFailure { backupError = it.message ?: "Unable to read backup." }
        }
    }
    // Drive: the access token lives only in this composition (memory), never stored or logged.
    val drive = remember { HttpDriveApi() }
    var driveToken by remember { mutableStateOf<String?>(null) }
    var driveBusy by remember { mutableStateOf(false) }
    var driveNote by remember { mutableStateOf<String?>(null) }
    var driveFiles by remember { mutableStateOf<List<DriveFile>>(emptyList()) }
    var exportToDrive by remember { mutableStateOf(false) }
    var authWait by remember { mutableStateOf<CompletableDeferred<String?>?>(null) }
    val authLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val d = authWait
        authWait = null
        runCatching { DriveAuth.resultFromIntent(ctx, r.data) }
            .onSuccess { d?.complete(it) }
            .onFailure { d?.completeExceptionally(it) }
    }
    suspend fun driveSignIn(): String {
        driveToken?.let { return it }
        val res = try { DriveAuth.authorize(ctx) } catch (e: ApiException) { throw DriveException(DriveError.OTHER, DriveLogic.signInMessage(e.statusCode)) }
        val pending = res.pendingIntent
        val token = if (res.hasResolution() && pending != null) {
            val d = CompletableDeferred<String?>()
            authWait = d
            authLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
            try { d.await() } catch (e: ApiException) { throw DriveException(DriveError.OTHER, DriveLogic.signInMessage(e.statusCode)) }
        } else res.accessToken
        return token?.also { driveToken = it } ?: throw DriveException(DriveError.OTHER, "Sign-in cancelled.")
    }
    fun driveRun(work: suspend () -> Unit) {
        if (driveBusy) return
        driveBusy = true; driveNote = null
        scope.launch {
            try { work() }
            catch (e: DriveException) { if (e.kind == DriveError.EXPIRED) driveToken = null; driveNote = e.message }
            catch (e: Exception) { driveNote = "Google Drive failed (${e.javaClass.simpleName}). Try again." }
            finally { driveBusy = false }
        }
    }
    CardBox {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Back up this app", style = MaterialTheme.typography.titleMedium)
            Text("Encrypted copy of your servers, app settings and phone key. Chat PIN is excluded.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { exportToDrive = false; dialog = "export" }) { Text("Export encrypted file") }
                OutlinedButton(onClick = { importPicker.launch(arrayOf("application/octet-stream", "application/*", "*/*")) }) { Text("Import") }
            }
            Text("Google Drive keeps the same encrypted file in a hidden app folder (newest 3). Sign in with Google; the passphrase never leaves the phone.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !driveBusy, onClick = { exportToDrive = true; dialog = "export" }) { Text("Back up to Drive") }
                OutlinedButton(enabled = !driveBusy, onClick = { driveRun {
                    val list = drive.list(driveSignIn())
                    if (list.isEmpty()) driveNote = "No backups found in Google Drive." else { driveFiles = list; dialog = "drive_pick" }
                } }) { Text("Restore from Drive") }
            }
            if (driveToken != null) TextButton(enabled = !driveBusy, onClick = {
                val t = driveToken; driveToken = null
                scope.launch { if (t != null) withContext(Dispatchers.IO) { drive.revoke(t) } }
                driveNote = "Signed out of Google."
            }) { Text("Sign out of Google") }
            if (driveBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            driveNote?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            backupError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    if (dialog == "drive_pick") AlertDialog(
        onDismissRequest = { dialog = "" },
        title = { Text("Choose a backup") },
        text = { Column { driveFiles.forEach { f ->
            TextButton(onClick = { dialog = ""; driveRun {
                val bytes = drive.download(driveSignIn(), f.id)
                importBytes = bytes; dialog = "import_pass"
            } }) { Text(DriveLogic.label(f)) }
        } } },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Cancel") } },
    )
    if (dialog == "export") AlertDialog(
        onDismissRequest = { dialog = ""; pass = ""; again = ""; exportToDrive = false },
        title = { Text("Encrypt app backup") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(pass, { pass = it }, label = { Text("Passphrase (12+ characters)") }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
            OutlinedTextField(again, { again = it }, label = { Text("Repeat passphrase") }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
            backupError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(enabled = pass.length >= 12 && pass == again, onClick = {
            val p = pass; pass = ""; again = ""; dialog = ""
            scope.launch {
                if (appLock?.unlock("Export phone key") == false) return@launch
                runCatching {
                    val settings = vm.store.current()
                    val seed = vm.keys.exportSeed()
                    try {
                        val payload = AppBackupPayload(settings.servers, settings.activeId, settings.appLock, settings.notify, settings.theme.name, Base64.getEncoder().encodeToString(seed))
                        AppBackup.encrypt(appBackupJson.encodeToString(payload).toByteArray(), p.toCharArray())
                    } finally { seed.fill(0) }
                }.onSuccess { enc ->
                    if (exportToDrive) {
                        exportToDrive = false
                        driveRun {
                            val token = driveSignIn()
                            val saved = drive.upload(token, DriveLogic.fileName(Instant.now()), enc)
                            runCatching { DriveLogic.toDelete(drive.list(token)).forEach { drive.delete(token, it.id) } }
                            driveNote = "Backed up to Google Drive (${DriveLogic.label(saved)})."
                        }
                    } else { pendingBytes = enc; exportPicker.launch("clauderc-app-backup.clrcbk") }
                }.onFailure { backupError = it.message ?: "Unable to create backup." }
            }
        }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { dialog = ""; pass = ""; again = ""; exportToDrive = false }) { Text("Cancel") } },
    )
    if (dialog == "import_pass") AlertDialog(
        onDismissRequest = { dialog = ""; pass = ""; importBytes?.fill(0); importBytes = null },
        title = { Text("Decrypt app backup") },
        text = { Column {
            OutlinedTextField(pass, { pass = it }, label = { Text("Passphrase") }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
            backupError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(enabled = pass.length >= 12, onClick = {
            val encrypted = importBytes ?: return@TextButton
            val p = pass; pass = ""; dialog = ""
            scope.launch {
                runCatching {
                    val plain = AppBackup.decrypt(encrypted, p.toCharArray())
                    try { appBackupJson.decodeFromString<AppBackupPayload>(plain.toString(Charsets.UTF_8)) }
                    finally { plain.fill(0); encrypted.fill(0); importBytes = null }
                }.onSuccess { importPayload = it; dialog = "confirm_import" }
                    .onFailure { backupError = "Could not decrypt or read backup. Check the passphrase and file."; dialog = "import_pass" }
            }
        }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { dialog = ""; pass = ""; importBytes?.fill(0); importBytes = null }) { Text("Cancel") } },
    )
    if (dialog == "confirm_import") AlertDialog(
        onDismissRequest = { importPayload = null; dialog = "" },
        title = { Text("Replace app data?") },
        text = { Text("This replaces your servers, settings and SSH key.") },
        confirmButton = { TextButton(onClick = {
            val payload = importPayload ?: return@TextButton
            importPayload = null; dialog = ""
            scope.launch {
                if (appLock?.unlock("Restore phone key") == false) return@launch
                runCatching {
                    val seed = Base64.getDecoder().decode(payload.sshSeed)
                    try { require(seed.size == 32) { "The backup's key is damaged." }; vm.keys.importSeed(seed) }
                    finally { seed.fill(0) }
                    vm.store.restoreBackup(payload.servers, payload.activeId, payload.appLock, payload.notify, runCatching { ThemeMode.valueOf(payload.theme) }.getOrDefault(ThemeMode.SYSTEM))
                }.onFailure { backupError = it.message ?: "Unable to restore backup." }
            }
        }) { Text("Replace") } },
        dismissButton = { TextButton(onClick = { importPayload = null; dialog = "" }) { Text("Cancel") } },
    )
}
