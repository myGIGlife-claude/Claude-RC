package life.mygig.clauderc.ui.screens

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import life.mygig.clauderc.ui.components.AttachFile
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Lock
import life.mygig.clauderc.ui.components.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import life.mygig.clauderc.api.ChatMessage
import life.mygig.clauderc.api.PinStatus
import life.mygig.clauderc.ui.MainViewModel
import life.mygig.clauderc.ui.components.Health
import life.mygig.clauderc.ui.components.StatusDot
import life.mygig.clauderc.ui.theme.Term
import life.mygig.clauderc.ui.theme.WarnAmber
import kotlinx.coroutines.delay

/** Chat with a Claude session inside the app. Behind App lock and the chat PIN. */
@Composable
fun ChatScreen(vm: MainViewModel, session: String) {
    val pinNeeded by vm.chatPinNeeded.collectAsState()
    val chat by vm.chat.collectAsState()
    val error by vm.chatError.collectAsState()
    val busy by vm.busy.collectAsState()
    val pending by vm.chatPending.collectAsState()
    Dialog(onDismissRequest = { vm.closeChat() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { vm.closeChat() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    Column(Modifier.weight(1f)) {
                        Text(session, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        val c = chat
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Timed from when the app first saw it busy, so reopening the chat restarts the clock.
                            val longRun by produceState(false, c?.busy) { value = false; if (c?.busy == true) { delay(5 * 60_000L); value = true } }
                            StatusDot(when { c == null -> Health.OFF; c.waiting || longRun -> Health.BAD; c.busy -> Health.WARN; else -> Health.OK })
                            Text(
                                when { pinNeeded != null -> "locked"; c == null -> "loading…"; c.waiting -> "needs an answer"; c.busy -> "working…"; else -> "idle" },
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Icon(Icons.Filled.Lock, contentDescription = "PIN-protected", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val need = pinNeeded
                if (need != null) {
                    PinGate(vm, need, error)
                } else {
                    Messages(chat?.messages.orEmpty(), pending, Modifier.weight(1f))
                    chat?.takeIf { it.waiting }?.let { c -> PromptCard(vm, c.screen.orEmpty(), busy == null) }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp)) }
                    Composer(vm, working = chat?.busy == true, enabled = busy == null && chat != null)
                }
            }
        }
    }
}

/** What the list shows: a message, or a run of tool steps folded into one line. */
private sealed interface Row0 {
    data class Msg(val m: ChatMessage, val pending: Boolean = false) : Row0
    data class Steps(val key: String, val items: List<ChatMessage>) : Row0
}

private fun rows(messages: List<ChatMessage>, pending: List<String>): List<Row0> {
    val out = mutableListOf<Row0>()
    val run = mutableListOf<ChatMessage>()
    fun flush() { if (run.isNotEmpty()) { out += Row0.Steps(run.first().id, run.toList()); run.clear() } }
    messages.forEach { m -> if (m.role == "tool") run += m else { flush(); out += Row0.Msg(m) } }
    flush()
    pending.forEachIndexed { i, t -> out += Row0.Msg(ChatMessage(id = "pending-$i", role = "user", text = t), pending = true) }
    return out
}

@Composable
private fun Messages(messages: List<ChatMessage>, pending: List<String>, modifier: Modifier) {
    val state = rememberLazyListState()
    val list = remember(messages, pending) { rows(messages, pending) }
    var open by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(list.size) { if (list.isNotEmpty()) state.animateScrollToItem(list.size - 1) }
    LazyColumn(modifier.fillMaxWidth(), state = state, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (list.isEmpty()) item { Text("No messages yet in this conversation.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(list, key = { r -> when (r) { is Row0.Msg -> "m-" + r.m.id + r.m.text.hashCode(); is Row0.Steps -> "s-" + r.key } }) { r ->
            when (r) {
                is Row0.Steps -> {
                    val expanded = r.key in open
                    val names = r.items.map { it.text.substringBefore(":").substringAfterLast("__") }.distinct().take(4).joinToString(", ")
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { open = if (expanded) open - r.key else open + r.key }.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            (if (expanded) "▾ " else "› ") + "${r.items.size} step" + (if (r.items.size == 1) "" else "s") + ": $names",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                        )
                        if (expanded) r.items.forEach { t ->
                            Text(t.text, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
                is Row0.Msg -> if (r.m.role == "user") {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                        Surface(shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.widthIn(max = 320.dp)) {
                            SelectionContainer { Text(r.m.text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium) }
                        }
                        if (r.pending) Text("queued · Claude reads it at its next pause", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Surface(shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.widthIn(max = 340.dp)) {
                        SelectionContainer { Text(r.m.text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
    }
}

/** Claude is asking something (permission, MCP approval): its screen and the answer keys. */
@Composable
private fun PromptCard(vm: MainViewModel, screen: String, enabled: Boolean) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), shape = RoundedCornerShape(14.dp), color = Color(0xFF2A2418)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Claude is asking", color = WarnAmber, fontWeight = FontWeight.SemiBold)
            Box(Modifier.fillMaxWidth().background(Term.Bg, RoundedCornerShape(10.dp)).padding(10.dp)) {
                Text(screen.lines().takeLast(10).joinToString("\n"), color = Term.Fg, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("1" to "1", "2" to "2", "3" to "3", "Up" to "↑", "Down" to "↓", "Escape" to "Esc").forEach { (k, l) ->
                    OutlinedButton(onClick = { vm.chatKey(k) }, enabled = enabled, contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.weight(1f)) { Text(l, maxLines = 1) }
                }
            }
            Button(onClick = { vm.chatKey("Enter") }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Enter ⏎") }
        }
    }
}

@Composable
private fun Composer(vm: MainViewModel, working: Boolean, enabled: Boolean) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    // Attach: the file goes into the project's uploads/ folder; its path goes in the message.
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "file"
        val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        if (bytes == null || bytes.isEmpty() || bytes.size > 15 * 1024 * 1024) {
            vm.say("That file couldn't be read, or it's bigger than 15 MB.")
        } else {
            vm.uploadToChat(name, bytes) { path -> text = (text.trimEnd() + " [attached: $path]").trim() }
        }
    }
    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = { pick.launch(arrayOf("*/*")) }, enabled = enabled, modifier = Modifier.height(52.dp)) {
            Icon(Icons.Filled.AttachFile, contentDescription = "Attach a file or photo")
        }
        OutlinedTextField(
            value = text, onValueChange = { text = it }, placeholder = { Text("Message Claude") },
            modifier = Modifier.weight(1f), maxLines = 6, shape = RoundedCornerShape(22.dp),
        )
        if (working) {
            FilledIconButton(onClick = { vm.interruptChat() }, modifier = Modifier.height(52.dp)) { Icon(Icons.Filled.Stop, contentDescription = "Stop Claude") }
        }
        FilledIconButton(
            onClick = { val t = text; vm.sendChat(t) { text = "" } },
            enabled = enabled && text.isNotBlank(), modifier = Modifier.height(52.dp),
        ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send") }
    }
}

/** Enter the chat PIN, or create one the first time. The app never stores or shows it. */
@Composable
private fun PinGate(vm: MainViewModel, status: PinStatus, error: String?) {
    var pin by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val setup = !status.set
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.padding(top = 24.dp).height(48.dp))
        Text(if (setup) "Create a chat PIN" else "Enter your chat PIN", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (setup) "6 to 12 digits. It's kept only on your server, in ~/.config/claude-launcher/chat-pin, which is where you can find it if you forget it. This app never shows it."
            else "Asked every time you open a chat or come back to the app. Forgot it? It's in ~/.config/claude-launcher/chat-pin on your server.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val digits = { s: String -> s.filter(Char::isDigit).take(12) }
        OutlinedTextField(
            value = pin, onValueChange = { pin = digits(it) }, label = { Text("PIN") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        )
        if (setup) {
            OutlinedTextField(
                value = again, onValueChange = { again = digits(it) }, label = { Text("PIN again") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                isError = again.isNotEmpty() && again != pin,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Button(
            onClick = {
                val p = pin
                if (setup) vm.setChatPin(p) else vm.unlockChat(p)
                pin = ""; again = ""
            },
            enabled = pin.length >= 6 && (!setup || again == pin),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(if (setup) "Create PIN and open chat" else "Unlock chat") }
    }
}
