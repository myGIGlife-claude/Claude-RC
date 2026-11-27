package life.mygig.clauderc.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/** What the Copy screen can copy (docs/copy-design.md). */
enum class CopyKind(val wire: String, val label: String) {
    PROJECT("project", "A project folder"),
    SECRETS("secrets", "Keys and secrets"),
    BUNDLE("bundle", "Unpushed git work"),
}

/**
 * One entry of a `copy-list` answer. Names and sizes only, never values.
 * Files: size, mtime, [mode] = octal permissions as text ("644"). Secrets: `env/NAME`, `secret-files/x`, `keystores/x.jks`,
 * `apple/AuthKey.p8`. Bundle: path = branch, size = commits a remote lacks, mtime = commit time, [mode] = the commit's sha.
 */
@Serializable
data class CopyEntry(
    val path: String,
    val size: Long = 0,
    val mtime: Long = 0,
    val mode: String = "",
    /** Secrets: a keystore or Apple key file, only copied when the owner ticks it. */
    val optional: Boolean = false,
)

/** [exists] is only there for project and bundle lists: false = that project folder is missing on the server. */
@Serializable
data class CopyList(val entries: List<CopyEntry> = emptyList(), val truncated: Boolean = false, val exists: Boolean? = null)

/** The `copy-send` path that means "the whole folder, .git included" (the folder is missing on the other server). */
const val COPY_WHOLE_FOLDER = "."

@Serializable
data class CopyKey(
    @SerialName("public_key") val publicKey: String = "",
    @SerialName("tailnet_ip") val tailnetIp: String = "",
)

@Serializable
data class CopyProbe(val reachable: Boolean = false, @SerialName("hostkey_ok") val hostkeyOk: Boolean = false)

/** A path with the reason the server gave. */
@Serializable
data class CopyReason(val path: String, val reason: String = "")

/** `copy-status`'s `result`, written when copy-apply ends. Paths and names only. */
@Serializable
data class CopyResult(
    val copied: List<String> = emptyList(),
    val skipped: List<CopyReason> = emptyList(),
    val refused: List<CopyReason> = emptyList(),
    @SerialName("env_added") val envAdded: List<String> = emptyList(),
    /** Names that exist on the target with another value: never overwritten. */
    @SerialName("env_conflicts") val envConflicts: List<String> = emptyList(),
    /** Bundle branches now under refs/remotes/from-<source>/. */
    val fetched: List<String> = emptyList(),
    /** Folder on the target holding what was overwritten ("" = nothing was). */
    val backup: String = "",
    @SerialName("key_removed") val keyRemoved: Boolean = false,
)

/** `copy-status`: the migrate-status shape, plus [result] once copy-apply has ended. */
@Serializable
data class CopyStatus(
    /** send, apply or none. */
    val job: String = "none",
    /** pack, transfer (send); apply (apply); done or failed; none. */
    val phase: String = "",
    val message: String = "",
    val pct: Int = 0,
    val bytes: Long = 0,
    /** The copy file's name on the receiving server (set when the send is done and while the apply runs). */
    val file: String = "",
    val started: JsonElement = JsonNull,
    val updated: JsonElement = JsonNull,
    val error: String? = null,
    val result: CopyResult? = null,
)

/** Same pattern the server accepts (claude-setup.sh COPY_FILE_RE). */
val COPY_FILE_RE = Regex("^copy-[0-9]{8}-[0-9]{6}(-[0-9]{1,3})?\\.tgz$")

/** Which way the copy goes: the tailnet, the public address, or nowhere. */
sealed interface CopyRoute {
    /** [address] is the other server's tailnet address. */
    data class Tailnet(val address: String) : CopyRoute
    data object Public : CopyRoute
    /** Stop with [reason]; there is no fallback. */
    data class Stop(val reason: String) : CopyRoute
}

private const val WRONG_KEY = "The second server answered with a different host key than the one you trusted. Nothing was copied."

/**
 * Route from the probe answers (null = not probed, because that address isn't set).
 * A reachable address with another host key stops everything: it may not be the server the owner trusts.
 */
fun chooseCopyRoute(tailnet: CopyProbe?, public: CopyProbe?, tailnetAddress: String): CopyRoute {
    if (tailnet != null && tailnet.reachable) {
        return if (tailnet.hostkeyOk) CopyRoute.Tailnet(tailnetAddress) else CopyRoute.Stop(WRONG_KEY)
    }
    if (public == null || !public.reachable) return CopyRoute.Stop("The first server can't reach the second one. Check that it is on.")
    return if (public.hostkeyOk) CopyRoute.Public else CopyRoute.Stop(WRONG_KEY)
}

/** The three lists the owner reviews (paths only). */
data class CopyDiff(
    val missingOnTo: List<CopyEntry> = emptyList(),
    val newerOnFrom: List<CopyEntry> = emptyList(),
    /** Newer on the target, or the same time but a different size: only overwritten when ticked. */
    val newerOnTo: List<CopyEntry> = emptyList(),
) {
    val all: List<CopyEntry> get() = missingOnTo + newerOnFrom + newerOnTo
    val isEmpty: Boolean get() = all.isEmpty()
}

/** Compares by path; identical (same time and size) entries are left out. */
fun diffCopy(from: List<CopyEntry>, to: List<CopyEntry>): CopyDiff {
    val there = to.associateBy { it.path }
    val missing = ArrayList<CopyEntry>()
    val newerFrom = ArrayList<CopyEntry>()
    val newerTo = ArrayList<CopyEntry>()
    for (a in from) {
        val b = there[a.path]
        when {
            b == null -> missing += a
            a.mtime > b.mtime -> newerFrom += a
            a.mtime < b.mtime || a.size != b.size -> newerTo += a
        }
    }
    val order = compareBy<CopyEntry> { it.path }
    return CopyDiff(missing.sortedWith(order), newerFrom.sortedWith(order), newerTo.sortedWith(order))
}

/** Missing and newer-here are ticked; newer on the target and optional files (keystores, Apple keys) are not. */
fun defaultCopyTicks(d: CopyDiff): Set<String> =
    (d.missingOnTo + d.newerOnFrom).filter { !it.optional }.map { it.path }.toSet()

/** What `copy-apply` gets on stdin: the policy line, then the paths the owner agreed to overwrite. */
fun copyApplyLines(d: CopyDiff, ticked: Set<String>): List<String> {
    val policy = if (d.newerOnFrom.any { it.path in ticked }) "newer" else "new-only"
    return listOf(policy) + d.newerOnTo.filter { it.path in ticked }.map { it.path }
}

/** A path that can travel as one stdin line. */
fun copyPathOk(path: String) = path.isNotEmpty() && path.none { it == '\n' || it == '\r' || it == '\u0000' }
