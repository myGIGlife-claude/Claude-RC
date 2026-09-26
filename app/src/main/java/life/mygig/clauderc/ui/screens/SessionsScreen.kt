package life.mygig.clauderc.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import life.mygig.clauderc.api.Session
import life.mygig.clauderc.ui.LocalGuard
import life.mygig.clauderc.ui.MainViewModel
import life.mygig.clauderc.ui.formatUptime
import life.mygig.clauderc.ui.openInClaude

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(vm: MainViewModel) {
    val sessions by vm.sessions.collectAsState()
    val refreshing by vm.sessionsRefreshing.collectAsState()
    val guard = LocalGuard.current
    val context = LocalContext.current
    var confirmStop by remember { mutableStateOf<Session?>(null) }

    LaunchedEffect(Unit) { vm.refreshSessions() }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { vm.refreshSessions() }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text("", Modifier.padding(top = 2.dp)) }
            if (sessions.isEmpty()) {
                item { Text(if (refreshing) "Loading…" else "No Claude sessions running.") }
            }
            items(sessions, key = { it.name }) { s ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(s.project, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Up ${formatUptime(s.uptimeSeconds)}" + if (s.attached) " · attached" else " · detached",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (s.dir.isNotBlank()) Text(s.dir, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            Button(onClick = { openInClaude(context) }) { Text("Open in Claude") }
                            OutlinedButton(onClick = { vm.loadTail(s.name) }) { Text("Tail") }
                            TextButton(onClick = { confirmStop = s }) { Text("Stop") }
                        }
                    }
                }
            }
        }
    }

    confirmStop?.let { s ->
        AlertDialog(
            onDismissRequest = { confirmStop = null },
            title = { Text("Stop ${s.project}?") },
            text = { Text("This ends the Claude Remote Control session on the server.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmStop = null
                    guard.run("Stop ${s.project}") { vm.stopProject(s.name) }
                }) { Text("Stop") }
            },
            dismissButton = { TextButton(onClick = { confirmStop = null }) { Text("Cancel") } },
        )
    }
}
