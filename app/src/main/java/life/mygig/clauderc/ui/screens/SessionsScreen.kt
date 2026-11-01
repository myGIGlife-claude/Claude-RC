package life.mygig.clauderc.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import life.mygig.clauderc.api.Session
import life.mygig.clauderc.ui.LocalAppLock
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import life.mygig.clauderc.ui.MainViewModel
import life.mygig.clauderc.ui.components.CalmDivider
import life.mygig.clauderc.ui.components.CalmGroup
import life.mygig.clauderc.ui.components.OneLine
import life.mygig.clauderc.ui.components.PillTabs
import life.mygig.clauderc.ui.components.SectionLabel
import life.mygig.clauderc.ui.components.StatusDot
import life.mygig.clauderc.ui.components.claudeHealth
import life.mygig.clauderc.ui.components.isNarrow
import life.mygig.clauderc.ui.components.sessionState
import life.mygig.clauderc.ui.components.stateLine
import life.mygig.clauderc.ui.components.stateSpoken
import life.mygig.clauderc.ui.formatUptime
import life.mygig.clauderc.ui.openInClaude
import life.mygig.clauderc.ui.theme.Term
import life.mygig.clauderc.ui.theme.WarnAmber
import life.mygig.clauderc.ui.theme.statusColors

/** Two tabs: General chat (conversations not tied to a repo) and Code chat (the project sessions). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(vm: MainViewModel) {
    // Saved for process death; the controller keeps it while you visit other tabs.
    var tab by rememberSaveable { mutableIntStateOf(vm.general.tab.value) }
    // A restored tab goes to the controller first; after that a widget tap can switch it while this shows.
    LaunchedEffect(Unit) {
        vm.general.selectTab(tab)
        vm.general.tab.collect { tab = it }
    }
    Column(Modifier.fillMaxSize()) {
        Text("Sessions", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 20.dp, top = 12.dp))
        PillTabs(
            listOf("General chat", "Code chat"), tab, { tab = it; vm.general.selectTab(it) },
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (tab == 0) GeneralChatTab(vm) else CodeSessions(vm)
        }
    }
}

/** The project sessions (general chats are in their own tab). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CodeSessions(vm: MainViewModel) {
    val allSessions by vm.sessions.collectAsStateWithLifecycle()
    val sessions = remember(allSessions) { allSessions.filterNot { it.general } }
    val pushReady by vm.push.pushReady.collectAsStateWithLifecycle()
    val refreshing by vm.sessionsRefreshing.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmStop by remember { mutableStateOf<Session?>(null) }
    var confirmRestart by remember { mutableStateOf<Session?>(null) }
    var confirmRestartAll by remember { mutableStateOf(false) }
    val busyRestart by vm.busyRestart.collectAsStateWithLifecycle()
    val narrow = isNarrow()
    val appLock = LocalAppLock.current
    val scope = rememberCoroutineScope()

    // Previews and "needs an answer" stay current while the tab is open.
    val foreground by vm.foreground.collectAsStateWithLifecycle()
    LaunchedEffect(foreground) {
        while (foreground) {
            vm.refreshSessions()
            delay(15_000)
        }
    }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { vm.refreshSessions() }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (sessions.size > 1) QuietChip("Restart all") { confirmRestartAll = true }
                    // A shell on the server: only offered when App lock guards the app.
                    if (settings?.appLock == true) {
                        QuietChip(if (narrow) ">_ Run" else ">_ Run a command") { scope.launch { if (appLock?.unlock("Run a command on the server") != false) vm.showRun(true) } }
                    }
                }
            }
            if (sessions.isEmpty()) item { Text(if (refreshing) "Loading…" else "No Claude sessions running.") }
            val needs = sessions.filter { it.waiting }
            val working = sessions.filter { !it.waiting && it.busy }
            val idle = sessions.filter { !it.waiting && !it.busy }
            @Composable
            fun rowFor(s: Session) = SessionRow(
                s,
                onOpen = { if (s.waiting) vm.loadTail(s.name) else vm.chats.openChat(s.name) },
                actions = SessionActions(
                    openInClaude = { openInClaude(context) },
                    terminal = { vm.loadTail(s.name) },
                    restart = { confirmRestart = s },
                    stop = { confirmStop = s },
                    toggleDoneAlerts = if (pushReady) { { vm.push.setSessionPush(s.name, !s.pushDone) } } else null,
                ),
            )
            if (sessions.isNotEmpty()) item { SessionSummary(needs.size, working.size, idle.size) }
            if (needs.isNotEmpty()) {
                item { SectionLabel("Needs you") }
                items(needs, key = { it.name }) { s -> rowFor(s) }
            }
            if (working.isNotEmpty()) {
                item { SectionLabel("Working") }
                item { CalmGroup { working.forEachIndexed { i, s -> if (i > 0) CalmDivider(); rowFor(s) } } }
            }
            if (idle.isNotEmpty()) {
                item { SectionLabel("Idle") }
                item { CalmGroup { idle.forEachIndexed { i, s -> if (i > 0) CalmDivider(); rowFor(s) } } }
            }
        }
    }

    if (confirmRestartAll) {
        AlertDialog(
            onDismissRequest = { confirmRestartAll = false },
            title = { Text("Restart all sessions?") },
            text = { Text("Each session restarts in its folder and reopens the same conversation, so new plugins, skills, MCP servers and tokens are picked up. Sessions where Claude is still working are skipped.") },
            confirmButton = { TextButton(onClick = { confirmRestartAll = false; vm.restartAll() }) { Text("Restart all") } },
            dismissButton = { TextButton(onClick = { confirmRestartAll = false }) { Text("Cancel") } },
        )
    }
    busyRestart?.let { name ->
        AlertDialog(
            onDismissRequest = { vm.dismissBusyRestart() },
            title = { Text("Claude is still working") },
            text = { Text("Restarting now interrupts what it's doing in $name. The conversation comes back, but the current step stops.") },
            confirmButton = { TextButton(onClick = { vm.restartSession(name, force = true) }) { Text("Restart anyway") } },
            dismissButton = { TextButton(onClick = { vm.dismissBusyRestart() }) { Text("Wait") } },
        )
    }
    confirmRestart?.let { s ->
        AlertDialog(
            onDismissRequest = { confirmRestart = null },
            title = { Text("Restart ${s.project}?") },
            text = { Text("Stops this Claude session and starts it again in the same folder, so it picks up new plugins, skills, MCP servers and tokens. It reopens the same conversation, so nothing is lost. If Claude is still working, you'll be asked first.") },
            confirmButton = { TextButton(onClick = { confirmRestart = null; vm.restartSession(s.name) }) { Text("Restart") } },
            dismissButton = { TextButton(onClick = { confirmRestart = null }) { Text("Cancel") } },
        )
    }
    confirmStop?.let { s ->
        AlertDialog(
            onDismissRequest = { confirmStop = null },
            title = { Text("Stop ${s.project}?") },
            text = { Text("This ends the Claude Remote Control session on the server.") },
            confirmButton = { TextButton(onClick = { confirmStop = null; vm.stopProject(s.name) }) { Text("Stop") } },
            dismissButton = { TextButton(onClick = { confirmStop = null }) { Text("Cancel") } },
        )
    }
}

/** What the overflow menu of a session offers. */
private class SessionActions(
    val openInClaude: () -> Unit,
    val terminal: () -> Unit,
    val restart: () -> Unit,
    val stop: () -> Unit,
    val toggleDoneAlerts: (() -> Unit)?,
)

/** A small tonal pill action, like the quick actions in the design. */
@Composable
private fun QuietChip(label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(label, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp), maxLines = 1)
    }
}

/** The session's last meaningful line, without prompt symbols: what it said or is waiting for. */
internal fun previewLine(preview: String): String =
    preview.lineSequence().map { it.trim() }.filterNot { isStatusLine(it) }
        .map { it.trimStart { c -> !c.isLetterOrDigit() } }.lastOrNull { it.isNotBlank() }.orEmpty()

/** Lines that are the session's own status display, not what it said: the cost line ("full $42.21 last $0.80 …") and the token spinner. */
private val STATUS_LINE = Regex("""(^|\s)(full|last)\s+\$\d|tokens\)\s*$""")
private fun isStatusLine(line: String) = STATUS_LINE.containsMatchIn(line)

/** One line under the tabs: how many sessions need you, are working, are idle. */
@Composable
private fun SessionSummary(needs: Int, working: Int, idle: Int) {
    Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(Triple(needs, "needs you", claudeHealth(false, true)), Triple(working, "working", claudeHealth(true, false)), Triple(idle, "idle", claudeHealth(false, false)))
            .filter { it.first > 0 }
            .forEachIndexed { i, (n, label, health) ->
                if (i > 0) Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                StatusDot(health)
                Text("$n $label", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
    }
}

/** A session as one calm row: status dot, name, one line of what it is doing, uptime, and a menu for everything else. */
@Composable
private fun SessionRow(s: Session, onOpen: () -> Unit, actions: SessionActions) {
    var menu by remember { mutableStateOf(false) }
    val line = previewLine(s.preview)
    val state = sessionState(s.busy, s.waiting)
    val body: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.clickable(onClickLabel = "Open ${s.project}", role = Role.Button, onClick = onOpen).padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatusDot(claudeHealth(s.busy, s.waiting))
            Column(Modifier.weight(1f)) {
                Text(s.project, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stateLine(state, line),
                    modifier = Modifier.semantics { contentDescription = stateSpoken(state, line) },
                    style =MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (s.waiting) {
                Button(
                    onClick = onOpen, modifier = Modifier.height(38.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WarnAmber, contentColor = Color(0xFF1A1305)),
                ) { OneLine("Answer") }
            } else {
                Text(formatUptime(s.uptimeSeconds), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More for ${s.project}") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Terminal") }, onClick = { menu = false; actions.terminal() })
                    DropdownMenuItem(text = { Text("Restart") }, onClick = { menu = false; actions.restart() })
                    DropdownMenuItem(text = { Text("Open in Claude app") }, onClick = { menu = false; actions.openInClaude() })
                    actions.toggleDoneAlerts?.let { toggle ->
                        DropdownMenuItem(text = { Text(if (s.pushDone) "Finished alerts: on (tap to turn off)" else "Finished alerts: off (tap to turn on)") }, onClick = { menu = false; toggle() })
                    }
                    DropdownMenuItem(text = { Text("Stop session") }, onClick = { menu = false; actions.stop() })
                }
            }
        }
    }
    if (s.waiting) {
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.statusColors.warnContainer, contentColor = MaterialTheme.statusColors.onWarnContainer, border = BorderStroke(1.dp, MaterialTheme.statusColors.warnBorder)) { body() }
    } else body()
}
