package life.mygig.clauderc.ui.screens

import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import life.mygig.clauderc.api.Catalog
import life.mygig.clauderc.api.ServiceDef
import life.mygig.clauderc.api.TailResult
import life.mygig.clauderc.api.Updates
import life.mygig.clauderc.ui.LoginKind
import life.mygig.clauderc.ui.MainViewModel
import life.mygig.clauderc.ui.openUrl

private const val GITHUB_TOKEN_URL =
    "https://github.com/settings/tokens/new?scopes=repo,read:org,gist,workflow,delete_repo&description=cLaudeRC%20server"

@Composable
fun LoginDialog(vm: MainViewModel, kind: LoginKind) {
    val busy by vm.busy.collectAsState()
    AlertDialog(
        onDismissRequest = { if (busy == null) vm.showLogin(null) },
        title = {
            Text(
                when (kind) {
                    LoginKind.CLAUDE -> "Log in to Claude"
                    LoginKind.GITHUB -> "Log in to GitHub"
                    LoginKind.AWS -> "Log in to AWS"
                    LoginKind.GITLAB -> "Log in to GitLab"
                    LoginKind.YOUTUBE -> "Connect YouTube"
                    LoginKind.DOCKER -> "Log in to a container registry"
                },
            )
        },
        text = {
            val error by vm.loginError.collectAsState()
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                when (kind) {
                    LoginKind.CLAUDE -> ClaudeLogin(vm, busy != null)
                    LoginKind.GITHUB -> GithubLogin(vm, busy != null)
                    LoginKind.AWS -> AwsLogin(vm, busy != null)
                    LoginKind.GITLAB -> GitlabLogin(vm, busy != null)
                    LoginKind.YOUTUBE -> YoutubeLogin(vm, busy != null)
                    LoginKind.DOCKER -> DockerLogin(vm, busy != null)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { vm.showLogin(null) }) { Text("Close") } },
    )
}

/** The + button: every service that isn't connected yet, one tap to set it up. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddServiceDialog(vm: MainViewModel) {
    val status by vm.status.collectAsState()
    val st = status
    val sv = st?.services.orEmpty()
    // name, hint, what a tap does
    val options = buildList<Triple<String, String, () -> Unit>> {
        if (st?.aws?.loggedIn != true) add(Triple("AWS", "Access keys or SSO for the AWS CLI") { vm.addService(LoginKind.AWS) })
        if (sv["gitlab"]?.loggedIn != true) {
            add(Triple("GitLab", if (sv["gitlab"]?.installed == false) "Installs glab, then asks for a token" else "Personal access token") {
                vm.addService(LoginKind.GITLAB)
            })
        }
        if (sv["docker"]?.loggedIn != true) {
            add(Triple("Docker Hub / GHCR", if (sv["docker"]?.installed == false) "Installs Docker (asks for your sudo password)" else "Push and pull images") {
                vm.addService(LoginKind.DOCKER)
            })
        }
        if (sv["youtube"]?.loggedIn != true) {
            add(Triple("YouTube", "Upload videos to your channel (youtube-upload)") { vm.showAddService(false); vm.showLogin(LoginKind.YOUTUBE) })
        }
        add(Triple("Apple developer account", "App Store Connect API key for signing and uploading iOS apps") { vm.showApple(true) })
        add(Triple("Android signing key", "Upload keystore (.jks) for signing app bundles") { vm.showKeystores(true) })
        add(Triple("Custom API key", "Any other API: a name like ACME_API_KEY and its value") { vm.showCustomKeys(true) })
        Catalog.services.forEach { def ->
            val svc = sv[def.id]
            if (svc?.loggedIn != true) {
                val hint = if (def.install != null && svc?.installed == false) "Installs ${def.install}, then asks for credentials" else def.hint
                add(Triple(def.name, hint) { vm.addTokenService(def) })
            }
        }
    }
    var query by remember { mutableStateOf("") }
    val shown = options.filter { query.isBlank() || it.first.contains(query.trim(), true) || it.second.contains(query.trim(), true) }
        .sortedBy { it.first.lowercase() }
    ModalBottomSheet(onDismissRequest = { vm.showAddService(false) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight(0.92f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Connect a service", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search services") }, shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            )
            if (st == null) Text("Check the server first (pull down on Connections).")
            if (options.isEmpty() && st != null) Text("Everything here is already connected.")
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(shown.size) { i ->
                    val (name, hint, go) = shown[i]
                    Surface(
                        onClick = { go() },
                        enabled = st != null,
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("A–Z · connected ones move to the Connections tab", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 24.dp))
                }
            }
        }
    }
}

/** Android signing keys: pick a .jks from the phone, give it a short name; every session gets NAME_KEYSTORE_*. */
@Composable
fun KeystoresDialog(vm: MainViewModel) {
    val context = LocalContext.current
    val status by vm.status.collectAsState()
    val busy by vm.busy.collectAsState()
    var name by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf("") }
    var storePw by remember { mutableStateOf("") }
    var keyPw by remember { mutableStateOf("") }
    var file by remember { mutableStateOf<ByteArray?>(null) }
    var fileName by remember { mutableStateOf("") }
    var confirmRemove by remember { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            if (bytes == null || bytes.isEmpty() || bytes.size > 100_000) {
                vm.say("That file couldn't be read (or it's too big for a keystore).")
            } else {
                file = bytes
                fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "keystore"
            }
        }
    }
    val nameOk = Regex("^[A-Z][A-Z0-9_]{0,30}$").matches(name) && !name.startsWith("CLAUDE") && !name.startsWith("ANTHROPIC")
    val pwOk = storePw.isNotEmpty() && !storePw.contains('\'') && !keyPw.contains('\'')
    AlertDialog(
        onDismissRequest = { if (busy == null) vm.showKeystores(false) },
        title = { Text("Android signing keys") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Your app's upload key, for signing bundles before they go to Google Play. The server checks the " +
                        "passwords, keeps the file private, and every session gets NAME_KEYSTORE_FILE, " +
                        "NAME_KEYSTORE_PASSWORD, NAME_KEY_ALIAS and NAME_KEY_PASSWORD after a restart.",
                    style = MaterialTheme.typography.bodySmall,
                )
                status?.keystores.orEmpty().forEach { n ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(n, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                        TextButton(onClick = { confirmRemove = n }, enabled = busy == null) { Text("Remove") }
                    }
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it.uppercase().replace(Regex("[^A-Z0-9_]"), "_") },
                    label = { Text("Short name, e.g. GTG") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    isError = name.isNotEmpty() && !nameOk,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                )
                OutlinedButton(onClick = { pick.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (file == null) "Choose keystore file (.jks)" else "File: $fileName")
                }
                OutlinedTextField(
                    value = alias, onValueChange = { alias = it.trim() },
                    label = { Text("Key alias, e.g. upload") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                )
                OutlinedTextField(
                    value = storePw, onValueChange = { storePw = it },
                    label = { Text("Keystore password") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                )
                OutlinedTextField(
                    value = keyPw, onValueChange = { keyPw = it },
                    label = { Text("Key password (empty = same)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                )
                Button(
                    onClick = {
                        val f = file ?: return@Button
                        vm.saveKeystore(name, alias, storePw, keyPw, f)
                        storePw = ""; keyPw = ""; file = null; fileName = ""
                    },
                    enabled = busy == null && nameOk && file != null && alias.isNotBlank() && pwOk,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Check and save") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { vm.showKeystores(false) }) { Text("Close") } },
    )
    confirmRemove?.let { n ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove the $n signing key?") },
            text = { Text("It's deleted from the server. Keep your own backup: Google Play only accepts bundles signed with this upload key.") },
            confirmButton = { TextButton(onClick = { confirmRemove = null; vm.removeKeystore(n) }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel") } },
        )
    }
}

/** Apple developer account: an App Store Connect API key (.p8) with its key ID and issuer ID. */
@Composable
fun AppleDialog(vm: MainViewModel) {
    val context = LocalContext.current
    val status by vm.status.collectAsState()
    val busy by vm.busy.collectAsState()
    var keyId by remember { mutableStateOf("") }
    var issuer by remember { mutableStateOf("") }
    var team by remember { mutableStateOf("") }
    var file by remember { mutableStateOf<ByteArray?>(null) }
    var fileName by remember { mutableStateOf("") }
    var confirmRemove by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            if (bytes == null || bytes.isEmpty() || bytes.size > 10_000) {
                vm.say("That file couldn't be read (or it's too big for a .p8 key).")
            } else {
                file = bytes
                fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "AuthKey.p8"
            }
        }
    }
    val idOk = Regex("^[A-Z0-9]{10}$").matches(keyId)
    val issuerOk = Regex("^[0-9a-fA-F-]{36}$").matches(issuer)
    val teamOk = team.isEmpty() || Regex("^[A-Z0-9]{10}$").matches(team)
    AlertDialog(
        onDismissRequest = { if (busy == null) vm.showApple(false) },
        title = { Text("Apple developer account") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "In App Store Connect › Users and Access › Integrations, create an API key and download its " +
                        ".p8 (Apple shows it once). The server keeps the file private; every session gets " +
                        "APPLE_API_KEY_ID, APPLE_API_ISSUER_ID, APPLE_API_KEY_FILE and APPLE_TEAM_ID after a restart.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (status?.apple == true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("A key is saved", modifier = Modifier.weight(1f))
                        TextButton(onClick = { confirmRemove = true }, enabled = busy == null) { Text("Remove") }
                    }
                }
                OutlinedTextField(
                    value = keyId, onValueChange = { keyId = it.trim().uppercase() },
                    label = { Text("Key ID (10 characters)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    isError = keyId.isNotEmpty() && !idOk,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                )
                OutlinedTextField(
                    value = issuer, onValueChange = { issuer = it.trim() },
                    label = { Text("Issuer ID (UUID)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    isError = issuer.isNotEmpty() && !issuerOk,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                )
                OutlinedTextField(
                    value = team, onValueChange = { team = it.trim().uppercase() },
                    label = { Text("Team ID (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    isError = !teamOk,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                )
                OutlinedButton(onClick = { pick.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (file == null) "Choose the .p8 key file" else "File: $fileName")
                }
                Button(
                    onClick = {
                        val f = file ?: return@Button
                        vm.saveApple(keyId, issuer, team, f)
                        file = null; fileName = ""
                    },
                    enabled = busy == null && idOk && issuerOk && teamOk && file != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { vm.showApple(false) }) { Text("Close") } },
    )
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove the Apple developer key?") },
            text = { Text("It's deleted from the server. You can create a new key in App Store Connect any time.") },
            confirmButton = { TextButton(onClick = { confirmRemove = false; vm.removeApple() }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }
}

/** Custom API keys: add a NAME=value every session sees, or remove one. Values are never shown. */
@Composable
fun CustomKeysDialog(vm: MainViewModel) {
    val status by vm.status.collectAsState()
    val busy by vm.busy.collectAsState()
    var name by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var confirmRemove by remember { mutableStateOf<String?>(null) }
    val nameOk = Updates.CUSTOM_NAME.matches(name) && !name.startsWith("CLAUDE_") && !name.startsWith("ANTHROPIC_")
    AlertDialog(
        onDismissRequest = { if (busy == null) vm.showCustomKeys(false) },
        title = { Text("Custom API keys") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NoAutofill()
                Text(
                    "For APIs without a built-in service. The key reaches every Claude session and MCP server after a " +
                        "restart, as an environment variable (use \${NAME} in MCP configs).",
                    style = MaterialTheme.typography.bodySmall,
                )
                status?.custom.orEmpty().forEach { n ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(n, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { confirmRemove = n }, enabled = busy == null) { Text("Remove") }
                    }
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it.uppercase().replace(Regex("[^A-Z0-9_]"), "_") },
                    label = { Text("Name, e.g. ACME_API_KEY") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    isError = name.isNotEmpty() && !nameOk,
                    supportingText = { Text(if (name.isNotEmpty() && !nameOk) "The name must end in one of the endings below" else "Pick an ending below") },
                    keyboardOptions = KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters, autoCorrectEnabled = false),   // caps from the keyboard itself, so the text isn't rewritten mid-typing
                )
                // Tapping an ending adds it to the name (replacing an ending already there).
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("_KEY", "_TOKEN", "_SECRET", "_PASSWORD", "_USERNAME", "_URL", "_ID", "_HOST", "_SERVER", "_EMAIL", "_REGION", "_PROJECT", "_ENDPOINT", "_ORG", "_ACCOUNT", "_USER", "_AUTHKEY", "_APIKEY")) { e ->
                        androidx.compose.material3.AssistChip(
                            onClick = { name = Regex("_(KEY|TOKEN|SECRET|PASSWORD|USERNAME|USER|SERVER|HOST|URL|ID|EMAIL|REGION|PROJECT|ENDPOINT|ORG|ACCOUNT|AUTHKEY|APIKEY)$").replace(name, "").trimEnd('_') + e },
                            label = { Text(e) },
                        )
                    }
                }
                OutlinedTextField(
                    value = value, onValueChange = { value = it.trim() },
                    label = { Text("Value") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                )
                Button(
                    onClick = { vm.setSecret(name, value); name = ""; value = "" },
                    enabled = busy == null && nameOk && value.isNotEmpty() && !value.contains('\''),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { vm.showCustomKeys(false) }) { Text("Close") } },
    )
    confirmRemove?.let { n ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove $n?") },
            text = { Text("Sessions lose it after their next restart.") },
            confirmButton = { TextButton(onClick = { confirmRemove = null; vm.removeSecret(n) }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel") } },
        )
    }
}

/** Connect form for a token service: create link, what Claude needs, the fields. */
@Composable
fun TokenServiceDialog(vm: MainViewModel, def: ServiceDef) {
    val context = LocalContext.current
    val busy by vm.busy.collectAsState()
    val error by vm.tokenError.collectAsState()
    val values = remember(def.id) { mutableStateListOf(*Array(def.fields.size) { "" }) }
    var needs by remember { mutableStateOf(false) }
    // JSON keys can be picked as a file instead of pasted (the field that's multiline).
    val jsonField = def.fields.indexOfFirst { it.multiline }
    val pickJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && jsonField >= 0) {
            val text = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            if (text.isNullOrBlank() || text.length > 20_000) vm.say("That file couldn't be read.") else values[jsonField] = text.trim()
        }
    }
    AlertDialog(
        onDismissRequest = { if (busy == null) vm.showTokenService(null) },
        title = { Text("Connect ${def.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                OutlinedButton(onClick = { openUrl(context, def.createUrl) }, modifier = Modifier.fillMaxWidth()) { Text(def.createLabel) }
                TextButton(onClick = { needs = !needs }) { Text(if (needs) "Hide what Claude needs" else "What Claude needs (step by step)") }
                if (needs) SelectionContainer { Text(def.needs, style = MaterialTheme.typography.bodySmall) }
                if (jsonField >= 0) {
                    Button(onClick = { pickJson.launch(arrayOf("application/json", "text/plain", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (values[jsonField].isBlank()) "Choose JSON key file" else "JSON key loaded — choose another")
                    }
                    Text("…or paste it below.", style = MaterialTheme.typography.bodySmall)
                }
                def.fields.forEachIndexed { i, f ->
                    OutlinedTextField(
                        value = values[i],
                        onValueChange = { values[i] = if (f.multiline) it else it.trim() },
                        label = { Text(f.label) },
                        singleLine = !f.multiline,
                        minLines = if (f.multiline) 4 else 1,
                        maxLines = if (f.multiline) 8 else 1,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (f.secret && !f.multiline) PasswordVisualTransformation() else VisualTransformation.None,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (f.secret && !f.multiline) KeyboardType.Password else KeyboardType.Text,
                            autoCorrectEnabled = false,
                        ),
                    )
                }
                Button(
                    onClick = { vm.tokenLogin(def, values.toList()) },
                    enabled = busy == null && def.fields.indices.all { values[it].trim().length >= def.fields[it].minLength },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Connect") }
                Text(
                    "Checked with ${def.name}, then kept in a private file on the server that Claude's sessions load. " +
                        "It travels over SSH on stdin only.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { vm.showTokenService(null) }) { Text("Close") } },
    )
}

/** Getting a service's CLI onto the server. */
@Composable
fun SetupDialog(vm: MainViewModel, kind: LoginKind) {
    val busy by vm.busy.collectAsState()
    var sudoPw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (busy == null) vm.showSetup(null) },
        title = { Text(if (kind == LoginKind.GITLAB) "Set up the GitLab CLI" else "Set up Docker") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (kind == LoginKind.GITLAB) {
                    Text(
                        "GitLab needs its command-line tool, glab, on the server. Install downloads the latest " +
                            "official release from gitlab.com into ~/.local/bin, checks it against the release's " +
                            "checksums, and needs no sudo. Then you can log in.",
                    )
                    Button(onClick = { vm.installGlab() }, enabled = busy == null, modifier = Modifier.fillMaxWidth()) {
                        Text("Install glab")
                    }
                } else {
                    Text(
                        "Docker needs root to install. Enter your sudo password and the server runs Docker's official " +
                            "install script (get.docker.com), then adds you to the docker group. The password is used once " +
                            "and never saved.",
                    )
                    OutlinedTextField(
                        value = sudoPw, onValueChange = { sudoPw = it },
                        label = { Text("sudo password") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    )
                    Button(
                        onClick = { vm.installCli("docker", sudoPw) { vm.showSetup(null); vm.showLogin(LoginKind.DOCKER) } },
                        enabled = busy == null,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Install Docker") }
                    Text("This can take a few minutes.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { vm.showSetup(null) }) { Text("Close") } },
    )
}

@Composable
private fun ClaudeLogin(vm: MainViewModel, busy: Boolean) {
    val context = LocalContext.current
    val url by vm.loginUrl.collectAsState()
    var code by remember { mutableStateOf("") }
    val u = url
    if (u == null) {
        Text("Starts `claude auth login` on the server using your claude.ai subscription (never an API key).")
        Button(onClick = { vm.claudeLoginStart() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Start login")
        }
    } else {
        Text("1. Open the link and approve the login.")
        Button(onClick = { openUrl(context, u.url) }, modifier = Modifier.fillMaxWidth()) { Text("Open link") }
        Text("2. Paste the code it shows you.")
        OutlinedTextField(
            value = code, onValueChange = { code = it.trim() },
            label = { Text("Code") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
        )
        Button(
            onClick = { vm.claudeLoginCode(code) },
            enabled = !busy && code.length >= 4,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Submit code") }
    }
}

@Composable
private fun GithubLogin(vm: MainViewModel, busy: Boolean) {
    val context = LocalContext.current
    var token by remember { mutableStateOf("") }
    Text(
        "Create a classic personal access token with these scopes: repo, read:org, gist, workflow (and delete_repo to delete repos from the app).",
    )
    OutlinedButton(onClick = { openUrl(context, GITHUB_TOKEN_URL) }, modifier = Modifier.fillMaxWidth()) {
        Text("Create token on GitHub")
    }
    PermissionsLink(LoginKind.GITHUB)
    OutlinedTextField(
        value = token, onValueChange = { token = it.trim() },
        label = { Text("Token") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
    )
    Button(
        onClick = { vm.githubLogin(token) },
        enabled = !busy && token.length >= 20,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Save token") }
    Text("The token is sent over SSH on stdin only, never on a command line.", style = MaterialTheme.typography.bodySmall)
}

/**
 * Exactly which permissions each token needs, shown from the login dialog.
 * Names and paths are taken from each provider's docs (checked 2026-09).
 */
private fun permissionsText(kind: LoginKind): String = when (kind) {
    LoginKind.GITHUB ->
        "Personal access token (classic). Fine-grained tokens aren't supported here.\n\n" +
            "Profile picture › Settings › Developer settings › Personal access tokens › Tokens (classic) › " +
            "Generate new token › Generate new token (classic). The button above opens it pre-filled.\n\n" +
            "Select these scopes:\n" +
            "• repo: full access to your public and private repositories (create, clone, push)\n" +
            "• read:org: read organization membership, so the app can list your orgs " +
            "(admin:org or write:org include it)\n" +
            "• gist (optional): in the GitHub CLI's documented minimum; cLaudeRC doesn't use it\n" +
            "• workflow: add and update GitHub Actions workflow files\n\n" +
            "If an organization uses SAML SSO, authorize the token for it afterwards (Configure SSO)."
    LoginKind.AWS ->
        "Access keys for an IAM user, never the root user.\n\n" +
            "IAM console › Users › your user › Security credentials › Access keys › Create access key › " +
            "use case: Command Line Interface (CLI) › Next › Create access key.\n\n" +
            "Permissions: the app itself only calls sts:GetCallerIdentity, which needs no permission. " +
            "Attach only the policies for what Claude should manage, for example:\n" +
            "• AmazonS3FullAccess or AmazonS3ReadOnlyAccess\n" +
            "• AWSLambda_FullAccess\n" +
            "• CloudWatchLogsReadOnlyAccess\n" +
            "• ReadOnlyAccess (look, don't touch)\n" +
            "Avoid AdministratorAccess unless Claude should be able to change anything.\n\n" +
            "SSO instead: run 'aws configure sso --use-device-code' on the server once. The permission set you " +
            "pick there (AWS suggests PowerUserAccess) decides access."
    LoginKind.GITLAB ->
        "Personal access token (legacy).\n\n" +
            "Avatar › Edit profile › Access › Personal access tokens › Generate token › Legacy token. " +
            "The button above opens it pre-filled on gitlab.com.\n\n" +
            "Select these scopes (what the GitLab CLI requires):\n" +
            "• api: full read/write API access; for a personal token it also covers git push over HTTPS\n" +
            "• write_repository: pull and push over HTTPS\n\n" +
            "Expiration date: required, at most 365 days by default. Reconnect here when it expires."
    LoginKind.DOCKER ->
        "Docker Hub: Docker Home (app.docker.com) › your avatar › Account settings › Personal access tokens › " +
            "Generate new token.\n" +
            "• Access permissions: Read & Write to push images (Read-only if you only pull)\n" +
            "• Username: your Docker Hub username; the token is the password\n\n" +
            "GHCR (ghcr.io) needs a GitHub personal access token (classic); fine-grained tokens don't work:\n" +
            "• write:packages: push and pull images\n" +
            "• delete:packages: only if Claude should delete images\n" +
            "• Ticking write:packages also ticks repo; untick repo if you don't need it\n" +
            "• Username: your GitHub username"
    LoginKind.YOUTUBE ->
        "YouTube needs your own sign-in (Google doesn't allow service accounts to upload). One-time setup in " +
            "Google Cloud Console:\n" +
            "1. Enable \"YouTube Data API v3\" (APIs & Services › Library).\n" +
            "2. Google Auth Platform › Branding: app name and your email. Audience: External, then Publish app " +
            "so it's \"In production\". Unverified is fine for your own channel (you'll see a warning; continue). " +
            "In \"Testing\" Google expires the login after 7 days.\n" +
            "3. Google Auth Platform › Clients › Create client › type \"TVs and Limited Input devices\" › Create. " +
            "Copy the client ID and secret here.\n" +
            "4. Start sign-in: open google.com/device, enter the code, pick the Google account that owns the channel, " +
            "allow access.\n\n" +
            "Good to know (Google's rules):\n" +
            "• Videos uploaded through the API by projects Google hasn't audited stay private until the project " +
            "passes an audit (you can change them in YouTube Studio).\n" +
            "• 100 uploads per day.\n\n" +
            "Saved as YOUTUBE_CLIENT_ID, YOUTUBE_CLIENT_SECRET, YOUTUBE_REFRESH_TOKEN. Claude uploads with: " +
            "youtube-upload video.mp4 --title \"…\" --description \"…\" --privacy unlisted"
    LoginKind.CLAUDE -> ""
}

@Composable
private fun PermissionsLink(kind: LoginKind) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text("What Claude needs (step by step)") }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("What Claude needs") },
            text = {
                SelectionContainer(Modifier.verticalScroll(rememberScrollState())) { Text(permissionsText(kind)) }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Got it") } },
        )
    }
}

@Composable
private fun SecretField(value: String, label: String, onChange: (String) -> Unit) = OutlinedTextField(
    value = value, onValueChange = { onChange(it.trim()) },
    label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
    visualTransformation = PasswordVisualTransformation(),
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
)

@Composable
private fun PlainField(value: String, label: String, onChange: (String) -> Unit) = OutlinedTextField(
    value = value, onValueChange = { onChange(it.trim()) },
    label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
)

@Composable
private fun YoutubeLogin(vm: MainViewModel, busy: Boolean) {
    val context = LocalContext.current
    val url by vm.loginUrl.collectAsState()
    var id by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    val u = url
    if (u == null) {
        Text("Enter your Google OAuth client (type TVs and Limited Input devices), then sign in with the account that owns the channel.")
        OutlinedButton(onClick = { openUrl(context, "https://console.cloud.google.com/auth/clients") }, modifier = Modifier.fillMaxWidth()) {
            Text("Open Google Auth Platform › Clients")
        }
        PermissionsLink(LoginKind.YOUTUBE)
        PlainField(id, "Client ID (…apps.googleusercontent.com)") { id = it }
        SecretField(secret, "Client secret") { secret = it }
        Button(
            onClick = { vm.youtubeStart(id, secret) },
            enabled = !busy && id.endsWith(".apps.googleusercontent.com") && secret.length >= 10,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Start sign-in") }
    } else {
        Text("Open the link, enter this code, choose the account that owns your channel, and allow access.")
        u.code?.let {
            SelectionContainer {
                Text(it, style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace)
            }
        }
        Button(onClick = { openUrl(context, u.url) }, modifier = Modifier.fillMaxWidth()) { Text("Open ${u.url.removePrefix("https://")}") }
        Text("Waiting for approval… this closes by itself when YouTube is connected.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun GitlabLogin(vm: MainViewModel, busy: Boolean) {
    val context = LocalContext.current
    var token by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("gitlab.com") }
    Text("Create a legacy personal access token with the api and write_repository scopes.")
    OutlinedButton(onClick = { openUrl(context, "https://$host/-/user_settings/personal_access_tokens/legacy/new?name=cLaudeRC&scopes=api%2Cwrite_repository") }, modifier = Modifier.fillMaxWidth()) {
        Text("Create token on GitLab")
    }
    PermissionsLink(LoginKind.GITLAB)
    PlainField(host, "GitLab host") { host = it }
    SecretField(token, "Token") { token = it }
    Button(onClick = { vm.gitlabLogin(token, host) }, enabled = !busy && token.length >= 20, modifier = Modifier.fillMaxWidth()) {
        Text("Connect")
    }
}

@Composable
private fun DockerLogin(vm: MainViewModel, busy: Boolean) {
    var registry by remember { mutableStateOf("docker.io") }
    var user by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    Text("Docker Hub: use an access token. GitHub (ghcr.io): a token with write:packages.")
    PermissionsLink(LoginKind.DOCKER)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        listOf("docker.io", "ghcr.io").forEachIndexed { i, r ->
            SegmentedButton(selected = registry == r, onClick = { registry = r }, shape = SegmentedButtonDefaults.itemShape(i, 2)) {
                Text(if (r == "docker.io") "Docker Hub" else "GHCR")
            }
        }
    }
    PlainField(registry, "Registry") { registry = it }
    PlainField(user, "Username") { user = it }
    SecretField(token, "Token or password") { token = it }
    Button(
        onClick = { vm.dockerLogin(registry, user, token) },
        enabled = !busy && user.isNotBlank() && token.length >= 8,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Connect") }
}

@Composable
private fun AwsLogin(vm: MainViewModel, busy: Boolean) {
    val context = LocalContext.current
    val status by vm.status.collectAsState()
    val url by vm.loginUrl.collectAsState()
    var sso by remember { mutableStateOf(status?.aws?.ssoConfigured == true) }
    var keyId by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("us-east-1") }

    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(selected = !sso, onClick = { sso = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
            Text("Access keys")
        }
        SegmentedButton(selected = sso, onClick = { sso = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
            Text("SSO")
        }
    }
    PermissionsLink(LoginKind.AWS)
    if (!sso) {
        OutlinedTextField(
            value = keyId, onValueChange = { keyId = it.trim() },
            label = { Text("Access key ID") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
        )
        OutlinedTextField(
            value = secret, onValueChange = { secret = it.trim() },
            label = { Text("Secret access key") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        )
        OutlinedTextField(
            value = region, onValueChange = { region = it.trim() },
            label = { Text("Region") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { vm.awsKeysLogin(keyId, secret, region) },
            enabled = !busy && keyId.length >= 16 && secret.length >= 16 && region.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save keys") }
        Text("Output format is set to json.", style = MaterialTheme.typography.bodySmall)
    } else {
        val u = url
        if (u == null) {
            if (status?.aws?.ssoConfigured == false) {
                Text("SSO isn't configured on the server yet. Use access keys, or run `aws configure sso` there once.")
            }
            Button(onClick = { vm.awsSsoStart() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Start SSO login")
            }
        } else {
            Text("Open the link, confirm the code matches, and approve.")
            u.code?.let {
                SelectionContainer {
                    Text(it, style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace)
                }
            }
            Button(onClick = { openUrl(context, u.url) }, modifier = Modifier.fillMaxWidth()) { Text("Open link") }
            Text("Waiting for approval… this closes by itself when AWS is logged in.", style = MaterialTheme.typography.bodySmall)
        }
    }
}


/**
 * Full-screen dialogs only get the keyboard's insets once they change, so a keyboard that opens
 * with the dialog (tap in the box) can first sit over the bottom. Resize the window for it and
 * re-send the insets whenever the keyboard shows or hides.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DialogKeyboardFix() {
    val view = androidx.compose.ui.platform.LocalView.current
    val window = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
    val imeVisible = WindowInsets.isImeVisible
    androidx.compose.runtime.SideEffect {
        window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
    androidx.compose.runtime.LaunchedEffect(imeVisible) { androidx.core.view.ViewCompat.requestApplyInsets(view) }
}

/** Keeps Samsung Pass / autofill off this dialog: a name plus a secret field looks like a login to it, and its icon wrecks the keyboard. */
@Composable
internal fun NoAutofill() {
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        var v: android.view.View? = view
        while (v != null) { v.importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS; v = v.parent as? android.view.View }
    }
}
