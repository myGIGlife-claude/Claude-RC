package life.mygig.clauderc.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import life.mygig.clauderc.data.widgetStore

/** The one place the widget snapshot is read and written; every write redraws the widgets. */
object WidgetStore {
    private val KEY = stringPreferencesKey("snapshot")

    suspend fun read(ctx: Context): WidgetSnapshot = decodeSnapshot(ctx.widgetStore.data.first()[KEY])

    suspend fun update(ctx: Context, change: (WidgetSnapshot) -> WidgetSnapshot) {
        ctx.widgetStore.edit { it[KEY] = change(decodeSnapshot(it[KEY])).encode() }
        refreshWidgets(ctx)
    }
}
