package life.mygig.clauderc.ui.screens

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import life.mygig.clauderc.ui.components.SectionLabel

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
import life.mygig.clauderc.ui.MainViewModel
import life.mygig.clauderc.ui.openInClaude
import life.mygig.clauderc.ui.openUrl

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun NewProjectScreen(vm: MainViewModel) {
    val owners by vm.owners.collectAsState()
    val ownersError by vm.ownersError.collectAsState()
    val busy by vm.busy.collectAsState()
    val result by vm.newResult.collectAsState()
    val context = LocalContext.current

    var name by rememberSaveable { mutableStateOf("") }
    var owner by rememberSaveable { mutableStateOf("") }
    var private by rememberSaveable { mutableStateOf(true) }
    var start by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(Unit) { vm.loadOwners() }
    LaunchedEffect(owners) {
        if (owner.isEmpty()) owners?.let { owner = it.defaultOwner ?: it.user }
    }

    val nameError = when {
        name.isEmpty() -> null
        !PROJECT_NAME_RE.matches(name) -> "Use letters, digits, '.', '_' or '-' (not first), max 100"
        name == "." || name == ".." -> "Not a valid name"
        else -> null
    }
    val canCreate = name.isNotEmpty() && nameError == null && owner.isNotEmpty() && busy == null

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionLabel("New project")
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
        Text("Owner", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when {
            owners != null -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEach { c ->
                    FilterChip(selected = owner == c, onClick = { owner = c }, label = { Text(if (c == owners?.user) "$c (you)" else c) })
                }
            }
            ownersError != null -> {
                Text(ownersError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { vm.loadOwners() }) { Text("Retry loading owners") }
            }
            else -> Text("Loading owners…", style = MaterialTheme.typography.bodySmall)
        }

        Text("Visibility", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = private, onClick = { private = true }, label = { Text("Private") })
            FilterChip(selected = !private, onClick = { private = false }, label = { Text("Public") })
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Start Claude now", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = start, onCheckedChange = { start = it })
        }

        Button(
            onClick = { vm.createProject(name, owner, private, start) },
            enabled = canCreate,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Create project") }

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
