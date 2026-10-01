package life.mygig.clauderc.ui.screens

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import life.mygig.clauderc.ui.components.AttachFile
import life.mygig.clauderc.ui.components.Mic
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.material3.LocalContentColor
import androidx.core.content.ContextCompat
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.DisposableEffect
import java.util.Locale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextDecoration
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
import life.mygig.clauderc.ui.components.claudeHealth
import life.mygig.clauderc.ui.theme.Term
import life.mygig.clauderc.ui.theme.WarnAmber

/** Chat with a Claude session inside the app. Behind App lock and the chat PIN. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(vm: MainViewModel, session: String) {
    val pinNeeded by vm.chatPinNeeded.collectAsState()
    val chat by vm.chat.collectAsState()
    val error by vm.chatError.collectAsState()
    val busy by vm.busy.collectAsState()
    val pending by vm.chatPending.collectAsState()
    val context = LocalContext.current
    var speak by rememberSaveable { mutableStateOf(false) }
    val voice = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(speak) {
        var engine: TextToSpeech? = null
        if (speak) engine = TextToSpeech(context) { st ->
            if (st == TextToSpeech.SUCCESS) { engine?.language = Locale.getDefault(); voice.value = engine }
            else { speak = false; vm.say("No text-to-speech voice on this phone.") }
        }
        onDispose { engine?.stop(); engine?.shutdown(); voice.value = null }
    }
    // Read Claude's new replies aloud (not the ones already there when it was turned on).
    val spoken = remember(speak) { mutableSetOf<String>() }
    var primed by remember(speak) { mutableStateOf(false) }
    LaunchedEffect(chat?.messages, voice.value) {
        val e = voice.value ?: return@LaunchedEffect
        if (chat == null) return@LaunchedEffect   // still loading: wait, so old replies aren't read out
        val replies = chat?.messages.orEmpty().filter { it.role == "assistant" }.map { (it.id + it.text.hashCode()) to it.text }
        if (!primed) { spoken += replies.map { it.first }; primed = true; return@LaunchedEffect }
        replies.filter { it.first !in spoken }.forEach { (k, t) -> spoken += k; e.speak(forSpeech(t), TextToSpeech.QUEUE_ADD, null, k) }
    }
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
                        FlowRow(itemVerticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val health = claudeHealth(c?.busy == true, c?.waiting == true)
                            StatusDot(if (c == null) Health.OFF else health)
                            Text(
                                when { pinNeeded != null -> "locked"; c == null -> "loading…"; c.waiting -> "needs an answer"; c.busy -> "working…"; else -> "idle" },
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (c != null && pinNeeded == null) {
                                // Claude's model; tap to pick another (/model).
                                var pickModel by remember { mutableStateOf(false) }
                                Box {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = busy == null) { pickModel = true },
                                    ) {
                                        Text(
                                            modelName(c.model) + " ▾", style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), maxLines = 1,
                                        )
                                    }
                                    DropdownMenu(expanded = pickModel, onDismissRequest = { pickModel = false }) {
                                        listOf("opus" to "Opus", "sonnet" to "Sonnet", "haiku" to "Haiku", "opusplan" to "Opus plans, Sonnet builds").forEach { (alias, label) ->
                                            DropdownMenuItem(text = { Text(label) }, onClick = { pickModel = false; vm.chatCommand("/model $alias") })
                                        }
                                    }
                                }
                                // Claude's permission mode; tap to cycle it (Shift+Tab).
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (c.mode == "bypass") MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = busy == null) { vm.chatKey("BTab") },
                                ) {
                                    Text(
                                        when (c.mode) { "auto" -> "Auto"; "plan" -> "Plan"; "edits" -> "Accept edits"; "bypass" -> "Bypass"; else -> "Ask first" } + " ⇄",
                                        style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                    TextButton(onClick = { speak = !speak }) { Text(if (speak) "🔊" else "🔈", fontSize = 20.sp) }
                    Icon(Icons.Filled.Lock, contentDescription = "PIN-protected", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val notice by vm.chatNotice.collectAsState()
                notice?.let { n ->
                    Surface(color = MaterialTheme.colorScheme.inverseSurface, modifier = Modifier.fillMaxWidth().clickable { vm.clearChatNotice() }) {
                        Text(n, color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                    }
                }
                val need = pinNeeded
                if (need != null) {
                    PinGate(vm, need, error)
                } else {
                    Messages(vm, chat?.messages.orEmpty(), pending, Modifier.weight(1f))
                    chat?.takeIf { it.waiting }?.let { c -> c.ask?.takeIf { it.isNotEmpty() }?.let { AskCard(vm, it, busy == null) } ?: PromptCard(vm, c.screen.orEmpty(), busy == null) }
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
private fun Messages(vm: MainViewModel, messages: List<ChatMessage>, pending: List<String>, modifier: Modifier) {
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
                is Row0.Msg -> if (r.m.role == "file") {
                    FileCard(vm, r.m)
                } else if (r.m.role == "user") {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                        Surface(shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.widthIn(max = 320.dp)) {
                            SelectionContainer { Text(linkify(r.m.text), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium) }
                        }
                        if (r.pending) Text("queued · Claude reads it at its next pause", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Surface(shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.widthIn(max = 340.dp)) {
                        SelectionContainer { Text(linkify(r.m.text), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
    }
}

/** claude-opus-5-5 -> Opus 5.5; empty until Claude has replied. */
private fun modelName(id: String): String {
    val parts = id.removePrefix("claude-").split("-").filter { it.isNotEmpty() && !(it.length >= 8 && it.all(Char::isDigit)) }
    val name = parts.firstOrNull { it.all(Char::isLetter) } ?: return "Model"
    val version = parts.filter { it.all(Char::isDigit) }.joinToString(".")
    return name.replaceFirstChar { it.uppercase() } + if (version.isEmpty()) "" else " $version"
}

/** Reply text made listenable: no code blocks, links or markdown marks. */
private fun forSpeech(t: String) = t
    .replace(Regex("```[\\s\\S]*?```"), " (code) ")
    .replace(URL_RE, " link ")
    .replace(Regex("[*#_`>|]"), "")
    .take(1500)

private val URL_RE = Regex("""https?://[^\s<>"')\]]+""")

/** The text with its web addresses tappable (opens the browser). */
@Composable
fun linkify(text: String): AnnotatedString {
    val style = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))
    return remember(text, style) {
        buildAnnotatedString {
            var at = 0
            for (m in URL_RE.findAll(text)) {
                // Sentence punctuation right after a link isn't part of it.
                val url = m.value.trimEnd('.', ',', ';', ':', '!', '?')
                append(text.substring(at, m.range.first))
                withLink(LinkAnnotation.Url(url, style)) { append(url) }
                at = m.range.first + url.length
            }
            append(text.substring(at))
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
    val dictate = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val said = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (r.resultCode == Activity.RESULT_OK && !said.isNullOrBlank()) text = (text.trimEnd() + " " + said).trim()
    }
    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = { vm.externalScreen(); pick.launch(arrayOf("*/*")) }, enabled = enabled, modifier = Modifier.height(52.dp)) {
            Icon(Icons.Filled.AttachFile, contentDescription = "Attach a file or photo")
        }
        // Listen inside the app (no Google pop-up); the system dialog is the fallback.
        var listening by remember { mutableStateOf(false) }
        val recognizer = remember { if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null }
        DisposableEffect(recognizer) { onDispose { recognizer?.destroy() } }
        val intent = remember {
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say your message")
        }
        DisposableEffect(recognizer) {
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    listening = false
                    val said = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!said.isNullOrBlank()) text = (text.trimEnd() + " " + said).trim()
                }
                override fun onError(error: Int) {
                    listening = false
                    if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT && error != SpeechRecognizer.ERROR_CLIENT) {
                        vm.say("Couldn't hear that (speech error $error).")
                    }
                }
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            onDispose {}
        }
        val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (ok && recognizer != null) { listening = true; recognizer.startListening(intent) }
            else if (!ok) vm.say("Allow the microphone for cLaudeRC in the phone's settings to dictate.")
        }
        IconButton(
            onClick = {
                if (recognizer != null) {
                    if (listening) { recognizer.stopListening(); return@IconButton }
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        listening = true; recognizer.startListening(intent)
                    } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    return@IconButton
                }
                try {
                    vm.externalScreen()
                    dictate.launch(
                        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say your message"),
                    )
                } catch (e: ActivityNotFoundException) {
                    vm.say("This phone has no speech recognizer. Turn on Google voice typing in the phone's settings.")
                }
            },
            enabled = enabled, modifier = Modifier.height(52.dp),
        ) { Icon(Icons.Filled.Mic, contentDescription = if (listening) "Stop listening" else "Speak your message", tint = if (listening) MaterialTheme.colorScheme.error else LocalContentColor.current) }
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
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PinGate(vm: MainViewModel, status: PinStatus, error: String?) {
    var pin by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var keep by rememberSaveable { mutableStateOf(0) }
    val setup = !status.set
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.padding(top = 24.dp).height(48.dp))
        Text(if (setup) "Create a chat PIN" else "Enter your chat PIN", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (setup) "6 to 12 digits. It's kept only on your server, in ~/.config/claude-launcher/chat-pin, which is where you can find it if you forget it. This app never shows it."
            else "Asked when you open a chat or come back to the app. Forgot it? It's in ~/.config/claude-launcher/chat-pin on your server.",
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
        if (!setup) {
            Text("Don't ask again for", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Ask always", 30 to "30 min", 60 to "1 h", 240 to "4 h").forEach { (m, label) ->
                    FilterChip(selected = keep == m, onClick = { keep = m }, label = { Text(label) })
                }
            }
            if (keep > 0) {
                Text("Restarting the phone asks again. Until then the PIN is kept encrypted on this phone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Button(
            onClick = {
                val p = pin
                if (setup) vm.setChatPin(p) else vm.unlockChat(p, keep)
                pin = ""; again = ""
            },
            enabled = pin.length >= 6 && (!setup || again == pin),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(if (setup) "Create PIN and open chat" else "Unlock chat") }
    }
}
