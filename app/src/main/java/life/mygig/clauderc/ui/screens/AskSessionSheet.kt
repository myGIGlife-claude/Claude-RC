package life.mygig.clauderc.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import life.mygig.clauderc.api.Session
import life.mygig.clauderc.ui.MainViewModel
import life.mygig.clauderc.ui.components.CalmDivider
import life.mygig.clauderc.ui.components.CalmGroup
import life.mygig.clauderc.ui.components.SectionLabel
import life.mygig.clauderc.ui.components.StatusDot
import life.mygig.clauderc.ui.components.claudeHealth

/** "Ask…" in a chat: pick another running session, type a question; Claude in this chat relays it (no server action). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskSessionSheet(vm: MainViewModel, onClose: () -> Unit) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val current = vm.chats.chatSession.collectAsStateWithLifecycle().value
    LaunchedEffect(Unit) { vm.refreshSessions() }
    var target by remember { mutableStateOf<String?>(null) }
    var question by remember { mutableStateOf("") }
    val others = sessions.filter { it.name != current }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Ask another session", style = MaterialTheme.typography.titleLarge)
            val to = target
            if (to == null) {
                Text(
                    "Claude in this chat passes your question to the session you pick and shows you its reply.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (others.isEmpty()) Text("No other running sessions.", style = MaterialTheme.typography.bodyMedium)
                SessionGroup("Sessions", others.filterNot { it.general }) { target = it.name }
                SessionGroup("Chats", others.filter { it.general }) { target = it.name }
            } else {
                Text("To: $to", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = question, onValueChange = { question = it }, label = { Text("Your question") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = { target = null }) { Text("Back") }
                    Button(onClick = { vm.chats.askSession(to, question, onSent = onClose) }, enabled = question.isNotBlank()) { Text("Send") }
                }
            }
        }
    }
}

@Composable
private fun SessionGroup(title: String, items: List<Session>, onPick: (Session) -> Unit) {
    if (items.isEmpty()) return
    SectionLabel(title)
    CalmGroup {
        items.forEachIndexed { i, s ->
            if (i > 0) CalmDivider()
            Row(
                Modifier.fillMaxWidth().clickable(onClickLabel = "Ask ${s.name}", role = Role.Button) { onPick(s) }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatusDot(claudeHealth(s.busy, s.waiting))
                Text(s.name, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
