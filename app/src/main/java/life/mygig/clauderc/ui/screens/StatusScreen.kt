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
import java.text.DateFormat
import java.util.Date
import life.mygig.clauderc.api.Health
import life.mygig.clauderc.ui.LocalGuard
import life.mygig.clauderc.ui.formatUptime
import life.mygig.clauderc.ui.LoginKind
import life.mygig.clauderc.ui.MainViewModel
import life.mygig.clauderc.ui.theme.BadRed
import life.mygig.clauderc.ui.theme.OkGreen

private val Amber = Color(0xFFE0A030)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(vm: MainViewModel) {
    val status by vm.status.collectAsState()
    val checkedAt by vm.statusCheckedAt.collectAsState()
    val refreshing by vm.statusRefreshing.collectAsState()
    val guard = LocalGuard.current
    val login = { kind: LoginKind -> guard.run("Log in on the server") { vm.showLogin(kind) } }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { vm.refreshStatus() }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Spacer(Modifier.padding(top = 4.dp)) }
            val s = status
            if (s == null) {
                item {
                    Text(
                        if (refreshing) "Checking the server…" else "Pull down to check the server.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
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
                item {
                    val aws = s.aws
                    StatusRow(
                        title = "AWS",
                        state = if (aws.loggedIn) RowState.OK else RowState.BAD,
                        detail = if (aws.loggedIn) {
                            (aws.identity?.arn ?: "Logged in") + " (profile ${aws.profile})"
                        } else {
                            "Not logged in — tap to log in"
                        },
                        onClick = if (aws.loggedIn) null else { { login(LoginKind.AWS) } },
                    )
                }
                val svcs = s.services
                if (svcs == null) {
                    item {
                        StatusRow(
                            title = "More services",
                            state = RowState.WARN,
                            detail = "The server's scripts are out of date. Rerun the install command on the server " +
                                "to connect GitLab, Docker/GHCR and Cloudflare.",
                            onClick = null,
                        )
                    }
                } else {
                    val services = listOf(
                        Triple("GitLab", svcs.gitlab, LoginKind.GITLAB),
                        Triple("Docker / GHCR", svcs.docker, LoginKind.DOCKER),
                        Triple("Cloudflare", svcs.cloudflare, LoginKind.CLOUDFLARE),
                    )
                    items(services.size) { i ->
                        val (title, svc, kind) = services[i]
                        StatusRow(
                            title = title,
                            state = when {
                                svc.loggedIn -> RowState.OK
                                !svc.installed -> RowState.WARN
                                else -> RowState.BAD
                            },
                            detail = when {
                                svc.loggedIn -> svc.detail?.let { "Connected: $it" } ?: "Connected"
                                !svc.installed -> "CLI not installed on the server"
                                else -> "Not connected — tap to connect"
                            },
                            // Re-connecting is allowed too, e.g. to add a second registry.
                            onClick = if (svc.installed) { { login(kind) } } else null,
                        )
                    }
                }
                s.health?.let { h -> item { HealthCard(h) } }
                item {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text("Server: ${s.hostname}", style = MaterialTheme.typography.bodyMedium)
                        Text("Script version: ${s.version}", style = MaterialTheme.typography.bodySmall)
                        checkedAt?.let {
                            Text(
                                "Last checked: " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class RowState { OK, WARN, BAD }

@Composable
private fun HealthCard(h: Health) {
    fun gb(kb: Long) = "%.1f GB".format(kb / 1_048_576.0)
    fun pct(used: Long, total: Long) = if (total > 0) (used * 100 / total).toInt() else 0
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Server health", style = MaterialTheme.typography.titleMedium)
            Text("Disk: ${gb(h.diskUsedKb)} of ${gb(h.diskTotalKb)} (${pct(h.diskUsedKb, h.diskTotalKb)}%)")
            Text("Memory: ${gb(h.memUsedKb)} of ${gb(h.memTotalKb)} (${pct(h.memUsedKb, h.memTotalKb)}%)")
            Text("Load: ${h.load.joinToString(" ") { "%.2f".format(it) }} on ${h.cpus} CPU" + if (h.cpus == 1) "" else "s")
            Text("Up: ${formatUptime(h.uptimeSeconds)}")
            val cur = h.claudeVersion
            val latest = h.claudeLatest
            Text(
                when {
                    cur == null -> "Claude Code: not found"
                    latest != null && latest != cur -> "Claude Code $cur (update available: $latest)"
                    else -> "Claude Code $cur"
                },
                color = if (cur != null && latest != null && latest != cur) Amber else MaterialTheme.colorScheme.onSurface,
            )
        }
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
