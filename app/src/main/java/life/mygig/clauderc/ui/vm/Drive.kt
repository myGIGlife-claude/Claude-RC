package life.mygig.clauderc.ui.vm

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import life.mygig.clauderc.api.ApiException
import life.mygig.clauderc.api.Codes
import life.mygig.clauderc.api.DriveBackupEntry
import life.mygig.clauderc.api.DriveBackupStatus
import life.mygig.clauderc.api.DriveLoginStart
import life.mygig.clauderc.api.DriveStatus
import life.mygig.clauderc.api.LauncherApi
import life.mygig.clauderc.ui.friendly

/** The Google Drive backup section: sign-in, passphrase, schedule, backups and restore. */
class DriveController(private val api: LauncherApi, private val tools: VmTools) {
    private val _driveStatus = MutableStateFlow<DriveStatus?>(null)
    val driveStatus = _driveStatus.asStateFlow()
    private val _driveError = MutableStateFlow<String?>(null)
    val driveError = _driveError.asStateFlow()
    private val _driveLogin = MutableStateFlow<DriveLoginStart?>(null)
    val driveLogin = _driveLogin.asStateFlow()
    private val _driveBackups = MutableStateFlow<List<DriveBackupEntry>>(emptyList())
    val driveBackups = _driveBackups.asStateFlow()
    private val _driveProgress = MutableStateFlow<DriveBackupStatus?>(null)
    val driveProgress = _driveProgress.asStateFlow()
    private val _driveRestoreCommand = MutableStateFlow<String?>(null)
    val driveRestoreCommand = _driveRestoreCommand.asStateFlow()
    fun clearDriveRestore() { _driveRestoreCommand.value = null }
    private var drivePoller: Job? = null

    fun driveRefresh() = driveAction("Loading Drive backup…") { _driveStatus.value = api.driveStatus(); if (_driveStatus.value?.connected == true) _driveBackups.value = api.driveList() }
    fun driveLoginStart() = driveAction("Starting Drive sign-in…") {
        val start = api.driveLoginStart(); _driveLogin.value = start
        drivePoller?.cancel(); drivePoller = tools.scope.launch {
            val until = System.currentTimeMillis() + start.expiresIn * 1000L
            while (System.currentTimeMillis() < until && _driveLogin.value != null) {
                delay(start.interval.coerceAtLeast(5) * 1000L)
                try { if (api.driveLoginPoll()["connected"]?.jsonPrimitive?.booleanOrNull == true) { _driveLogin.value = null; driveRefresh(); return@launch } }
                catch (e: ApiException) { if (e.code != Codes.NETWORK && e.code != Codes.TIMEOUT) { _driveError.value = friendly(e).first; _driveLogin.value = null; return@launch } }
            }
            if (_driveLogin.value != null) { _driveLogin.value = null; _driveError.value = "Drive sign-in code expired. Start again." }
        }
    }
    fun driveSetPassphrase(value: String) = driveAction("Saving backup passphrase…") { api.drivePassphrase(value); _driveStatus.value = api.driveStatus() }
    fun driveBackup() = driveAction("Starting Drive backup…") {
        api.driveBackup(); drivePoller?.cancel(); drivePoller = tools.scope.launch {
            try {
                do { _driveProgress.value = api.driveBackupStatus(); if (_driveProgress.value?.state == "running") delay(2_000) } while (_driveProgress.value?.state == "running")
                if (_driveProgress.value?.state == "failed") _driveError.value = _driveProgress.value?.message else { _driveError.value = null; _driveBackups.value = api.driveList(); _driveStatus.value = api.driveStatus() }
            } catch (e: ApiException) { _driveError.value = friendly(e).first }
        }
    }
    fun driveSchedule(on: Boolean) = driveAction("Updating Drive schedule…") { api.driveSchedule(on); _driveStatus.value = api.driveStatus() }
    fun driveDelete(id: String) = driveAction("Deleting Drive backup…") { api.driveDelete(id); _driveBackups.value = api.driveList() }
    fun driveRestore(id: String) = driveAction("Preparing Drive restore…") { _driveRestoreCommand.value = api.driveRestore(id)["command"]?.jsonPrimitive?.contentOrNull ?: "Restore command was not returned." }
    fun driveLogout() = driveAction("Disconnecting Drive…") { api.driveLogout(); _driveLogin.value = null; _driveStatus.value = api.driveStatus(); _driveBackups.value = emptyList() }
    private fun driveAction(label: String, block: suspend () -> Unit) = tools.action(label, onError = { _driveError.value = friendly(it).first }) { _driveError.value = null; block() }
}
