package life.mygig.clauderc.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import life.mygig.clauderc.api.AskQuestion
import life.mygig.clauderc.ui.MainViewModel
import life.mygig.clauderc.ui.theme.WarnAmber

/** Claude's multiple-choice question as tappable options; answering drives the terminal screen for you. */
@Composable
fun AskCard(vm: MainViewModel, questions: List<AskQuestion>, enabled: Boolean) {
    val picks = remember(questions) { questions.map { mutableStateOf(setOf<Int>()) } }
    val others = remember(questions) { questions.map { mutableStateOf("") } }
    val ready = questions.indices.all { picks[it].value.isNotEmpty() || others[it].value.isNotBlank() }
    Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), shape = RoundedCornerShape(14.dp), color = Color(0xFF2A2418)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Claude is asking", color = WarnAmber, fontWeight = FontWeight.SemiBold)
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                questions.forEachIndexed { qi, q ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (q.header.isNotBlank()) Text(q.header.uppercase(), style = MaterialTheme.typography.labelSmall, color = WarnAmber)
                        Text(q.question, fontWeight = FontWeight.Medium)
                        if (q.multiSelect) Text("Pick any that apply", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        q.options.forEachIndexed { oi, o ->
                            val on = oi in picks[qi].value
                            val toggle = {
                                picks[qi].value = if (q.multiSelect) (if (on) picks[qi].value - oi else picks[qi].value + oi) else setOf(oi)
                                if (!q.multiSelect) others[qi].value = ""
                            }
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(enabled = enabled, onClick = toggle), verticalAlignment = Alignment.CenterVertically) {
                                if (q.multiSelect) Checkbox(on, onCheckedChange = { toggle() }, enabled = enabled) else RadioButton(on, onClick = toggle, enabled = enabled)
                                Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                                    Text(o.label)
                                    if (o.description.isNotBlank()) Text(o.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        // "Type something" (single choice only; 9 is the most the terminal's number keys reach).
                        if (!q.multiSelect && q.options.size < 9) OutlinedTextField(
                            value = others[qi].value,
                            onValueChange = { others[qi].value = it; if (it.isNotBlank()) picks[qi].value = emptySet() },
                            label = { Text("Something else…") }, singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.chatKey("Escape") }, enabled = enabled) { Text("Cancel") }
                Button(
                    onClick = { vm.answerAsk(questions, picks.map { it.value }, others.map { it.value }) },
                    enabled = enabled && ready, modifier = Modifier.weight(1f),
                ) { Text("Send answers") }
            }
        }
    }
}
