package life.mygig.clauderc.ui.vm

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import life.mygig.clauderc.api.ApiException
import life.mygig.clauderc.api.Codes
import life.mygig.clauderc.api.LauncherApi
import life.mygig.clauderc.api.McpData
import life.mygig.clauderc.api.PluginsData
import life.mygig.clauderc.api.RunResult
import life.mygig.clauderc.api.Updates
import life.mygig.clauderc.ui.Detail

/**
 * Connections: MCP servers, plugins, tile details, MCP sign-in and the Claude command runner.
 * [scriptApi] is the server scripts' API level (0 until the status is in); [refreshStatus]
 * reloads the shared status after a disconnect.
 */
class ConnectionsController(
    private val api: LauncherApi,
    private val tools: VmTools,
    private val scriptApi: () -> Int,
    private val refreshStatus: () -> Unit,
) {
    private val _mcp = MutableStateFlow<McpData?>(null)
    val mcp = _mcp.asStateFlow()

    /** MCP servers from the server's cache; [check] runs a fresh health check (~10 s). */
    fun refreshMcp(check: Boolean = false) {
        if (scriptApi() < Updates.MIN_SCRIPT_API) return
        if (check) {
            tools.action("Checking MCP servers…") { _mcp.value = api.mcpRefresh() }
        } else {
            tools.scope.launch {
                runCatching { api.mcp() }.getOrNull()?.let { m ->
                    _mcp.value = m
                    // The server started a background check: pick up its result shortly.
                    if (m.refreshing) { delay(20_000); runCatching { api.mcp() }.getOrNull()?.let { _mcp.value = it } }
                }
            }
        }
    }

    private val _plugins = MutableStateFlow<PluginsData?>(null)
    val plugins = _plugins.asStateFlow()
    private val _pluginsLoading = MutableStateFlow(false)
    val pluginsLoading = _pluginsLoading.asStateFlow()

    fun loadPlugins() {
        if (_pluginsLoading.value || scriptApi() < Updates.MIN_SCRIPT_API) return
        tools.scope.launch {
            _pluginsLoading.value = true
            try { _plugins.value = api.plugins() } catch (e: ApiException) { tools.report(e) } finally { _pluginsLoading.value = false }
        }
    }

    /** The tile detail page that's open, if any. */
    private val _detail = MutableStateFlow<Detail?>(null)
    val detail = _detail.asStateFlow()
    fun openDetail(d: Detail?) {
        if (_mcpAuth.value != null) { _mcpAuth.value = null; tools.scope.launch { runCatching { api.mcpAuthCancel() } } }
        _detail.value = d
    }

    /** An MCP sign-in in progress: server name to the sign-in URL. */
    private val _mcpAuth = MutableStateFlow<Pair<String, String>?>(null)
    val mcpAuth = _mcpAuth.asStateFlow()

    fun mcpAuthStart(name: String) = tools.action("Starting sign-in for $name…") {
        _mcpAuth.value = name to api.mcpAuthStart(name).url
    }

    fun mcpAuthFinish(callbackUrl: String) = tools.action("Finishing sign-in…", onError = { e -> _mcpAuth.value = null; tools.report(e) }) {
        _mcp.value = api.mcpAuthFinish(callbackUrl)
        _mcpAuth.value = null
        tools.say("Signed in. Restart sessions to use it.")
    }

    fun disconnect(id: String, name: String) = tools.action("Disconnecting $name…") {
        api.disconnect(id)
        _detail.value = null
        tools.say("$name disconnected. Restart sessions to drop it.")
        refreshStatus()
    }

    /** A plugin's MCP server goes away with the plugin: turn the plugin off. */
    fun disablePlugin(id: String) = tools.action("Disabling $id…") {
        val r = api.claudeCmd("plugin disable $id")
        if (r.exitCode != 0) throw ApiException(Codes.INTERNAL, r.output.ifBlank { "claude plugin disable failed" })
        _detail.value = null
        tools.say("$id disabled. Restart sessions to drop it; turn it back on in the Claude tab.")
        runCatching { api.plugins() }.getOrNull()?.let { _plugins.value = it }
        _mcp.value = api.mcpRefresh()
    }

    fun removeMcp(name: String) = tools.action("Removing $name…") {
        val r = api.claudeCmd("mcp remove $name -s user")
        if (r.exitCode != 0) throw ApiException(Codes.INTERNAL, r.output.ifBlank { "claude mcp remove failed" })
        _detail.value = null
        tools.say("$name removed. Restart sessions to drop it.")
        _mcp.value = api.mcpRefresh()
    }

    /** Command Center: what ran and what it printed. */
    private val _ccResult = MutableStateFlow<Pair<String, RunResult>?>(null)
    val ccResult = _ccResult.asStateFlow()

    fun claudeCommand(args: String) = tools.action("Running claude ${args.trim()}…") {
        _ccResult.value = args.trim() to api.claudeCmd(args)
        // Plugin and marketplace changes: show the new list.
        if (args.trim().startsWith("plugin")) {
            runCatching { api.plugins() }.getOrNull()?.let { _plugins.value = it }
        }
    }

    /** A plugin the `skills` CLI manages: the server re-runs `npx skills add <source>` (can take minutes). */
    fun updateSkills(source: String) = tools.action("Updating $source…") {
        api.skillsUpdate(source)
        tools.say("Updated $source")
        runCatching { api.plugins() }.getOrNull()?.let { _plugins.value = it }
    }

    fun clearCcResult() { _ccResult.value = null }

    /** Another server: nothing from the old one may stay on screen or in use. */
    fun clearServerState() {
        _ccResult.value = null
        _mcp.value = null
        _plugins.value = null
        _mcpAuth.value = null
        _detail.value = null
    }
}
