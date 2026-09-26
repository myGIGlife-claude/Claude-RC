package life.mygig.clauderc.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import life.mygig.clauderc.api.PROJECT_NAME_RE
import life.mygig.clauderc.ui.LocalGuard
import life.mygig.clauderc.ui.MainViewModel
import life.mygig.clauderc.ui.openInClaude
import life.mygig.clauderc.ui.openUrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProjectScreen(vm: MainViewModel) {
    val owners by vm.owners.collectAsState()
    val busy by vm.busy.collectAsState()
    val result by vm.newResult.collectAsState()
    val guard = LocalGuard.current
    val context = LocalContext.current

    var name by rememberSaveable { mutableStateOf("") }
    var owner by rememberSaveable { mutableStateOf("") }
    var private by rememberSaveable { mutableStateOf(true) }
    var start by rememberSaveable { mutableStateOf(true) }
    var ownerMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.loadOwners() }
    LaunchedEffect(owners) {
        if (owner.isEmpty()) owners?.let { owner = it.defaultOwner ?: it.user }
    }

    val nameError = when {
        name.isEmpty() -> null
        !PROJECT_NAME_RE.matches(name) -> "Use letters, digits, '.', '_' or '-' (max 100)"
        name == "." || name == ".." -> "Not a valid name"
        else -> null
    }
    val canCreate = name.isNotEmpty() && nameError == null && owner.isNotEmpty() && busy == null

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.trim() },
            label = { Text("Project name") },
            isError = nameError != null,
            supportingText = { Text(nameError ?: "Becomes the repo and folder name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        val choices = owners?.let { listOf(it.user) + it.orgs.map { o -> o.login } }.orEmpty()
        ExposedDropdownMenuBox(expanded = ownerMenu, onExpandedChange = { ownerMenu = it }) {
            OutlinedTextField(
                value = owner,
                onValueChange = {},
                readOnly = true,
                label = { Text("Owner") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(ownerMenu) },
                supportingText = { if (owners == null) Text("Loading owners…") },
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = ownerMenu, onDismissRequest = { ownerMenu = false }) {
                choices.forEach { c ->
                    DropdownMenuItem(
                        text = { Text(if (c == owners?.user) "$c (you)" else c) },
                        onClick = { owner = c; ownerMenu = false },
                    )
                }
            }
        }

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = private,
                onClick = { private = true },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text("Private") }
            SegmentedButton(
                selected = !private,
                onClick = { private = false },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text("Public") }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = start, onCheckedChange = { start = it })
            Text("Start Claude now")
        }

        Button(
            onClick = { guard.run("Create $owner/$name") { vm.createProject(name, owner, private, start) } },
            enabled = canCreate,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Create") }

        result?.let { r ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Created ${r.repo}", style = MaterialTheme.typography.titleMedium)
                    Text("Folder: ${r.path}", style = MaterialTheme.typography.bodySmall)
                    r.session?.let { Text("Session: $it", style = MaterialTheme.typography.bodySmall) }
                    TextButton(onClick = { openUrl(context, r.url) }) { Text(r.url) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { openInClaude(context) }) { Text("Open in Claude") }
                        OutlinedButton(onClick = { vm.clearNewResult(); name = "" }) { Text("Done") }
                    }
                }
            }
        }
    }
}
