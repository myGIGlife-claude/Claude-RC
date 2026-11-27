package life.mygig.clauderc.ui.vm

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import life.mygig.clauderc.api.ApiException
import life.mygig.clauderc.api.COPY_MIN_SCRIPT_API
import life.mygig.clauderc.api.Codes
import life.mygig.clauderc.api.COPY_WHOLE_FOLDER
import life.mygig.clauderc.api.CopyDiff
import life.mygig.clauderc.api.CopyEntry
import life.mygig.clauderc.api.CopyKind
import life.mygig.clauderc.api.CopyRoute
import life.mygig.clauderc.api.CopyStatus
import life.mygig.clauderc.api.LauncherApi
import life.mygig.clauderc.api.chooseCopyRoute
import life.mygig.clauderc.api.copyApplyLines
import life.mygig.clauderc.api.copyPathOk
import life.mygig.clauderc.api.defaultCopyTicks
import life.mygig.clauderc.api.diffCopy
import life.mygig.clauderc.data.Server
import life.mygig.clauderc.data.SettingsStore
import life.mygig.clauderc.data.cleanTailnetHost
import life.mygig.clauderc.ui.friendly

/** The screen's steps (docs/copy-design.md, "App flow"); CHECK and ROUTE are shown while they work. */
enum class CopyStep { PICK, CHECK, CHOOSE, ROUTE, SEND, APPLY, DONE }

data class CopyUi(
    val open: Boolean = false,
    val step: CopyStep = CopyStep.PICK,
    val from: Server? = null,
    val to: Server? = null,
    val kind: CopyKind = CopyKind.PROJECT,
    val project: String = "",
    /** Folders on the From server (its local repos). */
    val projects: List<String> = emptyList(),
    val diff: CopyDiff? = null,
    /** The listing was cut at the server's cap. */
    val truncated: Boolean = false,
    /** Paths that were not offered because they can't travel safely (a line break in the name). */
    val unsendable: Int = 0,
    /** A plain heads-up for the pick step (empty = none). */
    val notice: String = "",
    val ticked: Set<String> = emptySet(),
    /** "Tailnet" or "Public route"; empty until chosen. */
    val route: String = "",
    val status: CopyStatus? = null,
    val result: CopyStatus? = null,
    val log: List<String> = emptyList(),
    val error: String? = null,
    val busy: Boolean = false,
    /** A key was put on the To server: Cancel removes it. */
    val keyOnTo: Boolean = false,
)

fun copyServerLabel(s: Server) = "${s.host} (${s.user})" + if (s.purpose.isNotBlank()) " · ${s.purpose}" else ""

private const val CODE_STEP = "copy_step"
private const val CODE_FAILED = "copy_failed"
private const val CODE_SCRIPTS = "copy_scripts"

/**
 * Copy between two of the owner's servers, server to server (docs/copy-design.md). The phone only relays the one-time
 * public key from the From server to the To server; it never holds a secret value. One piece of work at a time; a failed
 * step leaves [CopyUi.error] set and [retry] runs it again.
 */
class CopyController(
    private val tools: VmTools,
    private val store: SettingsStore,
    private val apiFor: (Server) -> LauncherApi,
) {
    private val _ui = MutableStateFlow(CopyUi())
    val ui: StateFlow<CopyUi> = _ui.asStateFlow()

    private var job: Job? = null
    private var generation = 0
    private var retryBlock: (suspend () -> Unit)? = null
    /** The file the To server received (set once the send finished). */
    private var sentFile: String? = null
    /** copy-apply was started on the To server for [sentFile] (it isn't started twice; a failed apply clears it). */
    private var applyStarted = false

    // ---- Machinery ---------------------------------------------------------------

    private fun work(block: suspend () -> Unit) {
        if (job?.isActive == true) return
        retryBlock = block
        val mine = ++generation
        _ui.update { it.copy(busy = true, error = null) }
        job = tools.scope.launch {
            try {
                block()
            } catch (e: ApiException) {
                fail(e)
            } finally {
                if (mine == generation) _ui.update { it.copy(busy = false) }
            }
        }
    }

    private fun fail(e: ApiException) {
        val text = friendly(e).first
        _ui.update { it.copy(error = text) }
        log("Stopped: $text")
    }

    private fun log(line: String) = _ui.update { it.copy(log = (it.log + line).takeLast(200)) }

    private fun setStep(step: CopyStep) = _ui.update { it.copy(step = step) }

    private fun needStep(vararg steps: CopyStep) {
        if (_ui.value.step !in steps) throw ApiException(CODE_STEP, "That isn't the next step. Go back to the current one.")
    }

    private fun servers(): Pair<Server, Server> {
        val s = _ui.value
        val from = s.from
        val to = s.to
        if (from == null || to == null || !from.isConfigured || !to.isConfigured) {
            throw ApiException(Codes.NOT_CONFIGURED, "Pick two servers that are set up in the app.")
        }
        if (from.id == to.id || (from.host == to.host && from.port == to.port && from.user == to.user)) {
            throw ApiException(Codes.INVALID_NAME, "From and To must be two different servers.")
        }
        return Pair(from, to)
    }

    fun retry() {
        retryBlock?.let { work(it) }
    }

    // ---- Open, close, choose -----------------------------------------------------

    fun open() {
        if (_ui.value.step == CopyStep.DONE) reset(open = true) else _ui.update { it.copy(open = true) }
    }

    /** Leaves the screen: stops the calls running here (a job already running on a server carries on). */
    fun close() {
        generation++
        job?.cancel()
        job = null
        _ui.update { it.copy(open = false, busy = false) }
    }

    fun reset(open: Boolean = true) {
        generation++
        job?.cancel()
        job = null
        retryBlock = null
        sentFile = null
        applyStarted = false
        _ui.value = CopyUi(open = open)
    }

    fun setServers(fromId: String, toId: String) {
        tools.scope.launch {
            if (_ui.value.step != CopyStep.PICK || job?.isActive == true) return@launch
            val list = store.current().servers
            val from = list.firstOrNull { it.id == fromId }
            val changed = from?.id != _ui.value.from?.id
            _ui.update {
                it.copy(
                    from = from, to = list.firstOrNull { s -> s.id == toId }, error = null,
                    projects = if (changed) emptyList() else it.projects, project = if (changed) "" else it.project,
                )
            }
            if (changed && from != null && from.isConfigured) loadProjects(from)
        }
    }

    private suspend fun loadProjects(from: Server) {
        val names = try {
            apiFor(from).repos(false).repos.filter { it.local }.map { it.name }.sorted()
        } catch (e: ApiException) {
            emptyList()
        }
        _ui.update { if (it.from?.id == from.id) it.copy(projects = names) else it }
    }

    fun setKind(kind: CopyKind) = _ui.update { if (it.step == CopyStep.PICK) it.copy(kind = kind, error = null) else it }

    fun setProject(name: String) = _ui.update { if (it.step == CopyStep.PICK) it.copy(project = name, error = null) else it }

    /** Back to the first step. */
    fun edit() {
        if (job?.isActive == true) return
        if (_ui.value.step == CopyStep.CHOOSE || _ui.value.step == CopyStep.CHECK) _ui.update { it.copy(step = CopyStep.PICK, diff = null, ticked = emptySet(), notice = "", error = null) }
    }

    fun toggle(path: String) = _ui.update { it.copy(ticked = if (path in it.ticked) it.ticked - path else it.ticked + path) }

    fun setGroup(entries: List<CopyEntry>, on: Boolean) {
        val paths = entries.map { it.path }
        _ui.update { it.copy(ticked = if (on) it.ticked + paths else it.ticked - paths.toSet()) }
    }

    // ---- 2. Check ----------------------------------------------------------------

    fun start() = work { check() }

    private suspend fun check() {
        needStep(CopyStep.PICK, CopyStep.CHECK, CopyStep.CHOOSE)
        val list = store.current().servers
        _ui.update { cur ->
            cur.copy(from = cur.from?.let { f -> list.firstOrNull { it.id == f.id } }, to = cur.to?.let { t -> list.firstOrNull { it.id == t.id } })
        }
        val (f, t) = servers()
        val cur = _ui.value
        if (cur.kind == CopyKind.PROJECT && cur.project.isBlank()) throw ApiException(Codes.INVALID_NAME, "Pick the project to copy.")
        val project = cur.project.takeIf { cur.kind == CopyKind.PROJECT }
        setStep(CopyStep.CHECK)
        for (s in listOf(f, t)) {
            if (apiFor(s).status().scriptApi < COPY_MIN_SCRIPT_API) {
                throw ApiException(CODE_SCRIPTS, "${copyServerLabel(s)} needs \"Update server scripts\" before it can copy. Update it in Settings, then try again.")
            }
        }
        log("Reading ${f.host}…")
        val a = apiFor(f).copyList(cur.kind, project)
        if (a.exists == false) throw ApiException(Codes.INVALID_NAME, "${f.host} has no project folder \"$project\".")
        val offered = a.entries.filter { copyPathOk(it.path) }
        log("Reading ${t.host}…")
        // Bundle entries carry the commit's sha in `mode`; the To server answers for the commits it already has.
        val bundleAsk = if (cur.kind == CopyKind.BUNDLE) offered.filter { it.mode.isNotBlank() }.map { "${it.path} ${it.mode}" } else emptyList()
        val b = apiFor(t).copyList(cur.kind, project, bundleAsk)
        val missingThere = b.exists == false
        val diff = if (cur.kind == CopyKind.PROJECT && missingThere) {
            // The folder isn't on the To server: send it whole, .git included (the server's "." path).
            CopyDiff(missingOnTo = listOf(CopyEntry(path = COPY_WHOLE_FOLDER)))
        } else if (cur.kind == CopyKind.BUNDLE) {
            // The To server lists the branches whose commit it already has: only the others are offered.
            val has = b.entries.map { it.path }.toSet()
            CopyDiff(missingOnTo = offered.filter { it.path !in has }.sortedBy { it.path })
        } else {
            diffCopy(offered, b.entries)
        }
        _ui.update {
            it.copy(
                step = CopyStep.CHOOSE, diff = diff, ticked = defaultCopyTicks(diff), truncated = a.truncated || b.truncated,
                unsendable = a.entries.size - offered.size, route = "", status = null, result = null,
                notice = if (cur.kind == CopyKind.BUNDLE && missingThere) {
                    "${t.host} doesn't have this project yet. Branches can only go into a missing project if the project has no remote; otherwise copy the project first."
                } else {
                    ""
                },
            )
        }
        log("Found ${diff.all.size} difference(s).")
    }

    // ---- 3-6. Route, send, apply -------------------------------------------------

    fun copyNow() = work { transferAndApply() }

    /** Polls `copy-status` on [api] every 2 s until the [jobName] job is done; throws if it failed. */
    private suspend fun poll(api: LauncherApi, who: String, jobName: String, staleStarted: kotlinx.serialization.json.JsonElement?): CopyStatus {
        val t0 = System.currentTimeMillis()
        var failures = 0
        var lastMessage = ""
        while (true) {
            val st = try {
                api.copyStatus()
            } catch (e: ApiException) {
                failures++
                if ((e.code != Codes.NETWORK && e.code != Codes.TIMEOUT) || failures > 5) throw e
                delay(2_000)
                continue
            }
            failures = 0
            val age = System.currentTimeMillis() - t0
            // Right after a start the file may still show the previous job: don't believe it for a moment.
            val stale = st.job != jobName || (staleStarted != null && st.started == staleStarted && age < 20_000)
            if (stale) {
                if (age > 90_000) throw ApiException(CODE_FAILED, "$who isn't reporting the job. Try again.")
            } else {
                _ui.update { it.copy(status = st) }
                if (st.message.isNotBlank() && st.message != lastMessage) {
                    lastMessage = st.message
                    log("$who: ${st.message}")
                }
                if (st.phase == "failed") {
                    throw ApiException(CODE_FAILED, st.error?.takeIf { it.isNotBlank() } ?: st.message.ifBlank { "It failed on $who." })
                }
                if (st.phase == "done") return st
            }
            delay(2_000)
        }
    }

    private suspend fun transferAndApply() {
        needStep(CopyStep.CHOOSE, CopyStep.ROUTE, CopyStep.SEND, CopyStep.APPLY)
        val (f, t) = servers()
        val s = _ui.value
        val diff = s.diff ?: throw ApiException(CODE_STEP, "Check the servers first.")
        val project = s.project.takeIf { s.kind == CopyKind.PROJECT }
        val fromApi = apiFor(f)
        val toApi = apiFor(t)
        if (sentFile == null) {
            val chosen = diff.all.filter { it.path in s.ticked }.map { it.path }
            if (chosen.isEmpty()) throw ApiException(CODE_STEP, "Tick at least one thing to copy.")
            // 4. Route: the tailnet when the From server reaches the To server there, else the public address.
            setStep(CopyStep.ROUTE)
            val tail = cleanTailnetHost(t.tailnetHost)
            val tailProbe = if (tail.isNotEmpty()) fromApi.copyProbe(tail, t.port, t.hostKeyType, t.hostKeyBlob) else null
            val publicProbe = if (tailProbe?.reachable == true) null else fromApi.copyProbe(t.host, t.port, t.hostKeyType, t.hostKeyBlob)
            val route = chooseCopyRoute(tailProbe, publicProbe, tail)
            if (route is CopyRoute.Stop) throw ApiException(CODE_FAILED, route.reason)
            val viaTail = route is CopyRoute.Tailnet
            _ui.update { it.copy(route = if (viaTail) "Tailnet" else "Public route") }
            log(if (viaTail) "Copying over the tailnet." else "Copying over the public route.")
            // 5. Send: a fresh key for this copy; only its public half crosses the phone.
            setStep(CopyStep.SEND)
            val before = fromApi.copyStatus()
            if (before.job == "send" && (before.phase == "pack" || before.phase == "transfer")) {
                throw ApiException(CODE_FAILED, "A copy is already running on ${f.host}. Wait for it to finish.")
            }
            val key = fromApi.copyKeygen()
            toApi.copyAuthorize(key.publicKey, if (viaTail) key.tailnetIp else "")
            _ui.update { it.copy(keyOnTo = true) }
            fromApi.copySend(s.kind, project, if (viaTail) tail else t.host, t.port, t.user, t.hostKeyType, t.hostKeyBlob, chosen)
            log("Sending ${chosen.size} item(s) from ${f.host}.")
            val done = poll(fromApi, f.host, "send", before.started)
            if (done.file.isBlank()) throw ApiException(CODE_FAILED, "${t.host} didn't say which file it received.")
            sentFile = done.file
            log("It reached ${t.host}.")
        }
        // 6. Apply on the To server.
        val file = sentFile ?: throw ApiException(CODE_STEP, "Send first.")
        setStep(CopyStep.APPLY)
        val before = toApi.copyStatus()
        // Once started, the job reports on its own (its file name is gone from the status when it ends): just wait for it.
        val resume = applyStarted
        if (!resume) {
            toApi.copyApply(file, copyApplyLines(diff, s.ticked))
            applyStarted = true
            log("Applying on ${t.host}.")
        }
        val done = try {
            poll(toApi, t.host, "apply", if (resume) null else before.started)
        } catch (e: ApiException) {
            if (e.code == CODE_FAILED) applyStarted = false   // the file stays on the server: Try again applies it again
            throw e
        }
        _ui.update { it.copy(step = CopyStep.DONE, result = done, keyOnTo = done.result?.keyRemoved != true && it.keyOnTo) }
        log("Done.")
    }

    /** Stops a copy that didn't finish: the To server removes the key and any file it received. */
    fun cancel() {
        val t = _ui.value.to ?: return
        generation++
        job?.cancel()
        tools.scope.launch {
            try {
                apiFor(t).copyCancel()
                sentFile = null
                applyStarted = false
                _ui.update { it.copy(keyOnTo = false, busy = false, error = null, step = if (it.step == CopyStep.DONE) it.step else CopyStep.CHOOSE) }
                log("Cancelled: the key on ${t.host} is removed.")
            } catch (e: ApiException) {
                fail(e)
            }
        }
    }
}
