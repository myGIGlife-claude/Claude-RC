package life.mygig.clauderc.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import life.mygig.clauderc.ssh.ServerConfig

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** One server. They all use this phone's single SSH key. */
@Serializable
data class Server(
    val id: String,
    val host: String = "",
    val port: Int = 22,
    val user: String = "",
    val hostKeyType: String = "",
    val hostKeyBlob: String = "",
    val hostKeyFingerprint: String = "",
    /** Fingerprint pinned before "Forget host key", so a changed key still gets flagged. */
    val previousFingerprint: String = "",
    /** In cLaudeCluster: true yes, false no, null not asked yet. */
    val inCluster: Boolean? = null,
) {
    val isConfigured: Boolean
        get() = host.isNotBlank() && user.isNotBlank() && hostKeyBlob.isNotBlank()

    fun toServerConfig() = ServerConfig(host, port, user, hostKeyType, hostKeyBlob)
}

/** The active server's fields, plus the full list and the app-wide settings. */
data class AppSettings(
    val host: String = "",
    val port: Int = 22,
    val user: String = "",
    val hostKeyType: String = "",
    val hostKeyBlob: String = "",
    val hostKeyFingerprint: String = "",
    val previousFingerprint: String = "",
    val appLock: Boolean = false,
    /** Session notifications (needs Android's notification permission). */
    val notify: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val servers: List<Server> = emptyList(),
    val activeId: String = "",
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
        val SERVERS = stringPreferencesKey("servers")
        val ACTIVE = stringPreferencesKey("active_server")
        val APP_LOCK = booleanPreferencesKey("app_lock")
        val NOTIFY = booleanPreferencesKey("notify")
        val THEME = stringPreferencesKey("theme")
        val REPOS_CACHE = stringPreferencesKey("repos_cache")
        val OWNERS_CACHE = stringPreferencesKey("owners_cache")
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun servers(p: Preferences): List<Server> =
        p[K.SERVERS]?.let { runCatching { json.decodeFromString<List<Server>>(it) }.getOrNull() }
            ?: emptyList()

    private fun activeId(p: Preferences, list: List<Server>): String =
        p[K.ACTIVE]?.takeIf { id -> list.any { it.id == id } } ?: list.firstOrNull()?.id.orEmpty()

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        val list = servers(p)
        val id = activeId(p, list)
        val a = list.firstOrNull { it.id == id } ?: Server("")
        AppSettings(
            host = a.host,
            port = a.port,
            user = a.user,
            hostKeyType = a.hostKeyType,
            hostKeyBlob = a.hostKeyBlob,
            hostKeyFingerprint = a.hostKeyFingerprint,
            previousFingerprint = a.previousFingerprint,
            appLock = p[K.APP_LOCK] ?: false,
            notify = p[K.NOTIFY] ?: false,
            theme = runCatching { ThemeMode.valueOf(p[K.THEME] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
            servers = list,
            activeId = id,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    /** Rewrites the server list; [change] gets the list and active id and returns the new active id. */
    private suspend fun editServers(change: (MutableList<Server>, String) -> String) {
        context.dataStore.edit { p ->
            val list = servers(p).toMutableList()
            val before = activeId(p, list)
            val after = change(list, before)
            p[K.SERVERS] = json.encodeToString(list.toList())
            p[K.ACTIVE] = after
            // The cached lists belong to one server.
            if (after != before) { p.remove(K.REPOS_CACHE); p.remove(K.OWNERS_CACHE) }
        }
    }

    /** Changes the active server, creating one if there is none. */
    private suspend fun editActive(change: (Server) -> Server) = editServers { list, id ->
        val i = list.indexOfFirst { it.id == id }
        if (i >= 0) { list[i] = change(list[i]); id } else change(Server(newId())).also { list += it }.id
    }

    /**
     * A different host or port is a different server: drop its pinned key.
     * A different username on the same server keeps the pin, so a key change
     * is still reported as a change. Either way the cached lists are dropped.
     */
    suspend fun saveServer(host: String, port: Int, user: String) {
        val before = current()
        editActive { s ->
            val newServer = s.host != host || s.port != port
            s.copy(host = host, port = port, user = user).let {
                if (newServer) it.copy(hostKeyType = "", hostKeyBlob = "", hostKeyFingerprint = "", previousFingerprint = "") else it
            }
        }
        if (before.host != host || before.port != port || before.user != user) {
            context.dataStore.edit { p -> p.remove(K.REPOS_CACHE); p.remove(K.OWNERS_CACHE) }
        }
    }

    suspend fun pinHostKey(type: String, blob: String, fingerprint: String) = editActive {
        it.copy(hostKeyType = type, hostKeyBlob = blob, hostKeyFingerprint = fingerprint, previousFingerprint = "")
    }

    suspend fun forgetHostKey() = editActive {
        it.copy(
            hostKeyType = "", hostKeyBlob = "", hostKeyFingerprint = "",
            previousFingerprint = it.hostKeyFingerprint.ifEmpty { it.previousFingerprint },
        )
    }

    /** Adds a blank server and makes it active, so the setup screen shows. */
    suspend fun addServer() = editServers { list, _ -> Server(newId()).also { list += it }.id }

    /** Adds a filled-in server under a new id (the one passed in is ignored) and keeps the active server as it is. */
    suspend fun addServerEntry(s: Server): Server {
        val added = s.copy(id = newId())
        editServers { list, cur -> list += added; cur }
        return added
    }

    /** Puts one server in or out of the cluster; [alsoOthers] puts every configured server in too. */
    suspend fun setInCluster(id: String, on: Boolean, alsoOthers: Boolean = false) = editServers { list, cur ->
        list.replaceAll { if (it.id == id || (alsoOthers && it.isConfigured)) it.copy(inCluster = on) else it }
        cur
    }

    suspend fun switchServer(id: String) = editServers { list, cur -> if (list.any { it.id == id }) id else cur }

    /** Removes a server. If it was active, the first configured one becomes active. */
    suspend fun removeServer(id: String) = editServers { list, cur ->
        list.removeAll { it.id == id }
        if (cur != id) cur else (list.firstOrNull { it.isConfigured } ?: list.firstOrNull())?.id.orEmpty()
    }

    private fun newId() = UUID.randomUUID().toString()

    suspend fun setAppLock(on: Boolean) = context.dataStore.edit { it[K.APP_LOCK] = on }
    suspend fun setNotify(on: Boolean) = context.dataStore.edit { it[K.NOTIFY] = on }
    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[K.THEME] = mode.name }

    /** Replaces backed-up app preferences; transient caches are intentionally cleared. */
    suspend fun restoreBackup(servers: List<Server>, activeId: String, appLock: Boolean, notify: Boolean, theme: ThemeMode) {
        context.dataStore.edit { p ->
            p[K.SERVERS] = json.encodeToString(servers)
            p[K.ACTIVE] = activeId.takeIf { id -> servers.any { it.id == id } } ?: servers.firstOrNull()?.id.orEmpty()
            p[K.APP_LOCK] = appLock
            p[K.NOTIFY] = notify
            p[K.THEME] = theme.name
            p.remove(K.REPOS_CACHE); p.remove(K.OWNERS_CACHE)
        }
    }

    suspend fun reposCache(): String? = context.dataStore.data.first()[K.REPOS_CACHE]
    suspend fun setReposCache(json: String) = context.dataStore.edit { it[K.REPOS_CACHE] = json }
    suspend fun ownersCache(): String? = context.dataStore.data.first()[K.OWNERS_CACHE]
    suspend fun setOwnersCache(json: String) = context.dataStore.edit { it[K.OWNERS_CACHE] = json }
}
