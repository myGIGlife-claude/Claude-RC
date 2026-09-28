package life.mygig.clauderc.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import life.mygig.clauderc.BuildConfig
import life.mygig.clauderc.api.Catalog
import life.mygig.clauderc.api.Updates
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import life.mygig.clauderc.ui.LocalGuard
import life.mygig.clauderc.ui.LoginKind
import life.mygig.clauderc.ui.MainViewModel
import life.mygig.clauderc.ui.theme.BadRed
import life.mygig.clauderc.ui.theme.OkGreen

private val Amber = Color(0xFFE0A030)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(vm: MainViewModel) {
    val status by vm.status.collectAsState()
    val refreshing by vm.statusRefreshing.collectAsState()
    val guard = LocalGuard.current
    val latest by vm.latest.collectAsState()
    val login = { kind: LoginKind -> guard.run("Log in on the server") { vm.showLogin(kind) } }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { vm.refreshStatus() }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Spacer(Modifier.padding(top = 4.dp)) }
            val l = latest
            val newApp = l?.appVersionCode?.takeIf { it > BuildConfig.VERSION_CODE }
            val apkUrl = l?.apkUrl
            if (newApp != null && apkUrl != null) {
                item {
                    StatusRow(
                        title = "App update available",
                        state = RowState.WARN,
                        detail = "Build ${newApp - 100} is ready (you have ${BuildConfig.VERSION_CODE - 100}). Tap to download.",
                        onClick = { vm.downloadAndInstall(apkUrl) },
                    )
                }
            }
            val s = status
            if (s == null) {
                item {
                    Text(
                        if (refreshing) "Checking the server…" else "Pull down to check the server.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                val required = s.scriptApi < Updates.MIN_SCRIPT_API
                val newCommit = l?.serverCommit?.takeIf { it != s.commit }
                if (required || newCommit != null) {
                    item { ServerUpdateCard(vm, required, newCommit.takeIf { s.scriptApi >= Updates.SELF_UPDATE_API }) }
                }
                item {
                    StatusRow(
                        title = "Claude",
                        state = if (s.claude.loggedIn) RowState.OK else RowState.BAD,
                        detail = if (s.claude.loggedIn) "Logged in (claude.ai subscription)" else "Not logged in — tap to log in",
                        onClick = if (s.claude.loggedIn) null else { { login(LoginKind.CLAUDE) } },
                    )
                }
                item {
                    val gh = s.github
                    val state = when {
                        !gh.loggedIn -> RowState.BAD
                        gh.missingScopes.isNotEmpty() -> RowState.WARN
                        else -> RowState.OK
                    }
                    StatusRow(
                        title = "GitHub",
                        state = state,
                        detail = when (state) {
                            RowState.BAD -> "Not logged in — tap to log in"
                            RowState.WARN -> "${gh.user}: missing ${gh.missingScopes.joinToString(", ")} — tap to fix"
                            RowState.OK -> "Logged in as ${gh.user}"
                        },
                        onClick = if (state == RowState.OK) null else { { login(LoginKind.GITHUB) } },
                    )
                }
                if (s.aws.loggedIn) {
                    item {
                        StatusRow(
                            title = "AWS",
                            state = RowState.OK,
                            detail = (s.aws.identity?.arn ?: "Logged in") + " (profile ${s.aws.profile})",
                            onClick = { login(LoginKind.AWS) },
                        )
                    }
                }
                // Only what's connected; the + button at the top adds more.
                val sv = s.services.orEmpty()
                // name, detail, tap
                val connected = buildList<Triple<String, String?, () -> Unit>> {
                    sv["gitlab"]?.takeIf { it.loggedIn }?.let { add(Triple("GitLab", it.detail) { login(LoginKind.GITLAB) }) }
                    sv["docker"]?.takeIf { it.loggedIn }?.let { add(Triple("Docker / GHCR", it.detail) { login(LoginKind.DOCKER) }) }
                    Catalog.services.forEach { def ->
                        sv[def.id]?.takeIf { it.loggedIn }?.let { add(Triple(def.name, it.detail) { vm.showTokenService(def.id) }) }
                    }
                }
                items(connected.size) { i ->
                    val (title, detail, tap) = connected[i]
                    StatusRow(
                        title = title,
                        state = RowState.OK,
                        detail = detail?.let { "Connected: $it" } ?: "Connected",
                        // Re-connecting is allowed, e.g. a new token or a second registry.
                        onClick = tap,
                    )
                }
                if (s.custom.isNotEmpty()) {
                    item {
                        StatusRow(
                            title = "Custom API keys",
                            state = RowState.OK,
                            detail = s.custom.joinToString(", "),
                            onClick = { vm.showCustomKeys(true) },
                        )
                    }
                }
                if (connected.isEmpty() && !s.aws.loggedIn && s.custom.isEmpty()) {
                    item {
                        Text(
                            "Tap + at the top to connect AWS, GitLab, Docker, Cloudflare, Vercel, Supabase and more.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

private enum class RowState { OK, WARN, BAD }

/** Newer server scripts on GitHub: update from here ([commit] non-null) or copy the command. */
@Composable
private fun ServerUpdateCard(vm: MainViewModel, required: Boolean, commit: String?) {
    val context = LocalContext.current
    val guard = LocalGuard.current
    val busy by vm.busy.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (required) Icons.Filled.Error else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (required) BadRed else Amber,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        if (required) "Server scripts need an update" else "Server scripts update available",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (required) "Parts of the app won't work until the server is updated." else "A newer version is on GitHub.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (commit != null) {
                    Button(onClick = { confirm = true }, enabled = busy == null) { Text("Update now") }
                }
                OutlinedButton(onClick = {
                    copy(context, "Update command", Updates.installCommand())
                    vm.say("Copied. Paste it into a terminal on the server.")
                }) { Text("Copy command") }
            }
        }
    }
    if (confirm && commit != null) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Update the server scripts?") },
            text = {
                Text(
                    "The server downloads install.sh from GitHub at commit ${commit.take(7)} and runs it. " +
                        "Running sessions keep going.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    guard.run("Update the server scripts") { vm.updateServerScripts(commit) }
                }) { Text("Update") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StatusRow(title: String, state: RowState, detail: String, onClick: (() -> Unit)?) {
    val (icon, tint) = when (state) {
        RowState.OK -> Icons.Filled.CheckCircle to OkGreen
        RowState.WARN -> Icons.Filled.Warning to Amber
        RowState.BAD -> Icons.Filled.Error to BadRed
    }
    Card(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it },
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = state.name, tint = tint, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
