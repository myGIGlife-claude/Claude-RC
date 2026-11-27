package life.mygig.clauderc.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import life.mygig.clauderc.api.CopyEntry
import life.mygig.clauderc.api.CopyKind
import life.mygig.clauderc.data.Server
import life.mygig.clauderc.ui.MainViewModel
import life.mygig.clauderc.ui.components.CalmDivider
import life.mygig.clauderc.ui.components.CalmGroup
import life.mygig.clauderc.ui.components.SectionLabel
import life.mygig.clauderc.ui.vm.CopyStep
import life.mygig.clauderc.ui.vm.CopyUi
import life.mygig.clauderc.ui.vm.copyServerLabel

private val COPY_STEPS = listOf(
    CopyStep.PICK to "Choose",
    CopyStep.CHECK to "Check",
    CopyStep.CHOOSE to "Pick what to copy",
    CopyStep.ROUTE to "Route",
    CopyStep.SEND to "Send",
    CopyStep.APPLY to "Apply",
)

private fun sizeText(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${bytes / (1024 * 1024)} MB"
}

/** Copy from one server to another, server to server. Names only: secret values never reach the phone. */
@Composable
fun CopyScreen(vm: MainViewModel) {
    val ui by vm.copy.ui.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val servers = settings?.servers.orEmpty().filter { it.isConfigured }
    val scroll = rememberScrollState()
    LaunchedEffect(ui.step) { scroll.scrollTo(0) }
    val done = ui.step == CopyStep.DONE
    val index = if (done) COPY_STEPS.size else COPY_STEPS.indexOfFirst { it.first == ui.step } + 1
    val title = if (done) "Done" else COPY_STEPS.firstOrNull { it.first == ui.step }?.second.orEmpty()
    Dialog(onDismissRequest = { vm.copy.close() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        DialogKeyboardFix()
        NoAutofill()
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { vm.copy.close() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close") }
                    Column(Modifier.weight(1f)) {
                        Text("Copy between servers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Step $index of ${COPY_STEPS.size}: $title",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                LinearProgressIndicator(progress = { index / COPY_STEPS.size.toFloat() }, modifier = Modifier.fillMaxWidth())
                if (ui.step == CopyStep.CHOOSE && !ui.busy && ui.error == null) {
                    CopyChoose(vm, ui, Modifier.weight(1f).align(Alignment.CenterHorizontally).widthIn(max = 720.dp).fillMaxWidth())
                } else {
                    Column(
                        Modifier.weight(1f).align(Alignment.CenterHorizontally).widthIn(max = 720.dp).fillMaxWidth().verticalScroll(scroll).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ui.error?.let { CopyError(vm, ui) }
                        when (ui.step) {
                            CopyStep.PICK -> CopyPick(vm, ui, servers)
                            CopyStep.DONE -> CopyDone(vm, ui)
                            else -> CopyProgress(vm, ui)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CopyNotice(container: Color, content: Color, body: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = container, contentColor = content) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = body)
    }
}

@Composable
private fun CopyHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CopyError(vm: MainViewModel, ui: CopyUi) {
    CopyNotice(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer) {
        SelectionContainer { Text(ui.error.orEmpty(), style = MaterialTheme.typography.bodyMedium) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { vm.copy.retry() }, enabled = !ui.busy) { Text("Try again") }
            if (ui.step == CopyStep.CHECK) {
                OutlinedButton(onClick = { vm.copy.edit() }, enabled = !ui.busy) { Text("Back") }
            } else if (ui.step != CopyStep.PICK) {
                OutlinedButton(onClick = { vm.copy.cancel() }, enabled = !ui.busy) { Text(if (ui.keyOnTo) "Cancel copy" else "Back") }
            }
        }
    }
}

// ---- 1. Choose ---------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CopyDropdown(label: String, value: String, options: List<Pair<String, String>>, enabled: Boolean, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it && enabled }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (id, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onPick(id) }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CopyPick(vm: MainViewModel, ui: CopyUi, servers: List<Server>) {
    val from = ui.from
    val to = ui.to
    val same = from != null && to != null && from.id == to.id
    val options = servers.map { it.id to copyServerLabel(it) }
    Text("Send files from one server to another. They travel straight between the two servers, never through this phone.", style = MaterialTheme.typography.bodyMedium)
    SectionLabel("Servers")
    CopyDropdown("Copy from", from?.let { copyServerLabel(it) }.orEmpty(), options, !ui.busy) { vm.copy.setServers(it, to?.id.orEmpty()) }
    CopyDropdown("Copy to", to?.let { copyServerLabel(it) }.orEmpty(), options, !ui.busy) { vm.copy.setServers(from?.id.orEmpty(), it) }
    if (same) Text("Pick two different servers.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    SectionLabel("What to copy")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CopyKind.entries.forEach { k ->
            FilterChip(selected = ui.kind == k, onClick = { vm.copy.setKind(k) }, label = { Text(k.label) }, enabled = !ui.busy)
        }
    }
    when (ui.kind) {
        CopyKind.PROJECT -> {
            CopyDropdown("Project", ui.project, ui.projects.map { it to it }, !ui.busy && from != null) { vm.copy.setProject(it) }
            CopyHint("A project that isn't on the second server is copied whole, including its git history. One that is there only gets the files git doesn't track (build files, .env), and its git history is never changed.")
        }
        CopyKind.SECRETS -> CopyHint(
            "Adds the key names the second server doesn't have yet, and secret files. A name that exists there with another value is reported and never overwritten. Signing keystores and Apple keys are listed separately and not ticked. Claude and worker sign-ins are never copied.",
        )
        CopyKind.BUNDLE -> CopyHint("Sends branches that were never pushed, as git bundles. The second server keeps them under separate names; its own branches don't move.")
    }
    Button(
        onClick = { vm.copy.start() },
        enabled = !ui.busy && from != null && to != null && !same && (ui.kind != CopyKind.PROJECT || ui.project.isNotBlank()),
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Check both servers") }
}

// ---- 3. Choose what to copy ----------------------------------------------------------

@Composable
private fun CopyChoose(vm: MainViewModel, ui: CopyUi, modifier: Modifier) {
    val diff = ui.diff ?: return
    val groups = listOf(
        Triple("Missing on ${ui.to?.host.orEmpty()}", diff.missingOnTo, "These are not on the second server."),
        Triple("Newer on ${ui.from?.host.orEmpty()}", diff.newerOnFrom, "These are older on the second server and will be replaced; the old copy is kept in ~/backups."),
        Triple("Newer on ${ui.to?.host.orEmpty()}", diff.newerOnTo, "The second server has a newer or different version. Tick only what you want overwritten; the old copy is kept in ~/backups."),
    )
    val count = diff.all.count { it.path in ui.ticked }
    Column(modifier) {
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${ui.from?.let { copyServerLabel(it) }.orEmpty()}  →  ${ui.to?.let { copyServerLabel(it) }.orEmpty()}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (diff.isEmpty) Text("Nothing to copy: the second server already has everything.", style = MaterialTheme.typography.bodyMedium)
                    if (ui.truncated) CopyHint("The list was cut at 20,000 entries. Run the copy again afterwards to catch the rest.")
                    if (ui.unsendable > 0) CopyHint("${ui.unsendable} item(s) with a line break in the name were left out.")
                }
            }
            groups.forEachIndexed { gi, (title, entries, hint) ->
                if (entries.isEmpty()) return@forEachIndexed
                item(key = "h-$gi") {
                    Column(Modifier.padding(top = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { SectionLabel("$title (${entries.size})") }
                            val all = entries.all { it.path in ui.ticked }
                            TextButton(onClick = { vm.copy.setGroup(entries, !all) }) { Text(if (all) "None" else "All") }
                        }
                        CopyHint(hint)
                    }
                }
                items(entries.size, key = { "$gi/${entries[it].path}" }) { i ->
                    CopyRow(entries[i], entries[i].path in ui.ticked) { vm.copy.toggle(entries[i].path) }
                }
            }
        }
        Column(Modifier.padding(16.dp)) {
            Button(onClick = { vm.copy.copyNow() }, enabled = count > 0, modifier = Modifier.fillMaxWidth()) { Text("Copy $count item(s)") }
            TextButton(onClick = { vm.copy.edit() }, modifier = Modifier.fillMaxWidth()) { Text("Back") }
        }
    }
}

@Composable
private fun CopyRow(e: CopyEntry, ticked: Boolean, onToggle: () -> Unit) {
    CalmGroup {
        Row(Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = ticked, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(e.path, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                if (e.optional) Text("Signing key: only copied when ticked", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
            val size = sizeText(e.size)
            if (size.isNotEmpty()) Text(size, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---- 2, 4, 5, 6. Working -------------------------------------------------------------

@Composable
private fun CopyProgress(vm: MainViewModel, ui: CopyUi) {
    if (ui.busy) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(ui.log.lastOrNull() ?: "Working…", style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (ui.route.isNotEmpty()) Text("Route: ${ui.route}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    ui.status?.takeIf { ui.step == CopyStep.SEND || ui.step == CopyStep.APPLY }?.let { st ->
        LinearProgressIndicator(progress = { (st.pct.coerceIn(0, 100)) / 100f }, modifier = Modifier.fillMaxWidth())
        val size = sizeText(st.bytes)
        Text(listOf(st.message, size).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (ui.log.isNotEmpty()) {
        CalmGroup {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ui.log.takeLast(8).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
    if (ui.busy && ui.keyOnTo) {
        OutlinedButton(onClick = { vm.copy.cancel() }, modifier = Modifier.fillMaxWidth()) { Text("Cancel copy") }
    }
}

// ---- Result --------------------------------------------------------------------------

@Composable
private fun CopyDone(vm: MainViewModel, ui: CopyUi) {
    val r = ui.result
    Text("Copy finished", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    if (ui.route.isNotEmpty()) Text("Route: ${ui.route}", style = MaterialTheme.typography.bodyMedium)
    if (r != null && r.message.isNotBlank()) Text(r.message, style = MaterialTheme.typography.bodyMedium)
    if (r != null && r.skipped.isNotEmpty()) CopyPathList("Skipped (kept as they were)", r.skipped)
    if (r != null && r.refused.isNotEmpty()) CopyPathList("Refused (unsafe paths)", r.refused)
    if (r?.keyRemoved == true) {
        Text("The copy key was removed from ${ui.to?.host.orEmpty()}.", style = MaterialTheme.typography.bodyMedium)
    } else {
        CopyNotice(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer) {
            Text("${ui.to?.host.orEmpty()} didn't confirm that the copy key was removed. Remove it now to be safe.", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { vm.copy.cancel() }) { Text("Remove the key") }
        }
    }
    Button(onClick = { vm.copy.reset(open = false) }, modifier = Modifier.fillMaxWidth()) { Text("Close") }
}

@Composable
private fun CopyPathList(title: String, paths: List<String>) {
    SectionLabel("$title (${paths.size})")
    CalmGroup {
        Column {
            paths.take(50).forEachIndexed { i, p ->
                if (i > 0) CalmDivider()
                SelectionContainer { Text(p, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall) }
            }
            if (paths.size > 50) Text("and ${paths.size - 50} more", Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}
