package life.mygig.clauderc.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import life.mygig.clauderc.ui.components.Health
import life.mygig.clauderc.ui.components.StatusDot

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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import life.mygig.clauderc.api.PROJECT_NAME_RE
import life.mygig.clauderc.api.Repo
import life.mygig.clauderc.ui.MainViewModel
import life.mygig.clauderc.ui.openUrl

private enum class Filter(val label: String) { RUNNING("Running"), LOCAL("On server"), ALL("All"), ARCHIVED("Archived") }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProjectsScreen(vm: MainViewModel) {
    val repos by vm.repos.collectAsState()
    val refreshing by vm.reposRefreshing.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(Filter.RUNNING) }
    var selected by remember { mutableStateOf<Repo?>(null) }

    // Cached list shows instantly; refresh in the background.
    LaunchedEffect(Unit) { vm.refreshRepos(false) }

    val shown = remember(repos, query, filter) {
        repos.asSequence()
            .filter { query.isBlank() || it.fullName.contains(query.trim(), ignoreCase = true) }
            .filter {
                when (filter) {
                    Filter.RUNNING -> it.running
                    Filter.LOCAL -> it.local && !it.archived
                    Filter.ALL -> !it.archived
                    Filter.ARCHIVED -> it.archived
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
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Filter.entries.forEach { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
            }
        }
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { vm.refreshRepos(true) }, modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (shown.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(when {
                            refreshing -> "Loading repos…"
                            filter == Filter.RUNNING && query.isBlank() -> "No projects running. Start one from All, or from Sessions."
                            else -> "No repos match."
                        })
                    }
                }
                items(shown, key = { it.fullName }) { repo ->
                    RepoTile(
                        repo,
                        onClick = { selected = repo },
                        onLongClick = { if (repo.running) vm.loadTail(repo.name) else selected = repo },
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Tap a project for Open in Claude, clone/pull + start, stop, log or GitHub. Long-press a running one for its log.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    selected?.let { repo -> RepoActions(vm, repo) { selected = null } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RepoTile(repo: Repo, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusDot(when { repo.running -> Health.OK; repo.cloning -> Health.WARN; repo.local -> Health.OFF; else -> Health.NONE })
                Text(repo.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                when { repo.cloning -> "Cloning…"; repo.running -> "Running"; repo.local -> "On server"; else -> "GitHub only" } +
                    (repo.pushedAt?.take(10)?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Badge(if (repo.private) "Private" else "Public")
                if (repo.archived) Badge("Archived")
                if (repo.isOrg) Badge(repo.owner)
            }
        }
    }
}

/** A plain label (not a button) so screen readers don't announce it as tappable. */
@Composable
private fun Badge(text: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), maxLines = 1)
    }
}

@Composable
private fun RepoActions(vm: MainViewModel, repo: Repo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var ask by remember { mutableStateOf<Ask?>(null) }
    var newName by remember { mutableStateOf(repo.name) }
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(repo.fullName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when {
                        repo.cloning -> "Still cloning on the server. You'll get a message when it's done."
                        repo.running -> "Claude is running for this project."
                        repo.local -> "The folder is on the server."
                        else -> "Not on the server yet — Open will clone it."
                    },
                )
                if (repo.cloning) {
                    // Nothing to do until the clone finishes.
                } else if (!repo.running) {
                    Button(onClick = { vm.openRepo(repo, start = true); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (repo.local) "Pull & start Claude" else "Clone & start Claude")
                    }
                    OutlinedButton(onClick = { vm.openRepo(repo, start = false); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (repo.local) "Pull only" else "Clone only")
                    }
                } else {
                    Button(
                        onClick = { vm.stopProject(repo.name); onDismiss() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Stop") }
                    OutlinedButton(onClick = { vm.loadTail(repo.name); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Tail log")
                    }
                }
                TextButton(onClick = { openUrl(context, "https://github.com/${repo.fullName}") }) {
                    Text("Open on GitHub")
                }
                HorizontalDivider()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { ask = Ask.RENAME }) { Text("Rename") }
                    TextButton(onClick = { ask = Ask.VISIBILITY }) { Text(if (repo.private) "Make public" else "Make private") }
                    TextButton(onClick = { ask = Ask.DELETE }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )

    val cancel = { ask = null }
    when (ask) {
        Ask.RENAME -> AlertDialog(
            onDismissRequest = cancel,
            title = { Text("Rename ${repo.name}") },
            text = {
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it.trim() }, label = { Text("New name") }, singleLine = true,
                    isError = newName.isNotEmpty() && !PROJECT_NAME_RE.matches(newName),
                )
            },
            confirmButton = {
                TextButton(onClick = { ask = Ask.RENAME_SURE }, enabled = newName != repo.name && PROJECT_NAME_RE.matches(newName)) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = cancel) { Text("Cancel") } },
        )
        Ask.RENAME_SURE -> AlertDialog(
            onDismissRequest = cancel,
            title = { Text("Are you sure?") },
            text = {
                Text("Rename ${repo.fullName} to ${repo.owner}/$newName on GitHub. Links to the old name redirect." +
                    if (repo.local) " The folder on the server keeps its old name." else "")
            },
            confirmButton = { TextButton(onClick = { vm.renameRepo(repo, newName); ask = null; onDismiss() }) { Text("Yes, rename") } },
            dismissButton = { TextButton(onClick = cancel) { Text("Cancel") } },
        )
        Ask.VISIBILITY -> AlertDialog(
            onDismissRequest = cancel,
            title = { Text("Are you sure?") },
            text = {
                Text(
                    if (repo.private) "Make ${repo.fullName} public? Anyone can then see its code and history."
                    else "Make ${repo.fullName} private? Stars and watchers from others are removed, and forks may break.",
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.setRepoPrivate(repo, !repo.private); ask = null; onDismiss() }) {
                    Text(if (repo.private) "Yes, make public" else "Yes, make private")
                }
            },
            dismissButton = { TextButton(onClick = cancel) { Text("Cancel") } },
        )
        Ask.DELETE -> AlertDialog(
            onDismissRequest = cancel,
            title = { Text("Delete ${repo.fullName}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This deletes the repo on GitHub for good: code, issues, pull requests and releases. It can't be undone." +
                        if (repo.local) " The folder on the server stays." else "")
                    Text("Type delete to confirm.")
                    OutlinedTextField(value = typed, onValueChange = { typed = it }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteRepo(repo); ask = null; onDismiss() }, enabled = typed.trim().equals("delete", ignoreCase = true)) {
                    Text("Delete forever", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = cancel) { Text("Cancel") } },
        )
        null -> {}
    }
}

private enum class Ask { RENAME, RENAME_SURE, VISIBILITY, DELETE }
