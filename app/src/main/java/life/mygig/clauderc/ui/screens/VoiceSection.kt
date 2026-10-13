package life.mygig.clauderc.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import life.mygig.clauderc.ui.MainViewModel
import life.mygig.clauderc.ui.components.CardBox
import life.mygig.clauderc.voice.NATURAL_DEFAULT_SID
import life.mygig.clauderc.voice.NATURAL_VOICES
import life.mygig.clauderc.voice.NaturalDownload
import life.mygig.clauderc.voice.NaturalVoice

/** Settings › App › Voice: download the natural voice once, switch it on, pick which one and hear it. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun VoiceSection(vm: MainViewModel) {
    val context = LocalContext.current
    val prefs = remember { vm.chats.voicePrefs }
    val installed by NaturalVoice.installed.collectAsStateWithLifecycle()
    val download by NaturalVoice.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { NaturalVoice.refresh(context) }
    var on by remember { mutableStateOf(prefs.getBoolean("natural", false)) }
    var sid by remember { mutableIntStateOf(prefs.getInt("sid", NATURAL_DEFAULT_SID)) }
    var confirmDelete by remember { mutableStateOf(false) }
    val voice = remember { ChatVoice(context, prefs) { vm.say(it) } }
    DisposableEffect(voice) { onDispose { voice.shutdown() } }

    CardBox {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Natural voice")
            Text(
                "Reads Claude's replies in a natural-sounding voice (Kokoro). It runs on your phone: free, works offline, nothing is sent anywhere. " +
                    "One-time download of about 333 MB (use Wi-Fi); it takes about 400 MB on the phone. The phone's own voice is used until it's ready.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                download is NaturalDownload.Running -> {
                    val pct = (download as NaturalDownload.Running).percent
                    LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("Downloading… $pct%", style = MaterialTheme.typography.bodySmall)
                }
                installed == true -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Use the natural voice", Modifier.weight(1f))
                        Switch(checked = on, onCheckedChange = { on = it; prefs.edit().putBoolean("natural", it).apply(); if (!it) voice.pause() })
                    }
                    Text("Voice", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NATURAL_VOICES.forEach { v ->
                            FilterChip(selected = sid == v.sid, onClick = { sid = v.sid; prefs.edit().putInt("sid", v.sid).apply() }, label = { Text(v.name) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            prefs.edit().putBoolean("natural", true).apply(); on = true
                            voice.speak("preview", "Hi Jonathan, this is how I sound. Pick the voice you like best.", flush = true, fromStart = true)
                        }) { Text(if (voice.playing == "preview") "Playing…" else "Hear it") }
                        OutlinedButton(onClick = { confirmDelete = true }) { Text("Delete download") }
                    }
                    if (voice.note.isNotEmpty()) Text(voice.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    if (download is NaturalDownload.Failed) Text((download as NaturalDownload.Failed).message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { NaturalVoice.download(context) }, enabled = installed != null) { Text("Download the natural voice") }
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete the natural voice?") },
            text = { Text("It frees about 400 MB. You can download it again any time.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; voice.pause(); NaturalVoice.delete(context) }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
