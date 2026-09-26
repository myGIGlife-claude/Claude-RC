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
