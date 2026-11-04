package life.mygig.clauderc.ui.vm

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import life.mygig.clauderc.api.ApiException
import life.mygig.clauderc.api.ChatTask
import life.mygig.clauderc.api.ChatWorker
import life.mygig.clauderc.api.ClusterAccount
import life.mygig.clauderc.api.ClusterConfig
import life.mygig.clauderc.api.Codes
import life.mygig.clauderc.api.LauncherApi
import life.mygig.clauderc.api.LoginUrl
import life.mygig.clauderc.api.WorkerRun
import life.mygig.clauderc.data.Server
import life.mygig.clauderc.data.SettingsStore

/** Worker kinds that can only sign in with a pasted API key. */
fun usesApiKey(kind: String): Boolean = kind == "gemini"

/** Worker kinds that choose between their account (device code) and a pasted API key. */
fun choosesSignIn(kind: String): Boolean = kind == "grok"

/** The server is still waiting for the browser, or the phone lost the connection meanwhile (it often does while the browser is in front): ask again. */
fun grokSignInStillWaiting(code: String): Boolean = code == Codes.LOGIN_PENDING || code == Codes.NETWORK || code == Codes.TIMEOUT

private const val GROK_SIGN_IN_MAX_MS = 15 * 60 * 1000L

/**
 * Team (extra Claude accounts): the cluster view across servers, its settings, the "join the
 * cluster?" prompt, workers and their sign-in, and the open chat's workers ([chatSession]).
 * [apiFor] reaches a server other than the active one.
 */
class TeamController(
    private val api: LauncherApi,
    private val store: SettingsStore,
    private val tools: VmTools,
    private val apiFor: (Server) -> LauncherApi,
    private val chatSession: () -> String?,
) {
    /** One server's accounts in the cluster view; [accounts] is null when it couldn't be reached ([error] says why). */
    data class ClusterGroup(val serverId: String, val host: String, val active: Boolean, val accounts: List<ClusterAccount>?, val error: String = "")
    /** A just-added server signed in to another Claude account: ask whether it joins the cluster. */
    data class ClusterOffer(val serverId: String, val host: String, val email: String)

    private val _cluster = MutableStateFlow<List<ClusterGroup>?>(null)
    val cluster = _cluster.asStateFlow()
    private val _clusterOffer = MutableStateFlow<ClusterOffer?>(null)
    val clusterOffer = _clusterOffer.asStateFlow()
    /** Server-wide cluster settings; null until loaded, or when the server is too old to have them. */
    private val _clusterConfig = MutableStateFlow<ClusterConfig?>(null)
    val clusterConfig = _clusterConfig.asStateFlow()

    private suspend fun clusterOf(s: Server, active: Boolean): ClusterGroup = try {
        ClusterGroup(s.id, s.host, active, (if (active) api else apiFor(s)).cluster().accounts)
    } catch (e: ApiException) {
        ClusterGroup(s.id, s.host, active, null, if (e.code == Codes.FORBIDDEN) "Server scripts need an update" else e.message.orEmpty())
    }

    /** The active server's accounts plus those of the servers already in the cluster, looked up together. */
    fun loadCluster() = tools.action("Loading accounts…") {
        _cluster.value = clusterGroups()
        _clusterConfig.value = try { api.clusterConfig() } catch (_: ApiException) { null }
    }
    fun setClusterConfig(key: String, value: String) = tools.action("Saving…") { _clusterConfig.value = api.clusterConfigSet(key, value) }

    private suspend fun clusterGroups(): List<ClusterGroup> = coroutineScope {
        val cur = store.current()
        val others = cur.servers.filter { it.id != cur.activeId && it.isConfigured && it.inCluster == true }
        val first = async { clusterOf(cur.servers.first { it.id == cur.activeId }, true) }
        (listOf(first) + others.map { s -> async { clusterOf(s, false) } }).awaitAll()
    }

    fun removeFromCluster(serverId: String) = tools.scope.launch {
        store.setInCluster(serverId, false)
        _cluster.value = _cluster.value?.filter { it.serverId != serverId }
    }

    fun answerClusterOffer(join: Boolean) {
        val o = _clusterOffer.value ?: return
        _clusterOffer.value = null
        tools.scope.launch { store.setInCluster(o.serverId, join, alsoOthers = join) }
    }

    /**
     * After a new server is trusted: if it's signed in to a different Claude account than the servers
     * we already have, offer the cluster. Same account, or nothing to compare: ask nothing.
     */
    suspend fun detectNewAccount() {
        val cur = store.current()
        val me = cur.servers.firstOrNull { it.id == cur.activeId } ?: return
        if (me.inCluster != null) return
        val others = cur.servers.filter { it.id != me.id && it.isConfigured }
        if (others.isEmpty()) return
        val mine = try { api.cluster().accounts.firstOrNull { it.name == "main" }?.email } catch (_: ApiException) { null }
        if (mine == null) return
        val known = coroutineScope {
            others.map { s -> async { try { apiFor(s).cluster().accounts.mapNotNull { it.email } } catch (_: ApiException) { emptyList() } } }.awaitAll().flatten()
        }
        if (mine in known) store.setInCluster(me.id, false)   // same account: nothing to add
        else _clusterOffer.value = ClusterOffer(me.id, me.host, mine)
    }

    /** The open chat's workers (attached or not) and its open work; null until loaded. */
    private val _chatWorkers = MutableStateFlow<List<ChatWorker>?>(null)
    val chatWorkers = _chatWorkers.asStateFlow()
    private val _chatTasks = MutableStateFlow<List<ChatTask>>(emptyList())
    val chatTasks = _chatTasks.asStateFlow()
    private suspend fun refreshChatWorkers(session: String) {
        val d = api.clusterSession(session)
        _chatWorkers.value = d.workers
        _chatTasks.value = d.tasks
    }
    fun loadChatWorkers() {
        val s = chatSession() ?: return
        tools.action("Loading workers…") { refreshChatWorkers(s) }
    }
    fun attachWorker(name: String, on: Boolean) {
        val s = chatSession() ?: return
        tools.action("Saving…") { api.clusterAttach(s, name, on); refreshChatWorkers(s) }
    }
    /** Role or mode for this chat only; blank goes back to the worker's own. */
    fun assignWorker(name: String, field: String, value: String) {
        val s = chatSession() ?: return
        tools.action("Saving…") { api.clusterAssign(s, name, field, value); refreshChatWorkers(s) }
    }
    /** The chat closed: its workers and tasks go with it. */
    fun clearChat() {
        _chatWorkers.value = null
        _chatTasks.value = emptyList()
    }

    private val _workerLogin = MutableStateFlow<Pair<String, LoginUrl>?>(null)
    val workerLogin = _workerLogin.asStateFlow()
    /** A worker whose API key is being asked for; the dialog's text depends on its kind. */
    data class WorkerKeyAsk(val name: String, val kind: String)
    private val _workerKey = MutableStateFlow<WorkerKeyAsk?>(null)
    val workerKey = _workerKey.asStateFlow()
    /** A Grok worker choosing how to sign in: its account or an API key. */
    private val _workerSignInChoice = MutableStateFlow<String?>(null)
    val workerSignInChoice = _workerSignInChoice.asStateFlow()
    fun closeWorkerSignInChoice() { _workerSignInChoice.value = null }
    fun useGrokKey(name: String) {
        _workerSignInChoice.value = null
        _workerKey.value = WorkerKeyAsk(name, "grok")
    }
    private var grokSignInWait: Job? = null
    fun startGrokAccountSignIn(name: String) = tools.action("Starting Grok sign-in for $name…") {
        _workerSignInChoice.value = null
        _workerLogin.value = name to api.workerGrokLoginStart(name)
        waitForGrokSignIn(name)
    }
    /** Not a spinner action: the screen stays usable, and the dialog closes itself when the browser step is done. */
    private fun waitForGrokSignIn(name: String) {
        grokSignInWait?.cancel()
        grokSignInWait = tools.scope.launch {
            val until = System.currentTimeMillis() + GROK_SIGN_IN_MAX_MS
            while (System.currentTimeMillis() < until) {
                try {
                    api.workerGrokLoginWait(name)
                    finishGrokSignIn(name, "$name is signed in to Grok.")
                    return@launch
                } catch (e: ApiException) {
                    if (!grokSignInStillWaiting(e.code)) {
                        finishGrokSignIn(name, e.message)   // not report(): its "Sign in" fix would open the main Claude sign-in
                        return@launch
                    }
                    if (e.code != Codes.LOGIN_PENDING) delay(3_000)
                }
            }
            closeWorkerLogin()
            tools.say("The Grok sign-in for $name timed out. Start it again.")
        }
    }
    /** The server keeps the finished session so a retry gets the same answer; now that it was read, end it. */
    private suspend fun finishGrokSignIn(name: String, message: String) {
        _workerLogin.value = null
        tools.say(message)
        try { api.workerLoginCancel(name) } catch (_: ApiException) { }
        _cluster.value = clusterGroups()
    }
    fun closeWorkerKey() { _workerKey.value = null }
    fun saveWorkerKey(name: String, key: String) = tools.action("Saving the key for $name…") {
        api.workerSetKey(name, key)
        _workerKey.value = null
        _cluster.value = clusterGroups()
    }
    private val _workerRuns = MutableStateFlow<Pair<String, List<WorkerRun>>?>(null)
    val workerRuns = _workerRuns.asStateFlow()

    fun addWorker(name: String, role: String, kind: String = "claude") = tools.action("Adding $name…") {
        api.workerAdd(name, role, kind)
        _cluster.value = clusterGroups()
        beginSignIn(name, kind)   // straight on to signing in
    }
    private suspend fun beginSignIn(name: String, kind: String) {
        when {
            choosesSignIn(kind) -> _workerSignInChoice.value = name
            usesApiKey(kind) -> _workerKey.value = WorkerKeyAsk(name, kind)
            else -> _workerLogin.value = name to api.workerLoginStart(name)
        }
    }
    fun setWorker(name: String, field: String, value: String) = tools.action("Saving…") {
        api.workerSet(name, field, value)
        _cluster.value = clusterGroups()
    }
    fun removeWorker(name: String) = tools.action("Removing $name…") {
        api.workerRemove(name)
        _cluster.value = clusterGroups()
    }
    fun workerLoginStart(name: String, kind: String = "claude") = tools.action("Starting sign-in for $name…") { beginSignIn(name, kind) }
    fun workerLoginCode(name: String, code: String) = tools.action("Checking code…") {
        api.workerLoginCode(name, code)
        _workerLogin.value = null
        _cluster.value = clusterGroups()
    }
    // ponytail: for the other kinds the server's login helper session just times out; worker-login-cancel would end it too.
    fun closeWorkerLogin() {
        val open = _workerLogin.value
        _workerLogin.value = null
        if (open?.second?.code == null) return
        // A device code waiting on the server: end it, so nothing can still be approved after Cancel.
        grokSignInWait?.cancel()
        tools.scope.launch { try { api.workerLoginCancel(open.first) } catch (_: ApiException) { } }
    }
    fun showWorkerRuns(name: String) = tools.action("Loading runs…") { _workerRuns.value = name to api.workerRuns(name).runs }
    fun closeWorkerRuns() { _workerRuns.value = null }

    /** Another server: nothing of the old one's cluster may stay on screen. */
    fun clearServerState() {
        _cluster.value = null
        _clusterConfig.value = null
        _clusterOffer.value = null
        grokSignInWait?.cancel()   // its calls would now reach the new server
        _workerLogin.value = null
        _workerSignInChoice.value = null
        _workerKey.value = null   // a key typed after switching servers must not go to the new server's worker
        _workerRuns.value = null
    }
}
