package life.mygig.clauderc.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay
import life.mygig.clauderc.api.TailResult
import life.mygig.clauderc.ui.MainViewModel
import life.mygig.clauderc.ui.theme.Term

private fun termText(size: Int) = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = size.sp,
    lineHeight = (size * 1.55f).sp,
    color = Term.Fg,
    shadow = Shadow(color = Term.Fg.copy(alpha = 0.35f), blurRadius = 8f),
)

/** A full-screen, green-on-black window with a back arrow and title. */
@Composable
private fun TerminalWindow(title: String, subtitle: String, onClose: () -> Unit, actions: @Composable () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        DialogKeyboardFix()
        Column(Modifier.fillMaxSize().background(Term.Bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(
                Modifier.fillMaxWidth().background(Term.Panel).padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Term.Head) }
                Column(Modifier.weight(1f)) {
                    Text(title, color = Term.Head, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Text(subtitle, color = Term.Label, fontSize = 12.sp)
                }
                actions()
            }
            content()
        }
    }
}

@Composable
private fun TermButton(label: String, onClick: () -> Unit, enabled: Boolean = true, primary: Boolean = false, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(10.dp),
        border = if (primary) null else BorderStroke(1.dp, Term.Border),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) Term.Fg else Term.Key,
            contentColor = if (primary) Color(0xFF031A06) else Term.Head,
            disabledContainerColor = Term.Key.copy(alpha = 0.5f),
            disabledContentColor = Term.Dim,
        ),
    ) { Text(label, fontFamily = FontFamily.Monospace, fontWeight = if (primary) FontWeight.Bold else FontWeight.Normal) }
}

/** A session's screen, live, with keys to answer its prompts. */
@Composable
fun TailScreen(vm: MainViewModel, tail: TailResult) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val updatedAt by vm.tailUpdatedAt.collectAsStateWithLifecycle()
    val refreshing by vm.tailRefreshing.collectAsStateWithLifecycle()
    var live by remember { mutableStateOf(true) }
    var size by rememberSaveable { mutableIntStateOf(13) }
    val vScroll = rememberScrollState()
    LaunchedEffect(tail.text) { vScroll.scrollTo(vScroll.maxValue) }
    val foreground by vm.foreground.collectAsStateWithLifecycle()
    LaunchedEffect(live, foreground) {
        while (live && foreground) {
            delay(5_000)
            vm.refreshTail(manual = false)
        }
    }
    val sub = when {
        refreshing -> "refreshing…"
        updatedAt != null -> (if (live) "● live · " else "") + "updated " + DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(updatedAt!!))
        else -> ""
    }
    TerminalWindow(tail.session, sub, { vm.closeTail() }, actions = {
        OutlinedButton(onClick = { if (size > 9) size-- }, border = BorderStroke(1.dp, Term.Border)) { Text("A−", color = Term.Head) }
        OutlinedButton(onClick = { if (size < 20) size++ }, border = BorderStroke(1.dp, Term.Border), modifier = Modifier.padding(start = 6.dp)) { Text("A+", color = Term.Head) }
    }) {
        SelectionContainer(Modifier.weight(1f).fillMaxWidth()) {
            Text(
                tail.text.ifBlank { "(no output yet)" },
                style = termText(size),
                modifier = Modifier.fillMaxSize().verticalScroll(vScroll).horizontalScroll(rememberScrollState()).padding(14.dp),
            )
        }
        Column(Modifier.fillMaxWidth().background(Term.Panel).padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ANSWER THE PROMPT", color = Term.Label, fontSize = 11.sp, letterSpacing = 1.sp)
            val keys = listOf("1" to "1", "2" to "2", "3" to "3", "Up" to "↑", "Down" to "↓", "y" to "y", "n" to "n", "Escape" to "Esc")
            LazyVerticalGrid(columns = GridCells.Fixed(5), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(100.dp)) {
                keys.forEach { (k, label) -> item { TermButton(label, { vm.sendKey(k) }, enabled = busy == null) } }
                item(span = { GridItemSpan(2) }) { TermButton("Enter ⏎", { vm.sendKey("Enter") }, enabled = busy == null, primary = true) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = live, onCheckedChange = { live = it }, colors = SwitchDefaults.colors(checkedTrackColor = Term.Dim, checkedThumbColor = Term.Fg))
                Text("  Auto-refresh", color = Term.Label, fontSize = 13.sp, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { vm.refreshTail() }, enabled = !refreshing, border = BorderStroke(1.dp, Term.Border)) { Text("Refresh", color = Term.Head) }
            }
        }
    }
}

/** Run a command: type or paste, optional sudo password; each run's output stays in the scrollback. */
@Composable
fun RunScreen(vm: MainViewModel) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val history by vm.runHistory.collectAsStateWithLifecycle()
    var command by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val vScroll = rememberScrollState()
    LaunchedEffect(history.size) { vScroll.scrollTo(vScroll.maxValue) }
    val st = status
    TerminalWindow("Run a command", "as you · home folder · App lock", { vm.showRun(false) }) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            SelectionContainer {
                Column(Modifier.fillMaxSize().verticalScroll(vScroll).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        st != null && !st.runEnabled -> {
                            Text("Running commands from the phone is off on this server. To allow it, run this there once, then pull to refresh Connections:", style = termText(13).copy(color = Term.Amber))
                            Text("$ $ENABLE_RUN_CMD", style = termText(13).copy(color = Term.Head))
                        }
                        history.isEmpty() -> Text("Output shows here. Commands that wait for keyboard input won't work (except sudo's password prompt).", style = termText(13).copy(color = Term.Dim))
                    }
                    history.forEach { (cmd, r) ->
                        Text("$ $cmd", style = termText(13).copy(color = Term.Head))
                        Text(r.output.ifBlank { "(no output)" }, style = termText(13))
                        Text("── exit ${r.exitCode}", style = termText(12).copy(color = if (r.exitCode == 0) Term.Dim else Color(0xFFF09A9D)))
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(Term.Panel).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Term.Fg, unfocusedTextColor = Term.Fg, focusedBorderColor = Term.Fg, unfocusedBorderColor = Term.Border,
                focusedLabelColor = Term.Label, unfocusedLabelColor = Term.Label, cursorColor = Term.Fg,
                focusedContainerColor = Term.Field, unfocusedContainerColor = Term.Field,
            )
            OutlinedTextField(
                value = command, onValueChange = { command = it }, label = { Text("Command (type or paste)") },
                minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth(), colors = fieldColors,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it }, label = { Text("sudo password (only if it uses sudo)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            )
            TermButton(
                "Run ⏎",
                { vm.runCommand(command, password); command = "" },
                enabled = busy == null && command.isNotBlank() && st != null && st.runEnabled,
                primary = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

const val ENABLE_RUN_CMD = "echo 'ALLOW_RUN=1' >> ~/.config/claude-launcher/config"
