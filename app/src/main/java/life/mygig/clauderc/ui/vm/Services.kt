package life.mygig.clauderc.ui.vm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import life.mygig.clauderc.api.LauncherApi
import life.mygig.clauderc.api.ServiceDef
import life.mygig.clauderc.api.StatusData
import life.mygig.clauderc.ui.LoginKind
import life.mygig.clauderc.ui.friendly

/**
 * The + dialog and what it opens: CLI installs and setup guides, Android signing keys, the Apple
 * developer key, custom API keys and token services. [status] is the shared server status,
 * [refreshStatus] reloads it; login forms open through [logins].
 */
class ServicesController(
    private val api: LauncherApi,
    private val tools: VmTools,
    private val status: () -> StatusData?,
    private val refreshStatus: () -> Unit,
    private val logins: LoginsController,
) {
    /** The + dialog: services that aren't connected yet. */
    private val _showAdd = MutableStateFlow(false)
    val showAdd = _showAdd.asStateFlow()
    fun showAddService(show: Boolean) { _showAdd.value = show }

    /**
     * One tap from the + dialog: install the CLI if it's missing and we can,
     * then open the login form.
     */
    fun addService(kind: LoginKind) {
        _showAdd.value = false
        val st = status()
        val svc = when (kind) {
            LoginKind.GITLAB -> st?.services?.get("gitlab")
            LoginKind.DOCKER -> st?.services?.get("docker")
            else -> null
        }
        when {
            svc == null || svc.installed -> logins.showLogin(kind)
            kind == LoginKind.GITLAB -> installGlab()
            else -> showSetup(kind)
        }
    }

    /** A service whose CLI isn't on the server yet: the setup guide for it. */
    private val _setup = MutableStateFlow<LoginKind?>(null)
    val setup = _setup.asStateFlow()
    fun showSetup(kind: LoginKind?) { _setup.value = kind }

    fun installGlab() = installCli("glab") { _setup.value = null; logins.openForm(LoginKind.GITLAB) }

    /** Installs [name] on the server, then runs [then] (usually: open the login form). */
    fun installCli(name: String, sudoPassword: String = "", then: () -> Unit = {}) = tools.action("Installing $name on the server…") {
        val r = api.installCli(name, sudoPassword)
        tools.say(r["note"]?.jsonPrimitive?.contentOrNull ?: "$name installed")
        refreshStatus()
        then()
    }

    /** The Android signing keys dialog. */
    private val _showKeystores = MutableStateFlow(false)
    val showKeystores = _showKeystores.asStateFlow()
    fun showKeystores(show: Boolean) { _showAdd.value = false; _showKeystores.value = show }

    fun saveKeystore(name: String, alias: String, storePassword: String, keyPassword: String, file: ByteArray) =
        tools.action("Checking and saving the $name signing key…") {
            val b64 = android.util.Base64.encodeToString(file, android.util.Base64.NO_WRAP)
            api.loginKeystore(name, alias, storePassword, keyPassword.ifEmpty { storePassword }, b64)
            tools.say("$name signing key saved. Restart sessions to use it.")
            refreshStatus()
        }

    private val _showApple = MutableStateFlow(false)
    val showApple = _showApple.asStateFlow()
    fun showApple(show: Boolean) { _showAdd.value = false; _showApple.value = show }

    fun saveApple(keyId: String, issuerId: String, teamId: String, file: ByteArray) =
        tools.action("Checking and saving the Apple developer key…") {
            val b64 = android.util.Base64.encodeToString(file, android.util.Base64.NO_WRAP)
            api.loginApple(keyId, issuerId, teamId, b64)
            tools.say("Apple developer key saved. Restart sessions to use it.")
            refreshStatus()
        }

    fun removeApple() = tools.action("Removing the Apple developer key…") {
        api.removeApple()
        tools.say("Apple developer key removed.")
        refreshStatus()
    }

    fun removeKeystore(name: String) = tools.action("Removing the $name signing key…") {
        api.removeKeystore(name)
        tools.say("$name signing key removed.")
        refreshStatus()
    }

    /** The custom API keys dialog. */
    private val _showCustom = MutableStateFlow(false)
    val showCustom = _showCustom.asStateFlow()
    fun showCustomKeys(show: Boolean) { _showAdd.value = false; _showCustom.value = show }

    fun setSecret(name: String, value: String) = tools.action("Saving $name…") {
        api.setSecret(name, value)
        tools.say("$name saved. Restart sessions to use it.")
        refreshStatus()
    }

    fun setSecretFile(name: String, file: ByteArray) = tools.action("Saving $name…") {
        val b64 = android.util.Base64.encodeToString(file, android.util.Base64.NO_WRAP)
        api.setSecretFile(name, b64)
        tools.say("$name saved (it holds the file's path). Restart sessions to use it.")
        refreshStatus()
    }

    fun removeSecret(name: String) = tools.action("Removing $name…") {
        api.removeSecret(name)
        tools.say("$name removed. Restart sessions to drop it.")
        refreshStatus()
    }

    /** The connect form for a token service (see Catalog), by id. */
    private val _tokenService = MutableStateFlow<String?>(null)
    val tokenService = _tokenService.asStateFlow()
    private val _tokenError = MutableStateFlow<String?>(null)
    val tokenError = _tokenError.asStateFlow()
    fun showTokenService(id: String?) { _tokenError.value = null; _tokenService.value = id }

    /** One tap from +: install the CLI if the server can and it's missing, then open the form. */
    fun addTokenService(def: ServiceDef) {
        _showAdd.value = false
        val st = status()
        val missing = st?.services?.get(def.id)?.installed == false
        if (def.install != null && missing) {
            installCli(def.install) { showTokenService(def.id) }
        } else {
            showTokenService(def.id)
        }
    }

    fun tokenLogin(def: ServiceDef, values: List<String>) {
        _tokenError.value = null
        tools.action("Checking with ${def.name}…", onError = { e ->
            if (_tokenService.value == def.id) _tokenError.value = friendly(e).first else tools.report(e)
        }) {
            val r = api.loginToken(def.id, values)
            if (_tokenService.value == def.id) _tokenService.value = null
            tools.say("${def.name} connected" + (r.user?.let { ": $it" } ?: "") + ". Restart running sessions to use it.")
            refreshStatus()
        }
    }
}
