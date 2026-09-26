package life.mygig.clauderc.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import life.mygig.clauderc.api.Repo
import life.mygig.clauderc.ui.LocalGuard
import life.mygig.clauderc.ui.MainViewModel
import life.mygig.clauderc.ui.openUrl

private enum class Filter(val label: String) { ALL("All"), LOCAL("On server"), RUNNING("Running") }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProjectsScreen(vm: MainViewModel) {
    val repos by vm.repos.collectAsState()
    val refreshing by vm.reposRefreshing.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    var selected by remember { mutableStateOf<Repo?>(null) }

    // Cached list shows instantly; refresh in the background.
    LaunchedEffect(Unit) { vm.refreshRepos(false) }

    val shown = remember(repos, query, filter) {
        repos.asSequence()
            .filter { query.isBlank() || it.fullName.contains(query.trim(), ignoreCase = true) }
            .filter {
                when (filter) {
                    Filter.ALL -> true
                    Filter.LOCAL -> it.local
                    Filter.RUNNING -> it.running
                }
            }
            .sortedByDescending { it.pushedAt ?: "" }
            .toList()
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search repos") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Filter.entries.forEach { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
            }
        }
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { vm.refreshRepos(true) }, modifier = Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize()) {
                if (shown.isEmpty()) {
                    item {
                        Text(
                            if (refreshing) "Loading repos…" else "No repos match.",
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                items(shown, key = { it.fullName }) { repo ->
                    ListItem(
                        headlineContent = { Text(repo.name) },
                        supportingContent = {
                            Column {
                                Text(repo.owner, style = MaterialTheme.typography.bodySmall)
                                Badges(repo)
                            }
                        },
                        modifier = Modifier.combinedClickable(
                            onClick = { selected = repo },
                            onLongClick = { if (repo.running) vm.loadTail(repo.name) else selected = repo },
                        ),
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    selected?.let { repo -> RepoActions(vm, repo) { selected = null } }
}

@Composable
private fun Badges(repo: Repo) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Badge(if (repo.private) "Private" else "Public")
        if (repo.isOrg) Badge("Org")
        if (repo.local) Badge("On server")
        if (repo.running) Badge("Running")
    }
}

@Composable
private fun Badge(text: String) {
    AssistChip(onClick = {}, label = { Text(text, style = MaterialTheme.typography.labelSmall) })
}

@Composable
private fun RepoActions(vm: MainViewModel, repo: Repo, onDismiss: () -> Unit) {
    val guard = LocalGuard.current
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(repo.fullName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when {
                        repo.running -> "Claude is running for this project."
                        repo.local -> "The folder is on the server."
                        else -> "Not on the server yet — Open will clone it."
                    },
                )
                if (!repo.running) {
                    Button(onClick = { vm.openRepo(repo, start = true); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (repo.local) "Pull & start Claude" else "Clone & start Claude")
                    }
                    OutlinedButton(onClick = { vm.openRepo(repo, start = false); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (repo.local) "Pull only" else "Clone only")
                    }
                } else {
                    Button(
                        onClick = { guard.run("Stop ${repo.name}") { vm.stopProject(repo.name) }; onDismiss() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Stop") }
                    OutlinedButton(onClick = { vm.loadTail(repo.name); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Tail log")
                    }
                }
                TextButton(onClick = { openUrl(context, "https://github.com/${repo.fullName}") }) {
                    Text("Open on GitHub")
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
