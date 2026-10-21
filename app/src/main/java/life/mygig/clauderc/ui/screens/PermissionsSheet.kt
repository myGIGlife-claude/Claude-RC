package life.mygig.clauderc.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import life.mygig.clauderc.api.PermScope
import life.mygig.clauderc.api.describePermissionRule
import life.mygig.clauderc.api.isValidPermissionRule
import life.mygig.clauderc.api.permissionChange
import life.mygig.clauderc.api.permissionRule
import life.mygig.clauderc.ui.MainViewModel
import life.mygig.clauderc.ui.components.CalmDivider
import life.mygig.clauderc.ui.components.CalmGroup
import life.mygig.clauderc.ui.components.CardBox
import life.mygig.clauderc.ui.components.SectionLabel
import life.mygig.clauderc.ui.components.PillTabs

private val PERM_SCOPES = listOf("project" to "This project", "local" to "Just me here", "user" to "All projects")
private val PERM_LISTS = listOf("allow" to "Allowed", "ask" to "Ask first", "deny" to "Blocked")
private val PERM_TOOLS = listOf("Bash", "Read", "Edit", "Write", "WebFetch", "WebSearch")
private val PERM_QUICK = listOf("Bash(git:*)", "Bash(ls:*)", "Read", "Edit", "WebSearch", "WebFetch")

/** What the open chat's Permissions button shows: Claude's allow / ask / block rules, edited by tapping instead of in the terminal. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PermissionsSheet(vm: MainViewModel, onClose: () -> Unit) {
    val perms by vm.chats.permissions.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.chats.loadPermissions() }
    var tab by rememberSaveable { mutableStateOf(0) }
    var adding by remember { mutableStateOf<String?>(null) }          // the list an Add dialog is for
    var picked by remember { mutableStateOf<Pair<String, String>?>(null) }  // (list, rule) tapped
    var editing by remember { mutableStateOf<Pair<String, String>?>(null) }
    var deleting by remember { mutableStateOf<Pair<String, String>?>(null) }
    val scopeKey = PERM_SCOPES[tab].first
    val scope: PermScope = perms?.scopes?.get(scopeKey) ?: PermScope()
    fun change(op: String, list: String = "", rule: String = "", new: String = "", mode: String = "") =
        vm.chats.changePermissions(permissionChange(scopeKey, op, list, rule, new, mode))

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = { vm.chats.clearPermissions(); onClose() },
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Permissions", style = MaterialTheme.typography.titleLarge)
            Text(
                "What Claude may do without asking. Pick where a rule applies: this project (shared in its .claude folder), just you in this project, or every project. " +
                    "Changes apply to Claude's next action; if one doesn't seem to, restart the session.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PillTabs(PERM_SCOPES.map { it.second }, tab, { tab = it })
            if (perms == null) {
                Text("Loading…", style = MaterialTheme.typography.bodyMedium)
            } else if (scope.invalid) {
                Text(
                    "This settings file isn't valid JSON, so it can't be edited here. Fix it on the server first.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
                )
            } else {
                Text("Default mode here", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("" to "Not set", "default" to "Ask first", "acceptEdits" to "Accept edits", "plan" to "Plan").forEach { (m, label) ->
                        FilterChip(selected = scope.mode == m, onClick = { if (scope.mode != m) change("mode", mode = m) }, label = { Text(label) })
                    }
                }
                PERM_LISTS.forEach { (key, title) ->
                    val rules = scope.list(key)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        SectionLabel(title)
                        TextButton(onClick = { adding = key }, modifier = Modifier.semantics { contentDescription = "Add a rule to $title" }) { Text("Add rule") }
                    }
                    if (rules.isEmpty()) {
                        Text("None.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
                    } else CalmGroup {
                        rules.forEachIndexed { i, r ->
                            if (i > 0) CalmDivider()
                            Column(Modifier.fillMaxWidth().clickable { picked = key to r }.padding(horizontal = 16.dp, vertical = 12.dp).semantics { contentDescription = "$title rule $r. Tap to change." }) {
                                Text(r, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                                Text(describePermissionRule(r), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    adding?.let { list ->
        PermissionRuleDialog(
            title = "Add to " + PERM_LISTS.first { it.first == list }.second, initial = "", confirm = "Add", quick = true,
            onDismiss = { adding = null }, onConfirm = { r -> adding = null; change("add", list, r) },
        )
    }
    picked?.let { (list, rule) ->
        AlertDialog(
            onDismissRequest = { picked = null },
            title = { Text(rule, fontFamily = FontFamily.Monospace) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(onClick = { picked = null; editing = list to rule }) { Text("Edit rule") }
                    PERM_LISTS.filter { it.first != list }.forEach { (k, t) ->
                        TextButton(onClick = { picked = null; change("move", list, rule, new = k) }) { Text("Move to $t") }
                    }
                    TextButton(onClick = { picked = null; deleting = list to rule }) { Text("Delete rule", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = { picked = null }) { Text("Cancel") } },
        )
    }
    editing?.let { (list, rule) ->
        PermissionRuleDialog(
            title = "Edit rule", initial = rule, confirm = "Save", quick = false,
            onDismiss = { editing = null }, onConfirm = { r -> editing = null; if (r != rule) change("edit", list, rule, new = r) },
        )
    }
    deleting?.let { (list, rule) ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this rule?") },
            text = { Text(rule, fontFamily = FontFamily.Monospace) },
            confirmButton = { TextButton(onClick = { deleting = null; change("remove", list, rule) }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

/** Add or edit one rule: a tool (Bash, Read, …) and, optionally, what it applies to. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PermissionRuleDialog(title: String, initial: String, confirm: String, quick: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var tool by remember { mutableStateOf(initial.substringBefore('(')) }
    var spec by remember { mutableStateOf(if (initial.contains('(')) initial.substringAfter('(').removeSuffix(")") else "") }
    val rule = permissionRule(tool, spec)
    val ok = isValidPermissionRule(rule)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PERM_TOOLS.forEach { t -> FilterChip(selected = tool == t, onClick = { tool = t }, label = { Text(t) }) }
                }
                OutlinedTextField(value = tool, onValueChange = { tool = it.trim() }, label = { Text("Tool") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = spec, onValueChange = { spec = it }, label = { Text("Applies to (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Examples: git status:*  ·  src/**  ·  domain:example.com. Blank means every use of the tool.") },
                )
                Text(if (ok) rule else "Not a valid rule yet.", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                    color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                if (quick) {
                    Text("Quick add", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PERM_QUICK.forEach { q ->
                            AssistChip(onClick = { tool = q.substringBefore('('); spec = if (q.contains('(')) q.substringAfter('(').removeSuffix(")") else "" }, label = { Text(q) })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(rule) }, enabled = ok) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
