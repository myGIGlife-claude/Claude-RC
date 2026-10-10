package life.mygig.clauderc

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import life.mygig.clauderc.data.CachedPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CachedPrefsTest {
    /** In memory; a write lands a moment later, like the disk. */
    private class FakeStore(start: Preferences = emptyPreferences()) : DataStore<Preferences> {
        val state = MutableStateFlow(start)
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            yield()
            return transform(state.value).also { state.value = it }
        }
    }

    private val k = stringPreferencesKey("s1")

    @Test fun readsSeeWritesAtOnceAndTheLastWriteStays() = runBlocking {
        val store = FakeStore()
        val scope = CoroutineScope(coroutineContext + Job())
        val prefs = CachedPrefs(store, scope)
        for (t in listOf("a", "ab", "abc")) {
            prefs.edit { it[k] = t }
            assertEquals(t, prefs.get()[k])
        }
        withTimeout(5_000) { store.state.first { it[k] == "abc" } }
        repeat(5) { yield() }
        assertEquals("abc", prefs.get()[k])
        prefs.edit { it.remove(k) }
        assertNull(prefs.get()[k])
        withTimeout(5_000) { store.state.first { it[k] == null } }
        scope.cancel()
    }

    @Test fun loadsWhatIsAlreadyStored() = runBlocking {
        val p = emptyPreferences().toMutablePreferences().also { it[k] = "kept" }
        val scope = CoroutineScope(coroutineContext + Job())
        assertEquals("kept", CachedPrefs(FakeStore(p), scope).get()[k])
        scope.cancel()
    }

    @Test fun speakerSwitchStyleCalls() = runBlocking {
        val store = FakeStore()
        val scope = CoroutineScope(coroutineContext + Job())
        val prefs = CachedPrefs(store, scope)
        assertFalse(prefs.getBoolean("speak", false))
        prefs.edit().putBoolean("speak", true).apply()
        assertTrue(prefs.getBoolean("speak", false))
        withTimeout(5_000) { store.state.first { it[booleanPreferencesKey("speak")] == true } }
        scope.cancel()
    }
}
