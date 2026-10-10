package life.mygig.clauderc.ui.vm

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import life.mygig.clauderc.api.ApiException
import life.mygig.clauderc.api.ChatHost
import life.mygig.clauderc.api.Codes
import life.mygig.clauderc.api.Host
import life.mygig.clauderc.api.HostHardenResult
import life.mygig.clauderc.api.HostKey
import life.mygig.clauderc.api.HostProbe
import life.mygig.clauderc.api.LauncherApi
import life.mygig.clauderc.data.cached
import life.mygig.clauderc.data.hostDraftStore
import life.mygig.clauderc.ui.friendly

/**
 * Hosts: web servers chats can work on (docs/hosts-design.md). The Add host dialog, its saved
 * draft, host keys, "Set up this server", "Add my key", and the open chat's hosts ([chatSession]).
 */
class HostsController(
    ctx: Context,
    private val api: LauncherApi,
    private val tools: VmTools,
    private val chatSession: () -> String?,
) {
    private val _hosts = MutableStateFlow<List<Host>?>(null)
    val hosts = _hosts.asStateFlow()
    /** The Add host dialog: open, the probed host key to confirm, and the server's last error shown in it. */
    private val _hostAddOpen = MutableStateFlow(false)
    val hostAddOpen = _hostAddOpen.asStateFlow()
    private val _hostProbe = MutableStateFlow<HostProbe?>(null)
    val hostProbe = _hostProbe.asStateFlow()
    /** The server's own words for a host problem: its auth_failed means the HOST refused our key, not this phone's key (friendly() says the latter). */
    private fun hostMessage(e: ApiException) = if (e.code == Codes.AUTH_FAILED) e.message else friendly(e).first
    private val _hostError = MutableStateFlow<String?>(null)
    val hostError = _hostError.asStateFlow()
    /** "Generate a key": the host name it was made for and its public half. */
    private val _hostKey = MutableStateFlow<Pair<String, HostKey>?>(null)
    val hostKey = _hostKey.asStateFlow()
    fun loadHosts() = tools.action("Loading servers…") { _hosts.value = api.hostList().hosts }
    /** What the Add server dialog keeps on the phone while you go and fetch things elsewhere: no secrets, only the plain fields and the PUBLIC key. */
    data class HostDraft(val name: String = "", val address: String = "", val port: String = "22", val user: String = "", val auth: String = "key")
    private val hostDraftPrefs = ctx.hostDraftStore.cached()
    private object HD {
        val NAME = stringPreferencesKey("name")
        val ADDRESS = stringPreferencesKey("address")
        val PORT = stringPreferencesKey("port")
        val USER = stringPreferencesKey("user")
        val AUTH = stringPreferencesKey("auth")
        val KEY_NAME = stringPreferencesKey("key_name")
        val KEY_PUB = stringPreferencesKey("key_pub")
        val KEY_FP = stringPreferencesKey("key_fp")
    }
    fun hostDraft() = hostDraftPrefs.get().let { p ->
        HostDraft(p[HD.NAME].orEmpty(), p[HD.ADDRESS].orEmpty(), p[HD.PORT] ?: "22", p[HD.USER].orEmpty(), p[HD.AUTH] ?: "key")
    }
    fun saveHostDraft(d: HostDraft) = hostDraftPrefs.edit { p ->
        p[HD.NAME] = d.name; p[HD.ADDRESS] = d.address; p[HD.PORT] = d.port; p[HD.USER] = d.user; p[HD.AUTH] = d.auth
    }
    private fun rememberHostKey(name: String, key: HostKey) {
        _hostKey.value = name to key
        hostDraftPrefs.edit { p -> p[HD.KEY_NAME] = name; p[HD.KEY_PUB] = key.publicKey; p[HD.KEY_FP] = key.fingerprint }
    }
    fun openHostAdd() {
        _hostProbe.value = null; _hostError.value = null; _hostAddOpen.value = true
        // The key made earlier is still waiting on the server (kept 24 h): show it again.
        val p = hostDraftPrefs.get()
        _hostKey.value = p[HD.KEY_NAME]?.let { n -> n to HostKey(p[HD.KEY_PUB].orEmpty(), p[HD.KEY_FP].orEmpty()) }
    }
    /** [keepDraft]: tapping outside the dialog keeps what was typed; Cancel and a finished add clear it. */
    fun closeHostAdd(keepDraft: Boolean = false) {
        _hostAddOpen.value = false; _hostProbe.value = null; _hostError.value = null; _hostKey.value = null
        if (!keepDraft) hostDraftPrefs.edit { it.clear() }
    }
    fun clearHostKey() { _hostKey.value = null }
    fun generateHostKey(name: String) {
        _hostKey.value = null
        _hostError.value = null
        tools.action("Generating a key…", onError = { _hostError.value = hostMessage(it) }) { rememberHostKey(name, api.hostKeygen(name)) }
    }
    fun importHostKey(name: String, keyBase64: String) {
        _hostKey.value = null
        _hostError.value = null
        tools.action("Importing the key…", onError = { _hostError.value = hostMessage(it) }) { rememberHostKey(name, api.hostKeyImport(name, keyBase64)) }
    }
    /** Address or port changed after Check: the shown key no longer applies. */
    fun clearHostProbe() { _hostProbe.value = null }
    fun probeHost(address: String, port: Int) {
        _hostProbe.value = null
        _hostError.value = null
        tools.action("Checking $address…", onError = { _hostError.value = hostMessage(it) }) { _hostProbe.value = api.hostProbe(address, port) }
    }
    fun addHost(name: String, address: String, port: Int, user: String, fingerprint: String, auth: String, secret: String) {
        _hostError.value = null
        tools.action("Adding $name…", onError = { _hostError.value = hostMessage(it) }) {
            api.hostAdd(name, address, port, user, fingerprint, auth, secret)
            closeHostAdd()
            openHostSetup(name, port)
            tools.say("$name added.")
            _hosts.value = api.hostList().hosts
        }
    }
    /** The "Set up this server" step: the host (name, SSH port) it is open for, the last result and the server's error shown in it. */
    private val _hostSetup = MutableStateFlow<Pair<String, Int>?>(null)
    val hostSetup = _hostSetup.asStateFlow()
    private val _hostHarden = MutableStateFlow<HostHardenResult?>(null)
    val hostHarden = _hostHarden.asStateFlow()
    private val _hostSetupError = MutableStateFlow<String?>(null)
    val hostSetupError = _hostSetupError.asStateFlow()
    fun openHostSetup(name: String, sshPort: Int) { _hostHarden.value = null; _hostSetupError.value = null; _hostSetup.value = name to sshPort }
    fun closeHostSetup() { _hostSetup.value = null; _hostHarden.value = null; _hostSetupError.value = null }
    fun hardenHost(name: String, ports: List<String>, steps: List<String>) {
        _hostHarden.value = null
        _hostSetupError.value = null
        tools.action("Setting up $name…", onError = {
            _hostSetupError.value = when (it.code) {
                "needs_sudo" -> "This user needs passwordless sudo (or use root)"
                else -> hostMessage(it)
            }
        }) { _hostHarden.value = api.hostHarden(name, ports, steps) }
    }
    /** "Download key": the host's private key held in memory just long enough to be written to the file the owner picks. */
    class HostKeyFile(val name: String, val bytes: ByteArray, val hint: String)
    private val _hostKeyFile = MutableStateFlow<HostKeyFile?>(null)
    val hostKeyFile = _hostKeyFile.asStateFlow()
    fun exportHostKey(h: Host) = tools.action("Getting the key for ${h.name}…") {
        val b64 = api.hostKeyExport(h.name)["private_key"]?.jsonPrimitive?.contentOrNull
            ?: throw ApiException(Codes.INTERNAL, "The server sent no key.")
        val bytes = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
        _hostKeyFile.value = HostKeyFile(h.name, bytes, "ssh -i ${h.name}-key ${h.user}@${h.address}" + (if (h.port != 22) " -p ${h.port}" else ""))
    }
    fun clearHostKeyFile() { _hostKeyFile.value?.bytes?.fill(0); _hostKeyFile.value = null }
    /** "Add my key": the host the dialog is open for, the server's answer and any error, all shown inside the dialog. */
    private val _hostAuthTarget = MutableStateFlow<String?>(null)
    val hostAuthTarget = _hostAuthTarget.asStateFlow()
    private val _hostAuthError = MutableStateFlow<String?>(null)
    val hostAuthError = _hostAuthError.asStateFlow()
    private val _hostAuthDone = MutableStateFlow<String?>(null)
    val hostAuthDone = _hostAuthDone.asStateFlow()
    fun openHostAuthorize(name: String) { _hostAuthError.value = null; _hostAuthDone.value = null; _hostAuthTarget.value = name }
    fun closeHostAuthorize() { _hostAuthTarget.value = null; _hostAuthError.value = null; _hostAuthDone.value = null }
    fun authorizeHostKey(name: String, publicKey: String) {
        _hostAuthError.value = null
        _hostAuthDone.value = null
        tools.action("Adding the key to $name…", onError = { _hostAuthError.value = hostMessage(it) }) {
            val added = api.hostAuthorize(name, publicKey)["added"]?.jsonPrimitive?.booleanOrNull == true
            _hostAuthDone.value = if (added) "Added. You can now sign in to $name with that key." else "That key was already on $name."
        }
    }
    fun testHost(name: String) = tools.action("Testing $name…") { api.hostTest(name); tools.say("$name: connection works.") }
    fun removeHost(name: String) = tools.action("Removing $name…") {
        api.hostRemove(name)
        _hosts.value = api.hostList().hosts
    }

    /** The open chat's hosts (attached or not); null until loaded. */
    private val _chatHosts = MutableStateFlow<List<ChatHost>?>(null)
    val chatHosts = _chatHosts.asStateFlow()
    fun loadChatHosts() {
        val s = chatSession() ?: return
        tools.action("Loading servers…") { _chatHosts.value = api.hostSession(s).hosts }
    }
    fun attachHost(name: String, on: Boolean) {
        val s = chatSession() ?: return
        tools.action("Saving…") { api.hostAttach(s, name, on); _chatHosts.value = api.hostSession(s).hosts }
    }
    /** The chat closed: its hosts go with it. */
    fun clearChatHosts() { _chatHosts.value = null }
}
