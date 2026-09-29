package life.mygig.clauderc.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import life.mygig.clauderc.api.Session
import life.mygig.clauderc.ui.LocalGuard
import life.mygig.clauderc.ui.MainViewModel
import life.mygig.clauderc.ui.components.Health
import life.mygig.clauderc.ui.components.StatusDot
import life.mygig.clauderc.ui.formatUptime
import life.mygig.clauderc.ui.openInClaude
import life.mygig.clauderc.ui.theme.Term
import life.mygig.clauderc.ui.theme.WarnAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(vm: MainViewModel) {
    val sessions by vm.sessions.collectAsState()
    val refreshing by vm.sessionsRefreshing.collectAsState()
    val settings by vm.settings.collectAsState()
    val guard = LocalGuard.current
    val context = LocalContext.current
    var confirmStop by remember { mutableStateOf<Session?>(null) }
    var confirmRestart by remember { mutableStateOf<Session?>(null) }
    var confirmRestartAll by remember { mutableStateOf(false) }
    val busyRestart by vm.busyRestart.collectAsState()

    // Previews and "needs an answer" stay current while the tab is open.
    LaunchedEffect(Unit) {
        while (true) {
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
                        OutlinedButton(onClick = { confirmRestartAll = true }, modifier = Modifier.weight(1f)) { Text("Restart all") }
                    }
                    // A shell on the server: only offered when App lock guards the app.
                    if (settings?.appLock == true) {
                        OutlinedButton(onClick = { vm.showRun(true) }, modifier = Modifier.weight(1f)) { Text(">_ Run a command") }
                    }
                }
            }
            if (sessions.isEmpty()) item { Text(if (refreshing) "Loading…" else "No Claude sessions running.") }
            // Sessions waiting on a question first.
            items(sessions.sortedByDescending { it.waiting }, key = { it.name }) { s ->
                SessionCard(
                    s,
                    onOpen = { openInClaude(context) },
                    onLog = { vm.loadTail(s.name) },
                    onRestart = { confirmRestart = s },
                    onStop = { confirmStop = s },
                )
            }
        }
    }

    if (confirmRestartAll) {
        AlertDialog(
            onDismissRequest = { confirmRestartAll = false },
            title = { Text("Restart all sessions?") },
            text = { Text("Each session restarts in its folder and reopens the same conversation, so new plugins, skills, MCP servers and tokens are picked up. Sessions where Claude is still working are skipped.") },
            confirmButton = { TextButton(onClick = { confirmRestartAll = false; guard.run("Restart all sessions") { vm.restartAll() } }) { Text("Restart all") } },
            dismissButton = { TextButton(onClick = { confirmRestartAll = false }) { Text("Cancel") } },
        )
    }
    busyRestart?.let { name ->
        AlertDialog(
            onDismissRequest = { vm.dismissBusyRestart() },
            title = { Text("Claude is still working") },
            text = { Text("Restarting now interrupts what it's doing in $name. The conversation comes back, but the current step stops.") },
            confirmButton = { TextButton(onClick = { guard.run("Restart $name") { vm.restartSession(name, force = true) } }) { Text("Restart anyway") } },
            dismissButton = { TextButton(onClick = { vm.dismissBusyRestart() }) { Text("Wait") } },
        )
    }
    confirmRestart?.let { s ->
        AlertDialog(
            onDismissRequest = { confirmRestart = null },
            title = { Text("Restart ${s.project}?") },
            text = { Text("Stops this Claude session and starts it again in the same folder, so it picks up new plugins, skills, MCP servers and tokens. It reopens the same conversation, so nothing is lost. If Claude is still working, you'll be asked first.") },
            confirmButton = { TextButton(onClick = { confirmRestart = null; guard.run("Restart ${s.project}") { vm.restartSession(s.name) } }) { Text("Restart") } },
            dismissButton = { TextButton(onClick = { confirmRestart = null }) { Text("Cancel") } },
        )
    }
    confirmStop?.let { s ->
        AlertDialog(
            onDismissRequest = { confirmStop = null },
            title = { Text("Stop ${s.project}?") },
            text = { Text("This ends the Claude Remote Control session on the server.") },
            confirmButton = { TextButton(onClick = { confirmStop = null; guard.run("Stop ${s.project}") { vm.stopProject(s.name) } }) { Text("Stop") } },
            dismissButton = { TextButton(onClick = { confirmStop = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SessionCard(s: Session, onOpen: () -> Unit, onLog: () -> Unit, onRestart: () -> Unit, onStop: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (s.waiting) Color(0xFF2A2418) else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (s.waiting) BorderStroke(1.dp, Color(0xFF5C4A24)) else null,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusDot(if (s.waiting) Health.WARN else Health.OK)
                Text(s.project, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        s.waiting -> "needs an answer"
                        s.busy -> "working… · up ${formatUptime(s.uptimeSeconds)}"
                        else -> "up ${formatUptime(s.uptimeSeconds)}" + if (s.attached) " · attached" else ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (s.waiting) WarnAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (s.preview.isNotBlank()) {
                Box(Modifier.fillMaxWidth().background(Term.Field, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(s.preview, color = Term.Fg, fontFamily = FontFamily.Monospace, fontSize = 11.5.sp, lineHeight = 17.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (s.waiting) {
                    Button(
                        onClick = onLog,
                        modifier = Modifier.weight(1f).height(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WarnAmber, contentColor = Color(0xFF1A1305)),
                    ) { Text("Answer") }
                } else {
                    Button(onClick = onOpen, modifier = Modifier.weight(1f).height(40.dp)) { Text("Open in Claude") }
                    OutlinedButton(onClick = onLog, modifier = Modifier.height(40.dp)) { Text("Log") }
                }
                TextButton(onClick = onRestart) { Text("Restart") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More for ${s.project}") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (s.waiting) DropdownMenuItem(text = { Text("Open in Claude") }, onClick = { menu = false; onOpen() })
                        DropdownMenuItem(text = { Text("Stop session") }, onClick = { menu = false; onStop() })
                    }
                }
            }
        }
    }
}
