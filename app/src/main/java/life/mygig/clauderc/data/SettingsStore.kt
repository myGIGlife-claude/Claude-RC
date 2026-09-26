package life.mygig.clauderc.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import life.mygig.clauderc.ssh.ServerConfig

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val host: String = "",
    val port: Int = 22,
    val user: String = "",
    val hostKeyType: String = "",
    val hostKeyBlob: String = "",
    val hostKeyFingerprint: String = "",
    /** Fingerprint pinned before "Forget host key", so a changed key still gets flagged. */
    val previousFingerprint: String = "",
    val appLock: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
) {
    val isConfigured: Boolean
        get() = host.isNotBlank() && user.isNotBlank() && hostKeyBlob.isNotBlank()

    fun toServerConfig() = ServerConfig(host, port, user, hostKeyType, hostKeyBlob)
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** Settings and caches. The app ships blank: no server is filled in by default. */
class SettingsStore(private val context: Context) {

    private object K {
        val HOST = stringPreferencesKey("host")
        val PORT = intPreferencesKey("port")
        val USER = stringPreferencesKey("user")
        val HK_TYPE = stringPreferencesKey("host_key_type")
        val HK_BLOB = stringPreferencesKey("host_key_blob")
        val HK_FP = stringPreferencesKey("host_key_fp")
        val HK_PREV_FP = stringPreferencesKey("host_key_prev_fp")
        val APP_LOCK = booleanPreferencesKey("app_lock")
        val THEME = stringPreferencesKey("theme")
        val REPOS_CACHE = stringPreferencesKey("repos_cache")
        val OWNERS_CACHE = stringPreferencesKey("owners_cache")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            host = p[K.HOST].orEmpty(),
            port = p[K.PORT] ?: 22,
            user = p[K.USER].orEmpty(),
            hostKeyType = p[K.HK_TYPE].orEmpty(),
            hostKeyBlob = p[K.HK_BLOB].orEmpty(),
            hostKeyFingerprint = p[K.HK_FP].orEmpty(),
            previousFingerprint = p[K.HK_PREV_FP].orEmpty(),
            appLock = p[K.APP_LOCK] ?: false,
            theme = runCatching { ThemeMode.valueOf(p[K.THEME] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
        )
    }

    suspend fun current(): AppSettings = settings.first()

    /**
     * A different host or port is a different server: drop its pinned key.
     * A different username on the same server keeps the pin, so a key change
     * is still reported as a change. Either way the cached lists are dropped.
     */
    suspend fun saveServer(host: String, port: Int, user: String) {
        context.dataStore.edit { p ->
            val newServer = p[K.HOST] != host || (p[K.PORT] ?: 22) != port
            val newUser = p[K.USER] != user
            p[K.HOST] = host
            p[K.PORT] = port
            p[K.USER] = user
            if (newServer) {
                p.remove(K.HK_TYPE); p.remove(K.HK_BLOB); p.remove(K.HK_FP); p.remove(K.HK_PREV_FP)
            }
            if (newServer || newUser) {
                p.remove(K.REPOS_CACHE); p.remove(K.OWNERS_CACHE)
            }
        }
    }

    suspend fun pinHostKey(type: String, blob: String, fingerprint: String) {
        context.dataStore.edit { p ->
            p[K.HK_TYPE] = type
            p[K.HK_BLOB] = blob
            p[K.HK_FP] = fingerprint
            p.remove(K.HK_PREV_FP)
        }
    }

    suspend fun forgetHostKey() {
        context.dataStore.edit { p ->
            p[K.HK_FP]?.let { p[K.HK_PREV_FP] = it }
            p.remove(K.HK_TYPE); p.remove(K.HK_BLOB); p.remove(K.HK_FP)
        }
    }

    suspend fun setAppLock(on: Boolean) = context.dataStore.edit { it[K.APP_LOCK] = on }
    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[K.THEME] = mode.name }

    suspend fun reposCache(): String? = context.dataStore.data.first()[K.REPOS_CACHE]
    suspend fun setReposCache(json: String) = context.dataStore.edit { it[K.REPOS_CACHE] = json }
    suspend fun ownersCache(): String? = context.dataStore.data.first()[K.OWNERS_CACHE]
    suspend fun setOwnersCache(json: String) = context.dataStore.edit { it[K.OWNERS_CACHE] = json }
}
