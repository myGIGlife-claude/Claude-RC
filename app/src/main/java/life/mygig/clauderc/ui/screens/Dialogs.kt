package life.mygig.clauderc.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Arrangement
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
import life.mygig.clauderc.ui.LocalGuard
import life.mygig.clauderc.ui.LoginKind
import life.mygig.clauderc.ui.MainViewModel
import life.mygig.clauderc.ui.openUrl

private const val GITHUB_TOKEN_URL =
    "https://github.com/settings/tokens/new?scopes=repo,read:org,gist,workflow&description=cLaudeRC%20server"

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
                    LoginKind.DOCKER -> DockerLogin(vm, busy != null)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { vm.showLogin(null) }) { Text("Close") } },
    )
}

/** The + button: every service that isn't connected yet, one tap to set it up. */
@Composable
fun AddServiceDialog(vm: MainViewModel) {
    val status by vm.status.collectAsState()
    val guard = LocalGuard.current
    val st = status
    val sv = st?.services.orEmpty()
    val newServer = (st?.scriptApi ?: 0) >= Updates.TOKEN_SERVICES_API
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
        Catalog.services.filter { it.id == "cloudflare" || newServer }.forEach { def ->
            val svc = sv[def.id]
            if (svc?.loggedIn != true) {
                val hint = if (def.install != null && svc?.installed == false) "Installs ${def.install}, then asks for credentials" else def.hint
                add(Triple(def.name, hint) { vm.addTokenService(def) })
            }
        }
    }
    AlertDialog(
        onDismissRequest = { vm.showAddService(false) },
        title = { Text("Connect a service") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (st == null) Text("Check the server first (pull down on Status).")
                if (st != null && !newServer) {
                    Text("Update the server scripts (card at the top of Status) to see more services.", style = MaterialTheme.typography.bodySmall)
                }
                if (options.isEmpty() && st != null) Text("Everything here is already connected.")
                options.forEach { (name, hint, go) ->
                    OutlinedButton(
                        onClick = { guard.run("Connect $name") { go() } },
                        enabled = st != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(name, style = MaterialTheme.typography.titleSmall)
                            Text(hint, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { vm.showAddService(false) }) { Text("Close") } },
    )
}

/** Connect form for a token service: create link, what Claude needs, the fields. */
@Composable
fun TokenServiceDialog(vm: MainViewModel, def: ServiceDef) {
    val context = LocalContext.current
    val busy by vm.busy.collectAsState()
    val error by vm.tokenError.collectAsState()
    val values = remember(def.id) { mutableStateListOf(*Array(def.fields.size) { "" }) }
    var needs by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (busy == null) vm.showTokenService(null) },
        title = { Text("Connect ${def.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                OutlinedButton(onClick = { openUrl(context, def.createUrl) }, modifier = Modifier.fillMaxWidth()) { Text(def.createLabel) }
                TextButton(onClick = { needs = !needs }) { Text(if (needs) "Hide what Claude needs" else "What Claude needs (step by step)") }
                if (needs) SelectionContainer { Text(def.needs, style = MaterialTheme.typography.bodySmall) }
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
    val context = LocalContext.current
    val busy by vm.busy.collectAsState()
    val status by vm.status.collectAsState()
    val canInstall = (status?.scriptApi ?: 0) >= Updates.INSTALL_CLI_API
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
                    if (canInstall) {
                        Button(onClick = { vm.installGlab() }, enabled = busy == null, modifier = Modifier.fillMaxWidth()) {
                            Text("Install glab")
                        }
                    } else {
                        Text(
                            "Update the server scripts first (the card at the top of Status), then come back here.",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else if ((status?.scriptApi ?: 0) >= Updates.TOKEN_SERVICES_API) {
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
                } else {
                    Text("Docker needs root to install, so run this on the server yourself (it asks for your sudo password):")
                    SelectionContainer {
                        Text(DOCKER_INSTALL, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = {
                        copy(context, "Docker install", DOCKER_INSTALL)
                        vm.say("Copied. Paste it into a terminal on the server.")
                    }, modifier = Modifier.fillMaxWidth()) { Text("Copy command") }
                    Text(
                        "It uses Docker's official install script (get.docker.com) and adds you to the docker " +
                            "group so docker works without sudo. Then tap Check again.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(onClick = { vm.showSetup(null); vm.refreshStatus() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Check again")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { vm.showSetup(null) }) { Text("Close") } },
    )
}

private const val DOCKER_INSTALL = "curl -fsSL https://get.docker.com | sudo sh && sudo usermod -aG docker \"\$USER\""

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
        "Create a classic personal access token with these scopes: repo, read:org, gist, workflow.",
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

@Composable
fun TailDialog(vm: MainViewModel, tail: TailResult) {
    val updatedAt by vm.tailUpdatedAt.collectAsState()
    val refreshing by vm.tailRefreshing.collectAsState()
    var live by remember { mutableStateOf(true) }
    val vScroll = rememberScrollState()
    // Stay at the bottom as new output arrives.
    LaunchedEffect(tail.text) { vScroll.scrollTo(vScroll.maxValue) }
    // Live: re-read every 5 s while the window is open.
    LaunchedEffect(live) {
        while (live) {
            delay(5_000)
            vm.refreshTail(manual = false)
        }
    }
    AlertDialog(
        onDismissRequest = { vm.closeTail() },
        title = {
            Column {
                Text(tail.session)
                Text(
                    when {
                        refreshing -> "Refreshing…"
                        updatedAt != null ->
                            "Updated " + DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(updatedAt!!)) +
                                if (live) " · live" else ""
                        else -> ""
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
                SelectionContainer {
                    Text(
                        tail.text.ifBlank { "(no output yet)" },
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .heightIn(max = 480.dp)
                            .verticalScroll(vScroll)
                            .horizontalScroll(rememberScrollState()),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = live, onCheckedChange = { live = it })
                    Text("  Auto-refresh", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.refreshTail() }, enabled = !refreshing) { Text("Refresh") }
        },
        dismissButton = { TextButton(onClick = { vm.closeTail() }) { Text("Close") } },
    )
}
