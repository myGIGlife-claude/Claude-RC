package life.mygig.clauderc.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import life.mygig.clauderc.api.Catalog
import life.mygig.clauderc.ui.Detail
import life.mygig.clauderc.ui.LoginKind
import life.mygig.clauderc.ui.MainViewModel
import life.mygig.clauderc.ui.Tab
import life.mygig.clauderc.ui.components.CardBox
import life.mygig.clauderc.ui.components.Health
import life.mygig.clauderc.ui.components.InfoRows
import life.mygig.clauderc.ui.components.OneLine
import life.mygig.clauderc.ui.components.SectionLabel
import life.mygig.clauderc.ui.components.StatusDot
import life.mygig.clauderc.ui.components.color
import life.mygig.clauderc.ui.openUrl

/** What a detail page shows and can do. */
private class Page(
    val title: String,
    val health: Health,
    val statusLine: String,
    val about: String? = null,
    val rows: List<Pair<String, String>> = emptyList(),
    val vars: List<String> = emptyList(),
    val note: String? = null,
    val edit: Pair<String, () -> Unit>? = null,
    val test: Pair<String, () -> Unit>? = null,
    val remove: Pair<String, () -> Unit>? = null,
    val removeConfirm: String? = null,
    val link: Pair<String, String>? = null,
)

/** A Connections tile, opened: status, public info, the variable names sessions get, and actions. */
@Composable
fun DetailScreen(vm: MainViewModel, d: Detail) {
    val status by vm.status.collectAsState()
    val mcp by vm.mcp.collectAsState()
    val busy by vm.busy.collectAsState()
    val context = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    BackHandler { vm.openDetail(null) }
    val s = status ?: return
    val sv = s.services.orEmpty()
    val login = { k: LoginKind -> { vm.showLogin(k) } }

    val page: Page = when (d) {
        is Detail.Service -> {
            val def = Catalog.byId(d.id)
            val svc = sv[d.id]
            Page(
                title = def?.name ?: d.id,
                health = if (svc?.loggedIn == true) Health.OK else Health.OFF,
                statusLine = if (svc?.loggedIn == true) "Connected" + (svc.detail?.let { " · $it" } ?: "") else "Not connected",
                about = def?.hint,
                rows = listOfNotNull(
                    "Credentials" to "saved on the server (never shown)",
                    def?.install?.let { "Tool" to (if (svc?.installed == true) "$it installed" else "$it not installed") },
                ),
                vars = Catalog.envVars[d.id].orEmpty(),
                edit = "Edit credentials" to { vm.openDetail(null); vm.showTokenService(d.id) },
                test = "Test now" to { vm.refreshAll() },
                remove = "Disconnect ${def?.name ?: d.id}" to { vm.disconnect(d.id, def?.name ?: d.id) },
                removeConfirm = "Its credentials are deleted from the server. Sessions lose them after their next restart.",
            )
        }
        is Detail.Login -> when (d.kind) {
            LoginKind.CLAUDE -> Page("Claude", if (s.claude.loggedIn) Health.OK else Health.BAD,
                if (s.claude.loggedIn) "Logged in with your claude.ai subscription" else "Not logged in",
                about = "Claude Code on the server. Sessions run with Remote Control, never an API key.",
                edit = "Log in again" to login(LoginKind.CLAUDE))
            LoginKind.GITHUB -> Page("GitHub", if (s.github.loggedIn) Health.OK else Health.BAD,
                if (s.github.loggedIn) "Logged in as ${s.github.user}" else "Not logged in",
                about = "Used to create, clone and push repositories (gh CLI).",
                rows = listOf("Missing scopes" to s.github.missingScopes.ifEmpty { listOf("none") }.joinToString(", ")),
                edit = "Replace token" to login(LoginKind.GITHUB))
            LoginKind.AWS -> Page("AWS", if (s.aws.loggedIn) Health.OK else Health.BAD,
                if (s.aws.loggedIn) "Connected" else "Not connected",
                about = "The AWS CLI on the server.",
                rows = listOfNotNull("Profile" to s.aws.profile, s.aws.identity?.arn?.let { "Identity" to it }, s.aws.identity?.account?.let { "Account" to it }),
                edit = "Log in again" to login(LoginKind.AWS))
            LoginKind.GITLAB -> Page("GitLab", Health.OK, "Logged in as ${sv["gitlab"]?.detail ?: "?"}",
                about = "The glab CLI on the server.", edit = "Replace token" to login(LoginKind.GITLAB))
            LoginKind.DOCKER -> Page("Docker / GHCR", Health.OK, "Logged in to ${sv["docker"]?.detail ?: "?"}",
                about = "docker login on the server: push and pull images.", edit = "Log in to a registry" to login(LoginKind.DOCKER))
            LoginKind.YOUTUBE -> Page("YouTube", Health.OK, "Connected to ${sv["youtube"]?.detail ?: "your channel"}",
                about = "Claude uploads with: youtube-upload video.mp4 --title \"…\" --privacy unlisted",
                vars = Catalog.envVars["youtube"].orEmpty(),
                note = "Videos from unaudited Google projects stay private until audited. 100 uploads a day.",
                edit = "Sign in again" to login(LoginKind.YOUTUBE),
                remove = "Disconnect YouTube" to { vm.disconnect("youtube", "YouTube") },
                removeConfirm = "The YouTube login is deleted from the server.")
        }
        is Detail.Mcp -> {
            val m = mcp?.servers?.firstOrNull { it.name == d.server.name } ?: d.server
            Page(
                title = m.label,
                health = mcpHealth(m),
                statusLine = when (m.health) {
                    "connected" -> "Connected"
                    "failed" -> "Failed to connect"
                    "needs_auth" -> "Needs sign-in"
                    else -> "Not checked yet"
                } + (mcp?.checkedSecondsAgo?.let { " · checked ${ago(it)}" } ?: ""),
                about = when (m.scope) {
                    "claude.ai" -> "A connector from your claude.ai account. Every session gets it."
                    "plugin" -> "Comes with the ${m.plugin} plugin (Claude tab)."
                    "user" -> "An MCP server for every project on this server."
                    else -> "An MCP server for one project."
                },
                rows = listOf("Scope" to m.scope, "Type" to (if (m.kind == "http") "remote (HTTP)" else "local program"), (if (m.kind == "http") "URL" else "Runs") to m.target) +
                    (if (m.health != "connected" && m.detail.isNotBlank()) listOf("Last error" to m.detail) else emptyList()),
                note = when {
                    m.health == "needs_auth" -> "Sign in from a Claude session: run /mcp there and pick ${m.label}."
                    m.scope == "claude.ai" -> "Added and removed in claude.ai › Settings › Connectors."
                    else -> null
                },
                test = "Test now" to { vm.refreshMcp(check = true) },
                remove = if (m.scope == "user") ("Remove ${m.label}" to { vm.removeMcp(m.name) }) else null,
                removeConfirm = "claude mcp remove ${m.name} -s user. Sessions lose it after their next restart.",
                link = when (m.scope) {
                    "claude.ai" -> "Open claude.ai connectors" to "https://claude.ai/settings/connectors"
                    else -> null
                },
                edit = if (m.scope == "plugin") ("Open the Claude tab" to { vm.openDetail(null); vm.selectTab(Tab.COMMAND) }) else null,
            )
        }
        is Detail.Keystore -> Page(
            title = "Signing: ${d.name}", health = Health.OK, statusLine = "Saved and checked",
            about = "An Android upload key for signing app bundles.",
            vars = listOf("${d.name}_KEYSTORE_FILE", "${d.name}_KEYSTORE_PASSWORD", "${d.name}_KEY_ALIAS", "${d.name}_KEY_PASSWORD"),
            note = "Keep your own backup: Google Play only accepts bundles signed with this key.",
            edit = "Replace or remove" to { vm.openDetail(null); vm.showKeystores(true) },
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(page.title, style = MaterialTheme.typography.headlineSmall)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = when (page.health) { Health.OK -> Color(0xFF16261E); Health.BAD -> Color(0xFF2A1718); Health.WARN -> Color(0xFF2A2418); else -> MaterialTheme.colorScheme.surfaceContainerHigh },
            border = BorderStroke(1.dp, page.health.color().copy(alpha = 0.45f)),
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusDot(page.health)
                Text(page.statusLine, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        }
        if (page.about != null || page.rows.isNotEmpty()) {
            CardBox {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("About")
                    page.about?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    if (page.rows.isNotEmpty()) InfoRows(page.rows)
                }
            }
        }
        if (page.vars.isNotEmpty()) {
            CardBox {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("What sessions get")
                    Text(page.vars.joinToString("\n"), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    Text("Values never leave the server.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        page.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            page.edit?.let { (label, go) -> Button(onClick = go, enabled = busy == null, modifier = Modifier.weight(1f).height(48.dp)) { OneLine(label) } }
            page.test?.let { (label, go) -> OutlinedButton(onClick = go, enabled = busy == null, modifier = Modifier.weight(1f).height(48.dp)) { OneLine(label) } }
        }
        page.link?.let { (label, url) -> OutlinedButton(onClick = { openUrl(context, url) }, modifier = Modifier.fillMaxWidth()) { Text(label) } }
        page.remove?.let { (label, _) ->
            TextButton(onClick = { confirm = true }, enabled = busy == null, modifier = Modifier.fillMaxWidth()) {
                Text(label, color = Color(0xFFF09A9D))
            }
        }
        Text("Changes reach running chats after Sessions › Restart.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (confirm) {
        val (label, go) = page.remove ?: return
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("$label?") },
            text = { Text(page.removeConfirm ?: "") },
            confirmButton = { TextButton(onClick = { confirm = false; go() }) { Text("Yes") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

private fun ago(seconds: Long) = when {
    seconds < 60 -> "just now"
    seconds < 3600 -> "${seconds / 60} min ago"
    else -> "${seconds / 3600} h ago"
}
