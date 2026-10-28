package life.mygig.clauderc.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import life.mygig.clauderc.data.widgetStore

/*
 * Per-widget settings, chosen in WidgetConfigActivity and stored per appWidgetId as JSON in `widgetStore`
 * ("config_<id>"). Missing or unreadable = the defaults below, so a widget placed without configuring works.
 */

object WidgetKind {
    const val SESSIONS = "sessions"
    const val ACTIONS = "actions"
    const val USAGE = "usage"
}

object SessionFilter {
    const val ALL = "all"
    const val ACTIVE = "active"   // waiting or working
    const val PICKED = "picked"   // only WidgetConfig.sessions
}

@Serializable
data class WidgetConfig(
    val kind: String = "",
    /** Sessions: a SessionFilter value. */
    val filter: String = SessionFilter.ALL,
    /** Sessions: tmux session names for SessionFilter.PICKED. */
    val sessions: List<String> = emptyList(),
    /** Sessions: at most this many rows (fewer when they don't fit). */
    val maxRows: Int = 12,
    /** Quick actions: QuickAction ids in display order; empty = DEFAULT_ACTIONS. */
    val actions: List<String> = emptyList(),
    /** Usage: account names to show; empty = all. */
    val accounts: List<String> = emptyList(),
    /** Usage: show the 7 day bar too. */
    val showWeekly: Boolean = true,
)

/** A Quick actions button: where it opens the app (a WidgetLink.OPEN_* value). */
data class QuickAction(val id: String, val label: String, val open: String)

val QUICK_ACTIONS = listOf(
    QuickAction("new_repo", "New repo", WidgetLink.OPEN_NEW_REPO),
    QuickAction("add_key", "Add key", WidgetLink.OPEN_KEYS),
    QuickAction("new_chat", "New chat", WidgetLink.OPEN_NEW_CHAT),
    QuickAction("sessions", "Sessions", WidgetLink.OPEN_SESSIONS),
    QuickAction("usage", "Usage", WidgetLink.OPEN_CLUSTER),
)

const val MAX_ACTIONS = 6
val DEFAULT_ACTIONS = listOf("new_repo", "add_key", "new_chat", "sessions")
val MAX_ROW_CHOICES = listOf(3, 5, 8, 12)

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
fun WidgetConfig.encode(): String = json.encodeToString(this)
fun decodeConfig(text: String?): WidgetConfig =
    text?.let { runCatching { json.decodeFromString<WidgetConfig>(it) }.getOrNull() } ?: WidgetConfig()

object WidgetConfigs {
    private fun key(appWidgetId: Int) = stringPreferencesKey("config_$appWidgetId")

    suspend fun get(ctx: Context, appWidgetId: Int): WidgetConfig = decodeConfig(ctx.widgetStore.data.first()[key(appWidgetId)])

    suspend fun set(ctx: Context, appWidgetId: Int, config: WidgetConfig) {
        ctx.widgetStore.edit { it[key(appWidgetId)] = config.encode() }
        refreshWidgets(ctx)
    }

    /** Forget removed widgets' settings. */
    suspend fun remove(ctx: Context, appWidgetIds: IntArray) {
        ctx.widgetStore.edit { p -> appWidgetIds.forEach { p.remove(key(it)) } }
    }
}
