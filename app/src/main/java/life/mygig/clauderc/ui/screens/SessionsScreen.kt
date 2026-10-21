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
import life.mygig.clauderc.ui.components.OneLine
import life.mygig.clauderc.ui.components.StatusDot
import life.mygig.clauderc.ui.components.claudeHealth
import life.mygig.clauderc.ui.components.isNarrow
import life.mygig.clauderc.ui.formatUptime
import life.mygig.clauderc.ui.openInClaude
import life.mygig.clauderc.ui.theme.Term
import life.mygig.clauderc.ui.theme.WarnAmber

/** Two tabs: General chat (conversations not tied to a repo) and Code chat (the project sessions). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(vm: MainViewModel) {
    // Saved for process death; the controller keeps it while you visit other tabs.
    var tab by rememberSaveable { mutableIntStateOf(vm.general.tab.value) }
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tab) {
            listOf("General chat", "Code chat").forEachIndexed { i, label ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i; vm.general.selectTab(i) },
                    text = { OneLine(label) },
                )
            }
        }
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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (sessions.size > 1) {
                        OutlinedButton(onClick = { confirmRestartAll = true }, modifier = Modifier.weight(1f)) { OneLine("Restart all") }
                    }
                    // A shell on the server: only offered when App lock guards the app.
                    if (settings?.appLock == true) {
                        OutlinedButton(onClick = { scope.launch { if (appLock?.unlock("Run a command on the server") != false) vm.showRun(true) } }, modifier = Modifier.weight(1f)) { OneLine(if (narrow) ">_ Run" else ">_ Run a command") }
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
                item { SessionGroup { working.forEachIndexed { i, s -> if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)); rowFor(s) } } }
            }
            if (idle.isNotEmpty()) {
                item { SectionLabel("Idle") }
                item { SessionGroup { idle.forEachIndexed { i, s -> if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)); rowFor(s) } } }
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

/** The session's last meaningful line, without prompt symbols: what it said or is waiting for. */
internal fun previewLine(preview: String): String =
    preview.lineSequence().map { it.trim().trimStart { c -> !c.isLetterOrDigit() } }.lastOrNull { it.isNotBlank() }.orEmpty()

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

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

@Composable
private fun SessionGroup(content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column { content() }
    }
}

/** A session as one calm row: status dot, name, one line of what it is doing, uptime, and a menu for everything else. */
@Composable
private fun SessionRow(s: Session, onOpen: () -> Unit, actions: SessionActions) {
    var menu by remember { mutableStateOf(false) }
    val line = previewLine(s.preview)
    val body: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatusDot(claudeHealth(s.busy, s.waiting))
            Column(Modifier.weight(1f)) {
                Text(s.project, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    line.ifEmpty { if (s.waiting) "needs an answer" else if (s.busy) "working…" else "idle" },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
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
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Color(0xFF2A2418), border = BorderStroke(1.dp, Color(0xFF5C4A24))) { body() }
    } else body()
}
