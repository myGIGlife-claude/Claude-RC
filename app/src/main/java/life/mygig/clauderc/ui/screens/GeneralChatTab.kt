package life.mygig.clauderc.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import life.mygig.clauderc.api.GENERAL_TITLE_MAX
import life.mygig.clauderc.api.GeneralChat
import life.mygig.clauderc.api.Updates
import life.mygig.clauderc.api.cleanGeneralTitle
import life.mygig.clauderc.ui.MainViewModel
import life.mygig.clauderc.ui.components.Health
import life.mygig.clauderc.ui.components.OneLine
import life.mygig.clauderc.ui.components.StatusDot
import life.mygig.clauderc.ui.components.claudeHealth
import life.mygig.clauderc.ui.theme.Term
import life.mygig.clauderc.ui.theme.WarnAmber
import life.mygig.clauderc.ui.vm.generalTitle
import life.mygig.clauderc.ui.vm.relativeTime

/** General chats: a New chat button and every saved chat, running or stopped, newest first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralChatTab(vm: MainViewModel) {
    val chats by vm.general.chats.collectAsStateWithLifecycle()
    val refreshing by vm.general.refreshing.collectAsStateWithLifecycle()
    val loaded by vm.general.loaded.collectAsStateWithLifecycle()
    val opening by vm.general.opening.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val outdated = status?.let { it.scriptApi < Updates.MIN_SCRIPT_API } == true
    var renaming by remember { mutableStateOf<GeneralChat?>(null) }
    var confirmDelete by remember { mutableStateOf<GeneralChat?>(null) }

    // Status and previews stay current while the tab is open.
    val foreground by vm.foreground.collectAsStateWithLifecycle()
    LaunchedEffect(foreground, outdated) {
        while (foreground && !outdated) {
            vm.general.refresh()
            delay(15_000)
        }
    }

    if (outdated) {
        Text(
            "Server scripts need an update before you can use general chats.",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { vm.general.refresh() }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Button(onClick = { vm.general.newChat() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    OneLine("New chat")
                }
            }
            if (chats.isEmpty()) item {
                Text(
                    if (loaded) "No chats yet. Tap New chat to start one." else if (refreshing) "Loading…" else "",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val now = System.currentTimeMillis() / 1000
            items(chats, key = { it.id }) { c ->
                GeneralChatRow(
                    c,
                    now = now,
                    opening = opening == c.id,
                    onOpen = { vm.general.open(c) },
                    onRename = { renaming = c },
                    onStop = { vm.general.stop(c.id) },
                    onDelete = { confirmDelete = c },
                )
            }
        }
    }

    renaming?.let { c -> RenameChatDialog(c, onDismiss = { renaming = null }) { title -> renaming = null; vm.general.rename(c.id, title) } }
    confirmDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this chat?") },
            text = { Text("It is moved aside on the server, not erased.") },
            confirmButton = { TextButton(onClick = { confirmDelete = null; vm.general.delete(c.id) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun GeneralChatRow(
    c: GeneralChat,
    now: Long,
    opening: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val title = generalTitle(c)
    val waiting = c.running && c.waiting
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = Modifier.fillMaxWidth().clip(shape).clickable(enabled = !opening, onClickLabel = "Open chat", onClick = onOpen),
        shape = shape,
        color = if (waiting) Color(0xFF2A2418) else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (waiting) BorderStroke(1.dp, Color(0xFF5C4A24)) else null,
    ) {
        Column(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 12.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    opening -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    c.running -> StatusDot(claudeHealth(c.busy, c.waiting))
                    else -> StatusDot(Health.NONE)
                }
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More for $title") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                        if (c.running) DropdownMenuItem(text = { Text("Stop") }, onClick = { menu = false; onStop() })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            val state = when {
                opening -> "starting…"
                !c.running -> "Saved"
                c.waiting -> "needs an answer"
                c.busy -> "working"
                else -> "running"
            }
            val time = relativeTime(if (c.updatedAt > 0) c.updatedAt else c.createdAt, now)
            Text(
                listOf(time, state).filter { it.isNotEmpty() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = if (waiting) WarnAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 10.dp),
            )
            if (c.running && c.preview.isNotBlank()) {
                Box(Modifier.padding(top = 6.dp, end = 10.dp).fillMaxWidth().background(Term.Field, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(c.preview, color = Term.Fg, fontFamily = FontFamily.Monospace, fontSize = 11.5.sp, lineHeight = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun RenameChatDialog(c: GeneralChat, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var title by rememberSaveable(c.id) { mutableStateOf(c.title.trim()) }
    val clean = cleanGeneralTitle(title)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename chat") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { v -> title = v.replace('\n', ' ').take(GENERAL_TITLE_MAX) },
                label = { Text("Title") },
                singleLine = true,
                supportingText = { Text("${title.length}/$GENERAL_TITLE_MAX") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { clean?.let(onSave) }, enabled = clean != null) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
