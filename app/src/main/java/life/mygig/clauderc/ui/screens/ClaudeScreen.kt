package life.mygig.clauderc.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import life.mygig.clauderc.api.InstalledPlugin
import life.mygig.clauderc.api.PluginsData
import life.mygig.clauderc.ui.MainViewModel
import life.mygig.clauderc.ui.Tab
import life.mygig.clauderc.ui.components.CardBox
import life.mygig.clauderc.ui.components.Health
import life.mygig.clauderc.ui.components.InfoTile
import life.mygig.clauderc.ui.components.OneLine
import life.mygig.clauderc.ui.components.PillTabs
import life.mygig.clauderc.ui.components.SectionLabel
import life.mygig.clauderc.ui.components.isNarrow
import life.mygig.clauderc.ui.theme.Term

/** Claude tab: Claude Code itself, plugins and marketplaces, other claude commands. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClaudeScreen(vm: MainViewModel) {
    val status by vm.status.collectAsStateWithLifecycle()
    val plugins by vm.plugins.collectAsStateWithLifecycle()
    val loading by vm.pluginsLoading.collectAsStateWithLifecycle()
    val result by vm.ccResult.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var install by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<InstalledPlugin?>(null) }
    var addMarket by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val change = { args: String -> vm.claudeCommand(args) }

    LaunchedEffect(status?.scriptApi) { vm.loadPlugins() }


    val p = plugins
    PullToRefreshBox(isRefreshing = loading, onRefresh = { vm.loadPlugins() }, modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            fullItem { Text("Claude Code ${p?.claudeVersion ?: ""}".trim(), style = MaterialTheme.typography.titleMedium) }
            fullItem { PillTabs(listOf("Accounts", "Plugins", "Tools"), tab, { tab = it }, badges = if (p?.installed?.any { it.update == "available" } == true) setOf(1) else emptySet()) }
            when (tab) {
                0 -> fullItem { ClusterSection(vm) }
                1 -> {
                    fullItem {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionLabel("Installed" + (p?.installed?.size?.let { " · $it" } ?: ""), Modifier.weight(1f))
                            Button(onClick = { install = true }, enabled = p != null) { OneLine("+ Install") }
                        }
                    }
                    if (p == null) fullItem { Text(if (loading) "Loading plugins…" else "Pull down to load plugins.") }
                    p?.installed?.sortedBy { it.name.lowercase() }?.sortedBy { it.update != "available" }?.forEach { pl ->
                        val (health, sub) = when {
                            !pl.enabled -> Health.OFF to if (pl.update == "available") "disabled · update available" else "disabled"
                            pl.update == "available" -> Health.WARN to "update available · ${pl.marketplace}"
                            pl.update == "error" -> Health.BAD to "can't check for updates · ${pl.marketplace}"
                            else -> Health.OK to "on · ${pl.marketplace}"
                        }
                        item { InfoTile(pl.name, sub, health, { open = pl }) }
                    }
                    fullItem { Text("Tap a plugin to enable, disable, update or remove it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (p?.installed?.isNotEmpty() == true) fullItem { Text("Green: on and current · Yellow: update available · Red: can't check · Hollow: off", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                else -> {
                    fullItem {
                        CardBox {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                ToolRow("Doctor", "Checks the install", "Run", busy == null) { vm.claudeCommand("doctor") }
                                ToolRow("Full checkup", "/doctor in a chat of its own; Claude can fix what it finds", "Start", busy == null) { vm.startCheckup() }
                                ToolRow("Update Claude", "Installs the newest version", "Update", busy == null) { change("update") }
                                ToolRow("Other command", "claude … (doctor, plugin, mcp list)", "Run…", true) { custom = true }
                            }
                        }
                    }
                    fullItem { SectionLabel("Marketplaces") }
                    fullItem {
                        CardBox {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                p?.marketplaces?.sortedBy { it.name.lowercase() }?.forEach { m ->
                                    val n = p.available.count { it.marketplace == m.name }
                                    Row {
                                        Text(m.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("$n plugin" + if (n == 1) "" else "s", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Row {
                                    TextButton(onClick = { addMarket = true }) { Text("+ Add marketplace") }
                                    TextButton(onClick = { change("plugin marketplace update") }, enabled = busy == null) { Text("Update all") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (install && p != null) InstallPluginSheet(vm, p, onClose = { install = false }, onAddMarketplace = { install = false; addMarket = true })
    open?.let { pl -> PluginSheet(vm, pl, p, onClose = { open = null }) }
    if (addMarket) {
        var source by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addMarket = false },
            title = { Text("Add a marketplace") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("A GitHub owner/repo (e.g. anthropics/claude-plugins) or a URL. Its plugins then show under + Install.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = source, onValueChange = { source = it.trim() }, singleLine = true, label = { Text("owner/repo or URL") }, keyboardOptions = KeyboardOptions(autoCorrectEnabled = false))
                }
            },
            confirmButton = { TextButton(onClick = { addMarket = false; change("plugin marketplace add $source") }, enabled = source.isNotBlank()) { Text("Add") } },
            dismissButton = { TextButton(onClick = { addMarket = false }) { Text("Cancel") } },
        )
    }
    if (custom) {
        var args by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { custom = false },
            title = { Text("Run a claude command") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("claude ", fontFamily = FontFamily.Monospace)
                        OutlinedTextField(value = args, onValueChange = { args = it.replace("\n", " ") }, singleLine = true, placeholder = { Text("mcp list") }, keyboardOptions = KeyboardOptions(autoCorrectEnabled = false))
                    }
                    Text("Allowed: doctor, update, --version, plugin …, plugin marketplace …, mcp list / get / remove.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { custom = false; change(args) }, enabled = args.isNotBlank()) { Text("Run") } },
            dismissButton = { TextButton(onClick = { custom = false }) { Text("Cancel") } },
        )
    }
    result?.let { (args, r) ->
        AlertDialog(
            onDismissRequest = { vm.clearCcResult() },
            title = { Text("claude $args", fontFamily = FontFamily.Monospace, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectionContainer(Modifier.heightIn(max = 360.dp).background(Term.Bg, RoundedCornerShape(10.dp)).padding(12.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
                        Text(r.output.ifBlank { "(no output)" }, color = Term.Fg, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                    Text(if (r.exitCode == 0) "Done (exit 0)" else "Exit code ${r.exitCode}", color = if (r.exitCode == 0) Term.Label else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                    if (r.exitCode == 0 && Regex("^(plugins? (install|i|uninstall|remove|enable|disable|update)|mcp remove)\\b").containsMatchIn(args)) {
                        Text("Running sessions pick this up after a restart.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.clearCcResult() }) { Text("Close") } },
            dismissButton = if (r.exitCode == 0 && args.startsWith("plugin")) ({ TextButton(onClick = { vm.clearCcResult(); vm.selectTab(Tab.SESSIONS) }) { Text("Go to Sessions") } }) else null,
        )
    }
}

@Composable
private fun ToolRow(title: String, hint: String, action: String, enabled: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = onClick, enabled = enabled) { OneLine(action) }
    }
}

private fun LazyGridScope.fullItem(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

/** One installed plugin: what it is, and enable / disable / update / uninstall. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PluginSheet(vm: MainViewModel, pl: InstalledPlugin, p: PluginsData?, onClose: () -> Unit) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }
    val desc = p?.available?.firstOrNull { it.id == pl.id }?.description
    val change = { args: String -> onClose(); vm.claudeCommand(args) }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(pl.name, style = MaterialTheme.typography.headlineSmall)
            when (pl.update) {
                "current" -> "Up to date"
                "available" -> "Update available" + if (pl.latest.isNotBlank()) " (latest: ${pl.latest})" else ""
                "error" -> "Couldn't check for updates (marketplace unreachable)"
                else -> null
            }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            desc?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            CardBox {
                life.mygig.clauderc.ui.components.InfoRows(listOf(
                    "Marketplace" to pl.marketplace,
                    "Version" to pl.version.ifBlank { "?" }.take(14),
                    "State" to if (pl.enabled) "enabled" else "disabled",
                    "Scope" to pl.scope,
                ))
            }
            val toggleLabel = if (pl.enabled) "Disable" else "Enable"
            val toggle = { change(if (pl.enabled) "plugin disable ${pl.id}" else "plugin enable ${pl.id}") }
            val viaSkills = pl.via == "skills" && pl.source.isNotBlank()
            val update = { if (viaSkills) { onClose(); vm.updateSkills(pl.source) } else change("plugin update ${pl.id}") }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (pl.update == "available") {
                    Button(onClick = update, enabled = busy == null, modifier = Modifier.weight(1f)) { Text("Update") }
                    OutlinedButton(onClick = toggle, enabled = busy == null, modifier = Modifier.weight(1f)) { Text(toggleLabel) }
                } else {
                    Button(onClick = toggle, enabled = busy == null, modifier = Modifier.weight(1f)) { Text(toggleLabel) }
                    OutlinedButton(onClick = update, enabled = busy == null, modifier = Modifier.weight(1f)) { Text("Update") }
                }
            }
            if (viaSkills) Text("Managed with npx skills add ${pl.source}; Update runs that for you.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { confirm = true }, enabled = busy == null, modifier = Modifier.fillMaxWidth()) { Text("Uninstall ${pl.name}", color = Color(0xFFF09A9D)) }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Uninstall ${pl.name}?") },
            text = { Text("Sessions lose it after their next restart. You can install it again from + Install.") },
            confirmButton = { TextButton(onClick = { confirm = false; change("plugin uninstall ${pl.id}") }) { Text("Uninstall") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

/** Search every plugin in your marketplaces; Add marketplace for the rest. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstallPluginSheet(vm: MainViewModel, p: PluginsData, onClose: () -> Unit, onAddMarketplace: () -> Unit) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var market by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = remember(p, query, market) {
        p.available.asSequence()
            .filter { market == null || it.marketplace == market }
            .filter { query.isBlank() || it.name.contains(query.trim(), true) || it.description.contains(query.trim(), true) }
            .sortedWith(compareBy({ it.installed }, { -it.installs }))
            .take(150).toList()
    }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight(0.92f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Install a plugin", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search your marketplaces") }, leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                shape = RoundedCornerShape(24.dp), keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = market == null, onClick = { market = null }, label = { Text("All") }) }
                items(p.marketplaces.map { it.name }.sorted()) { m -> FilterChip(selected = market == m, onClick = { market = m }, label = { Text(m) }) }
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.id }) { a ->
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(a.name, fontWeight = FontWeight.SemiBold)
                                Text(a.description.ifBlank { a.marketplace }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            if (a.installed) {
                                Text("Installed", color = Color(0xFF7FD4A8), style = MaterialTheme.typography.bodySmall)
                            } else {
                                Button(onClick = { onClose(); vm.claudeCommand("plugin install ${a.id}") }, enabled = busy == null) { Text("Install") }
                            }
                        }
                    }
                }
                item {
                    CardBox {
                        Column {
                            Text("Not listed?", fontWeight = FontWeight.SemiBold)
                            Text("Add another marketplace (GitHub owner/repo or URL), then its plugins show here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = onAddMarketplace) { Text("+ Add marketplace") }
                        }
                    }
                }
                item { Text("", Modifier.padding(bottom = 24.dp)) }
            }
        }
    }
}
