package life.mygig.clauderc.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import life.mygig.clauderc.data.SettingsStore
import life.mygig.clauderc.data.ThemeMode
import life.mygig.clauderc.ui.components.CalmDivider
import life.mygig.clauderc.ui.components.CalmGroup
import life.mygig.clauderc.ui.components.SectionLabel
import life.mygig.clauderc.ui.theme.ClaudeRcTheme

/**
 * A widget's settings (the launcher opens this when a widget is placed or reconfigured). Choices come from the
 * cached snapshot only, never the network. Backing out keeps the widget with its current or default settings.
 */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val kind = widgetKind(this, id)
        if (kind == null) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        // OK from the start: on Android 11 and older a cancelled first configure would remove the new widget.
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        enableEdgeToEdge()
        val themes = SettingsStore(applicationContext).settings.map { it.theme }
        setContent {
            val theme by themes.collectAsStateWithLifecycle(ThemeMode.SYSTEM)
            ClaudeRcTheme(theme) { WidgetConfigScreen(id, kind, onClose = ::finish) }
        }
    }
}

/** Which of our widgets [appWidgetId] is; null for an id that isn't one of ours. */
private fun widgetKind(ctx: Context, appWidgetId: Int): String? {
    if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return null
    val provider = AppWidgetManager.getInstance(ctx).getAppWidgetInfo(appWidgetId)?.provider ?: return null
    if (provider.packageName != ctx.packageName) return null
    return when (provider.className) {
        SessionsWidgetReceiver::class.java.name -> WidgetKind.SESSIONS
        QuickActionsWidgetReceiver::class.java.name -> WidgetKind.ACTIONS
        UsageWidgetReceiver::class.java.name -> WidgetKind.USAGE
        else -> null
    }
}

@Composable
private fun WidgetConfigScreen(appWidgetId: Int, kind: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var snap by remember { mutableStateOf<WidgetSnapshot?>(null) }
    var config by remember { mutableStateOf(WidgetConfig(kind = kind)) }
    LaunchedEffect(appWidgetId) {
        val saved = WidgetConfigs.get(ctx, appWidgetId).copy(kind = kind)
        // Show the default buttons ticked, so what you see is what the widget shows.
        config = if (kind == WidgetKind.ACTIONS && saved.actions.isEmpty()) saved.copy(actions = DEFAULT_ACTIONS) else saved
        snap = WidgetStore.read(ctx)
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // Wide windows (a fold's inner screen, tablets): one centred column, never a stretched list.
        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Column(modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()) {
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        when (kind) { WidgetKind.SESSIONS -> "Sessions widget"; WidgetKind.ACTIONS -> "Quick actions widget"; else -> "Usage widget" },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp, top = 24.dp),
                    )
                    Text(
                        "The widget fits what you pick to its size, on the cover and the inner screen alike.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                    )
                    val s = snap
                    if (s != null) when (kind) {
                        WidgetKind.SESSIONS -> SessionsSettings(config, s) { config = it }
                        WidgetKind.ACTIONS -> ActionsSettings(config) { config = it }
                        else -> UsageSettings(config, s) { config = it }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onClose) { Text("Cancel") }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { scope.launch { WidgetConfigs.set(ctx, appWidgetId, config); onClose() } }, enabled = snap != null) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

private val FILTERS = listOf(
    SessionFilter.ALL to "All sessions",
    SessionFilter.ACTIVE to "Active only (needs you or working)",
    SessionFilter.PICKED to "Only the ones I pick",
)

@Composable
private fun SessionsSettings(config: WidgetConfig, snap: WidgetSnapshot, onChange: (WidgetConfig) -> Unit) {
    SectionLabel("Show")
    CalmGroup {
        FILTERS.forEachIndexed { i, (value, label) ->
            if (i > 0) CalmDivider()
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .selectable(selected = config.filter == value, role = Role.RadioButton) { onChange(config.copy(filter = value)) }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = config.filter == value, onClick = null)
                Text(label, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
    if (config.filter == SessionFilter.PICKED) {
        SectionLabel("Sessions")
        // Picked sessions that aren't running right now stay in the list, so they can be unticked.
        val known = snap.sessions.forWidget().map { it.name to it.project } +
            config.sessions.filter { n -> snap.sessions.none { it.name == n } }.map { it to it }
        if (known.isEmpty()) Hint("No sessions known yet. Open cLaudeRC once so it can load them.")
        else CheckGroup(known, config.sessions) { onChange(config.copy(sessions = config.sessions.toggled(it))) }
    }
    SectionLabel("Rows at most")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        MAX_ROW_CHOICES.forEach { n ->
            FilterChip(selected = config.rowCap() == n, onClick = { onChange(config.copy(maxRows = n)) }, label = { Text("$n") })
        }
    }
    Hint("Fewer when the widget is small; two columns on a wide one.")
}

@Composable
private fun ActionsSettings(config: WidgetConfig, onChange: (WidgetConfig) -> Unit) {
    val chosen = config.actions.filter { id -> QUICK_ACTIONS.any { it.id == id } }
    SectionLabel("Buttons, in order")
    Hint("Up to $MAX_ACTIONS. With none ticked the widget shows the default four.")
    CalmGroup {
        val rows = chosen.mapNotNull { id -> QUICK_ACTIONS.firstOrNull { it.id == id } } + QUICK_ACTIONS.filter { it.id !in chosen }
        rows.forEachIndexed { i, a ->
            if (i > 0) CalmDivider()
            val on = a.id in chosen
            val at = chosen.indexOf(a.id)
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .toggleable(value = on, enabled = on || chosen.size < MAX_ACTIONS, role = Role.Checkbox) {
                        onChange(config.copy(actions = chosen.toggled(a.id, MAX_ACTIONS)))
                    }
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = on, onCheckedChange = null, enabled = on || chosen.size < MAX_ACTIONS)
                Icon(
                    painterResource(actionIcon(a.id)), contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 12.dp).size(22.dp),
                )
                Text(a.label, modifier = Modifier.padding(start = 12.dp).weight(1f))
                if (on) {
                    IconButton(onClick = { onChange(config.copy(actions = chosen.moved(a.id, -1))) }, enabled = at > 0) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move ${a.label} up")
                    }
                    IconButton(onClick = { onChange(config.copy(actions = chosen.moved(a.id, 1))) }, enabled = at < chosen.size - 1) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move ${a.label} down")
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageSettings(config: WidgetConfig, snap: WidgetSnapshot, onChange: (WidgetConfig) -> Unit) {
    SectionLabel("Accounts")
    val names = (snap.usage.map { it.account } + config.accounts).distinct()
    if (names.isEmpty()) Hint("No accounts known yet. Open the Claude tab in cLaudeRC once so it can load them.")
    else {
        Hint("With none ticked the widget shows all of them.")
        CheckGroup(names.map { it to null }, config.accounts) { onChange(config.copy(accounts = config.accounts.toggled(it))) }
    }
    SectionLabel("Bars")
    CalmGroup {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .toggleable(value = config.showWeekly, role = Role.Switch) { onChange(config.copy(showWeekly = it)) }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Show the 7 day bar")
                Text("When the widget is big enough", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = config.showWeekly, onCheckedChange = null)
        }
    }
}

/** Tickable rows: (value, optional second line); [checked] holds the ticked values. */
@Composable
private fun CheckGroup(items: List<Pair<String, String?>>, checked: List<String>, onToggle: (String) -> Unit) {
    CalmGroup {
        items.forEachIndexed { i, (value, detail) ->
            if (i > 0) CalmDivider()
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .toggleable(value = value in checked, role = Role.Checkbox) { onToggle(value) }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = value in checked, onCheckedChange = null)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(detail ?: value, maxLines = 1)
                    if (detail != null && detail != value) {
                        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
}
