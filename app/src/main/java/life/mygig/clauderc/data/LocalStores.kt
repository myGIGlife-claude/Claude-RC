package life.mygig.clauderc.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/*
 * The small stores that used to be SharedPreferences. Each keeps its old file name, key names and
 * value types, and its SharedPreferencesMigration moves the old values over before the first read.
 * One delegate per file for the whole process: never create a second DataStore for these names.
 */
private fun migratedStore(name: String) = preferencesDataStore(
    name = name,
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
    produceMigrations = { ctx -> listOf(SharedPreferencesMigration(ctx, name)) },
)

/** SessionWatcher's last-seen state ("state") and the build it last told about ("told_build"). */
val Context.sessionWatchStore: DataStore<Preferences> by migratedStore("session_watch")
/** Push: the server's Firebase ids and whether this phone is registered. */
val Context.pushStore: DataStore<Preferences> by migratedStore("push")
/** Half-typed chat messages, keyed by session name. */
val Context.chatDraftsStore: DataStore<Preferences> by migratedStore("chat_drafts")
/** The Add server dialog's draft. */
val Context.hostDraftStore: DataStore<Preferences> by migratedStore("host_draft")
/** The chat's speaker switch ("speak"). */
val Context.chatVoiceStore: DataStore<Preferences> by migratedStore("chat_voice")

/** Lives as long as the process: a write must not be dropped because the screen that made it closed. */
private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
private val cachedStores = HashMap<DataStore<Preferences>, CachedPrefs>()

/** The one [CachedPrefs] of this store (made on first use, which starts loading it). */
fun DataStore<Preferences>.cached(): CachedPrefs = synchronized(cachedStores) { cachedStores.getOrPut(this) { CachedPrefs(this, uiScope) } }

/**
 * A DataStore for UI code that reads on the spot (a dialog's first value): loaded in the
 * background as soon as it is made, and written through, so a read right after a write sees it.
 * Only a read before the first load finishes waits for the disk.
 * Main thread only: the fields aren't locked.
 */
class CachedPrefs internal constructor(private val store: DataStore<Preferences>, scope: CoroutineScope) {
    private var snap: Preferences? = null
    /** Writes not on disk yet: until they are, an older value read from disk must not replace [snap]. */
    private var pending = 0
    /** One writer, in order: the last draft typed is the one that stays. */
    private val writes = Channel<(MutablePreferences) -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch { store.data.collect { if (pending == 0) snap = it } }
        scope.launch {
            for (change in writes) {
                try { store.edit { change(it) } } catch (e: IOException) { } finally { pending-- }
            }
        }
    }

    fun get(): Preferences = snap ?: runBlocking { store.data.first() }.also { snap = it }

    /** [change] runs twice (on the cached copy and in DataStore), so it must only set or remove values. */
    fun edit(change: (MutablePreferences) -> Unit) {
        snap = get().toMutablePreferences().also(change)
        pending++
        writes.trySend(change)
    }

    // SharedPreferences-style calls, so the chat's speaker switch reads as it did.
    fun getBoolean(key: String, default: Boolean): Boolean = get()[booleanPreferencesKey(key)] ?: default
    fun edit() = Editor()
    inner class Editor {
        private val values = mutableMapOf<String, Boolean>()
        fun putBoolean(key: String, value: Boolean) = also { values[key] = value }
        fun apply() {
            val v = values.toMap()
            edit { p -> v.forEach { (k, b) -> p[booleanPreferencesKey(k)] = b } }
        }
    }
}
