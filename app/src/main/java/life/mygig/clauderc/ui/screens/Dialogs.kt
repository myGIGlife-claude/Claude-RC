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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import life.mygig.clauderc.api.TailResult
import life.mygig.clauderc.ui.LoginKind
import life.mygig.clauderc.ui.MainViewModel
import life.mygig.clauderc.ui.openUrl

private const val GITHUB_TOKEN_URL =
    "https://github.com/settings/tokens/new?scopes=repo,read:org,workflow&description=cLaudeRC%20server"

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
                    LoginKind.CLOUDFLARE -> "Connect Cloudflare"
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
                    LoginKind.CLOUDFLARE -> CloudflareLogin(vm, busy != null)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { vm.showLogin(null) }) { Text("Close") } },
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
        "Create a classic personal access token with these scopes: repo, read:org, workflow. " +
            "(admin:org or write:org also cover read:org.)",
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

/** Exactly which permissions each token needs, shown from the login dialog. */
private fun permissionsText(kind: LoginKind): String = when (kind) {
    LoginKind.GITHUB ->
        "Classic personal access token (Settings › Developer settings › Tokens (classic)).\n\n" +
            "Tick these scopes:\n" +
            "• repo: create, clone and push your repositories (public and private)\n" +
            "• read:org: list the organizations you can create repos in\n" +
            "  (admin:org or write:org also cover this)\n" +
            "• workflow: push changes to GitHub Actions files (.github/workflows)\n\n" +
            "Nothing else is needed. Pick an expiry you're comfortable with."
    LoginKind.AWS ->
        "Access keys for an IAM user (IAM › Users › your user › Security credentials › Create access key, " +
            "use case \"Command Line Interface\").\n\n" +
            "• Never use root account keys.\n" +
            "• The app itself only calls sts:GetCallerIdentity, which every user may call.\n" +
            "• Give the user only the policies for what Claude should manage, e.g. " +
            "AmazonS3FullAccess, AWSLambda_FullAccess, CloudWatchLogsReadOnlyAccess. " +
            "Avoid AdministratorAccess unless you really want Claude to change anything.\n\n" +
            "SSO instead: run 'aws configure sso --use-device-code' on the server once; the permission set " +
            "you pick there decides access."
    LoginKind.GITLAB ->
        "Personal access token (Preferences › Access tokens › Add new token).\n\n" +
            "Select these scopes:\n" +
            "• api: create projects, merge requests and issues, and read your user (glab needs it)\n" +
            "• write_repository: push over HTTPS\n\n" +
            "read_user and read_repository are included in the above. Set an expiry date " +
            "(GitLab requires one)."
    LoginKind.DOCKER ->
        "Docker Hub: Account settings › Personal access tokens › Generate new token.\n" +
            "• Access permissions: Read & Write (to push images), or Read-only if you only pull.\n" +
            "• Username: your Docker Hub username (not your email).\n\n" +
            "GHCR (ghcr.io): a GitHub classic personal access token with:\n" +
            "• write:packages: push images (includes read:packages)\n" +
            "• delete:packages: only if Claude should delete images\n" +
            "• Username: your GitHub username."
    LoginKind.CLOUDFLARE ->
        "My Profile › API Tokens › Create Token. The \"Edit Cloudflare Workers\" template is the easy choice. " +
            "To build it yourself, add:\n\n" +
            "Account permissions:\n" +
            "• Workers Scripts: Edit (deploy Workers)\n" +
            "• Account Settings: Read (wrangler reads your account)\n" +
            "• Optional: Workers KV Storage, Workers R2 Storage, D1, Cloudflare Pages: Edit, if Claude uses them\n\n" +
            "User permissions:\n" +
            "• User Details: Read\n" +
            "• Memberships: Read (wrangler lists your accounts)\n\n" +
            "Zone permissions (only for custom domains/routes):\n" +
            "• Workers Routes: Edit\n\n" +
            "Account Resources: include your account. Zone Resources: only the zones Claude should touch."
    LoginKind.CLAUDE -> ""
}

@Composable
private fun PermissionsLink(kind: LoginKind) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text("What permissions does it need?") }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Permissions needed") },
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
    Text("Create a personal access token with the api and write_repository scopes.")
    OutlinedButton(onClick = { openUrl(context, "https://$host/-/user_settings/personal_access_tokens") }, modifier = Modifier.fillMaxWidth()) {
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
private fun CloudflareLogin(vm: MainViewModel, busy: Boolean) {
    val context = LocalContext.current
    var token by remember { mutableStateOf("") }
    Text(
        "Create an API token (the \"Edit Cloudflare Workers\" template suits wrangler). It's saved in a " +
            "private file on the server that Claude's sessions load; restart running sessions to pick it up.",
    )
    OutlinedButton(onClick = { openUrl(context, "https://dash.cloudflare.com/profile/api-tokens") }, modifier = Modifier.fillMaxWidth()) {
        Text("Create token on Cloudflare")
    }
    PermissionsLink(LoginKind.CLOUDFLARE)
    SecretField(token, "API token") { token = it }
    Button(onClick = { vm.cloudflareLogin(token) }, enabled = !busy && token.length >= 30, modifier = Modifier.fillMaxWidth()) {
        Text("Connect")
    }
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
