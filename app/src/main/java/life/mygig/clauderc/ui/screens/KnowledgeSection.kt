package life.mygig.clauderc.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import life.mygig.clauderc.api.KnowledgePack
import life.mygig.clauderc.ui.MainViewModel
import life.mygig.clauderc.ui.components.CardBox
import life.mygig.clauderc.ui.openUrl

/** Developer knowledge pack: what is installed for every session, plus the monthly refresh (research + fact-check, ends in a pull request). */
@Composable
fun KnowledgeSection(vm: MainViewModel, pack: KnowledgePack, canRefresh: Boolean) {
    val refresh by vm.knowledgeRefresh.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(canRefresh) { if (canRefresh) vm.loadKnowledgeRefresh() }
    CardBox {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${pack.skills} skills for every Claude session and worker", style = MaterialTheme.typography.titleSmall)
            Text(
                "Current standards and what changed lately for web, PHP, JavaScript/TypeScript, Node, Python, Go, Java, Android, iOS and UI/UX. " +
                    "Version ${pack.version}. Update now refreshes it; restart sessions to load it.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val r = refresh
            if (canRefresh && r != null && r.installed) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Refresh every month", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "On the 1st, Claude researches and fact-checks every skill and opens a pull request. Nothing changes until it is merged. Needs 'Run a command' on.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = r.schedule == "monthly", onCheckedChange = { vm.setKnowledgeSchedule(it) }, enabled = busy == null)
                }
                OutlinedButton(onClick = { vm.startKnowledgeRefresh() }, enabled = busy == null && !r.running, modifier = Modifier.fillMaxWidth()) {
                    Text(if (r.running) "Refreshing…" else "Refresh now")
                }
                r.last?.let { last ->
                    val text = when (last.state) {
                        "running" -> "Running: ${last.message}"
                        "done" -> last.message
                        else -> "Last refresh failed: ${last.message}"
                    }
                    Text(
                        text, style = MaterialTheme.typography.bodySmall,
                        color = if (last.state == "failed") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    last.pr?.let { url -> OutlinedButton(onClick = { openUrl(context, url) }, modifier = Modifier.fillMaxWidth()) { Text("Open the pull request") } }
                }
            }
        }
    }
}
