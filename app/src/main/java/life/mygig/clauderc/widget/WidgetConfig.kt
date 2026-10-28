package life.mygig.clauderc.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import life.mygig.clauderc.data.widgetStore

/** What one placed widget shows. Chosen in WidgetConfigActivity (on adding it, and later by long-press > reconfigure). */
@Serializable
data class WidgetConfig(
    val kind: String = KIND_SESSIONS,
    /** Sessions: ALL, ACTIVE (waiting + working) or PICKED (only [sessions]). */
    val filter: String = FILTER_ALL,
    /** Sessions: tmux names for PICKED. */
    val sessions: List<String> = emptyList(),
    val maxRows: Int = 8,
    /** Quick actions: WidgetLink.OPEN_* values, in order (max [MAX_ACTIONS]). */
    val actions: List<String> = DEFAULT_ACTIONS,
    /** Usage: which account names to show; empty = all Claude accounts. */
    val accounts: List<String> = emptyList(),
    val showWeekly: Boolean = true,
) {
    companion object {
        const val KIND_SESSIONS = "sessions"
        const val KIND_QUICK = "quick"
        const val KIND_USAGE = "usage"
        const val FILTER_ALL = "all"
        const val FILTER_ACTIVE = "active"
        const val FILTER_PICKED = "picked"
        const val MAX_ACTIONS = 6
        val DEFAULT_ACTIONS = listOf(WidgetLink.OPEN_NEW_REPO, WidgetLink.OPEN_KEYS, WidgetLink.OPEN_NEW_CHAT, WidgetLink.OPEN_SESSIONS)
    }
}

/** Per-widget settings, keyed by appWidgetId, in the same DataStore as the snapshot. */
object WidgetConfigs {
    private fun key(id: Int) = stringPreferencesKey("config_$id")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** The saved settings, or [default] (what a freshly added widget of that kind shows) if none or unreadable. */
    suspend fun get(ctx: Context, id: Int, default: WidgetConfig): WidgetConfig =
        ctx.widgetStore.data.first()[key(id)]?.let { runCatching { json.decodeFromString<WidgetConfig>(it) }.getOrNull() } ?: default

    suspend fun set(ctx: Context, id: Int, c: WidgetConfig) {
        ctx.widgetStore.edit { it[key(id)] = json.encodeToString(c) }
        refreshWidgets(ctx)
    }

    /** Called when a widget is removed, so its settings don't pile up. */
    suspend fun remove(ctx: Context, ids: IntArray) = ctx.widgetStore.edit { p -> ids.forEach { p.remove(key(it)) } }.let { }
}

/** Every quick action a widget can offer: its WidgetLink value, label, and a short hint for the picker. */
val QUICK_ACTIONS: List<Triple<String, String, String>> = listOf(
    Triple(WidgetLink.OPEN_NEW_REPO, "New repo", "Start a project"),
    Triple(WidgetLink.OPEN_KEYS, "Add key", "Logins, MCP, API keys"),
    Triple(WidgetLink.OPEN_NEW_CHAT, "New chat", "Talk to Claude, no repo"),
    Triple(WidgetLink.OPEN_SESSIONS, "Sessions", "See what is running"),
    Triple(WidgetLink.OPEN_CLUSTER, "Usage", "Accounts and limits"),
)
