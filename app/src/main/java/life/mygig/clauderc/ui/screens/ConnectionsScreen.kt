package life.mygig.clauderc.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import life.mygig.clauderc.api.Catalog
import life.mygig.clauderc.api.McpServer
import life.mygig.clauderc.api.Updates
import life.mygig.clauderc.ui.Detail
import life.mygig.clauderc.ui.LoginKind
import life.mygig.clauderc.ui.MainViewModel
import life.mygig.clauderc.ui.components.Health
import life.mygig.clauderc.ui.components.InfoTile
import life.mygig.clauderc.ui.components.PillTabs
import life.mygig.clauderc.ui.components.SectionLabel
import life.mygig.clauderc.ui.theme.WarnAmber

private data class Tile(val title: String, val subtitle: String?, val health: Health, val onClick: () -> Unit)

fun mcpHealth(m: McpServer) = when (m.health) {
    "connected" -> Health.OK
    "failed" -> Health.BAD
    "needs_auth" -> Health.WARN
    else -> Health.OFF
}

fun mcpSubtitle(m: McpServer) = when (m.health) {
    "failed" -> "failed to connect"
    "needs_auth" -> "needs sign-in"
    else -> when (m.scope) {
        "claude.ai" -> "claude.ai connector"
        "plugin" -> "from plugin ${m.plugin ?: ""}".trim()
        "user" -> "user · all projects"
        else -> "project"
    }
}

/** Connections: everything Claude on this server is connected to, as tiles in groups. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsScreen(vm: MainViewModel) {
    val status by vm.status.collectAsStateWithLifecycle()
    val refreshing by vm.statusRefreshing.collectAsStateWithLifecycle()
    val latest by vm.latest.collectAsStateWithLifecycle()
    val mcp by vm.connections.mcp.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val login = { kind: LoginKind -> vm.logins.showLogin(kind) }
    val detail = { d: Detail -> vm.connections.openDetail(d) }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { vm.refreshAll() }, modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val l = latest
            val s = status
            if (s == null) {
                full { Text(if (refreshing) "Checking the server…" else "Pull down to check the server.", style = MaterialTheme.typography.bodyLarge) }
                return@LazyVerticalGrid
            }
            val required = s.scriptApi < Updates.MIN_SCRIPT_API
            val newCommit = l?.serverCommit?.takeIf { it != s.commit }
            if (required || newCommit != null) {
                full { ServerUpdateCard(vm, required, newCommit) }
            }

            full { PillTabs(listOf("Logins", "MCP", "Keys"), tab, { tab = it }) }

            if (tab == 0) section("Core", listOf(
                Tile("Claude", if (s.claude.loggedIn) "claude.ai subscription" else "Not logged in: tap to log in",
                    if (s.claude.loggedIn) Health.OK else Health.BAD) {
                    if (s.claude.loggedIn) detail(Detail.Login(LoginKind.CLAUDE)) else login(LoginKind.CLAUDE)
                },
                Tile("GitHub", when {
                    !s.github.loggedIn -> "Not logged in: tap to log in"
                    s.github.missingScopes.isNotEmpty() -> "missing ${s.github.missingScopes.joinToString(", ")}"
                    else -> s.github.user
                }, when {
                    !s.github.loggedIn -> Health.BAD
                    s.github.missingScopes.isNotEmpty() -> Health.WARN
                    else -> Health.OK
                }) {
                    if (s.github.loggedIn) detail(Detail.Login(LoginKind.GITHUB)) else login(LoginKind.GITHUB)
                },
            ))

            val sv = s.services.orEmpty()
            val services = buildList {
                if (s.aws.loggedIn) add(Tile("AWS", "profile ${s.aws.profile}", Health.OK) { detail(Detail.Login(LoginKind.AWS)) })
                sv["docker"]?.takeIf { it.loggedIn }?.let { add(Tile("Docker / GHCR", it.detail, Health.OK) { detail(Detail.Login(LoginKind.DOCKER)) }) }
                sv["gitlab"]?.takeIf { it.loggedIn }?.let { add(Tile("GitLab", it.detail, Health.OK) { detail(Detail.Login(LoginKind.GITLAB)) }) }
                sv["youtube"]?.takeIf { it.loggedIn }?.let { add(Tile("YouTube", it.detail, Health.OK) { detail(Detail.Login(LoginKind.YOUTUBE)) }) }
                Catalog.services.forEach { def ->
                    sv[def.id]?.takeIf { it.loggedIn }?.let { add(Tile(def.name, it.detail, Health.OK) { detail(Detail.Service(def.id)) }) }
                }
            }.sortedBy { it.title.lowercase() }
            if (tab == 0) section("Services", services)

            val servers = mcp?.servers.orEmpty()
            if (tab == 1) section("MCP servers", servers.sortedWith(compareBy({ it.health != "failed" && it.health != "needs_auth" }, { it.label.lowercase() }))
                .map { m -> Tile(m.label, mcpSubtitle(m), mcpHealth(m)) { detail(Detail.Mcp(m)) } })

            val keys = buildList {
                if (s.apple) add(Tile("Apple developer", "App Store Connect key", Health.OK) { detail(Detail.Apple) })
                s.keystores.forEach { n -> add(Tile("Signing: $n", "Android upload key", Health.OK) { detail(Detail.Keystore(n)) }) }
                if (s.custom.isNotEmpty()) {
                    add(Tile("Custom keys", "${s.custom.size} key" + if (s.custom.size == 1) "" else "s", Health.OK) { vm.services.showCustomKeys(true) })
                }
            }
            if (tab == 2) section("Keys", keys)

            if ((tab == 1 && servers.isEmpty()) || (tab == 2 && keys.isEmpty())) {
                full { Text("Nothing here yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (tab == 0 && services.isEmpty()) {
                full {
                    Text(
                        "Tap + at the top to connect AWS, GitLab, Docker, Cloudflare, Vercel, Supabase, YouTube and more.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun LazyGridScope.full(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

private fun LazyGridScope.section(title: String, tiles: List<Tile>) {
    if (tiles.isEmpty()) return
    full { SectionLabel(title) }
    tiles.forEach { t -> item { InfoTile(t.title, t.subtitle, t.health, t.onClick) } }
}

/** Newer server scripts on GitHub: update from here ([commit] non-null) or copy the command. */
@Composable
private fun ServerUpdateCard(vm: MainViewModel, required: Boolean, commit: String?) {
    val context = LocalContext.current
    val busy by vm.busy.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (required) Color(0xFF2A1718) else Color(0xFF2A2418),
        border = BorderStroke(1.dp, if (required) Color(0xFF6A2A2D) else Color(0xFF5C4A24)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (required) "Server scripts need an update" else "Server scripts update available",
                color = if (required) Color(0xFFF09A9D) else WarnAmber,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (required) "Parts of the app won't work until the server is updated." else "A newer version is on GitHub.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (commit != null) Button(onClick = { confirm = true }, enabled = busy == null) { Text("Update now") }
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
            text = { Text("The server downloads install.sh from GitHub at commit ${commit.take(7)} and runs it. Running sessions keep going.") },
            confirmButton = {
                TextButton(onClick = { confirm = false; vm.updateServerScripts(commit) }) { Text("Update") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}
