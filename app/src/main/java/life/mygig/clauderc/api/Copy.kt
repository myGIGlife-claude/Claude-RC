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

/** One file (or env name, or branch) in a `copy-list` answer. Names and sizes only, never values. */
@Serializable
data class CopyEntry(
    val path: String,
    val size: Long = 0,
    val mtime: Long = 0,
    val mode: Int = 0,
    /** Secrets: a keystore or Apple key file, only copied when the owner ticks it. */
    val optional: Boolean = false,
    /** Bundle: the branch's commit, sent back to the other server to ask which ones it already has. */
    val sha: String = "",
)

@Serializable
data class CopyList(val entries: List<CopyEntry> = emptyList(), val truncated: Boolean = false)

@Serializable
data class CopyKey(
    @SerialName("public_key") val publicKey: String = "",
    @SerialName("tailnet_ip") val tailnetIp: String = "",
)

@Serializable
data class CopyProbe(val reachable: Boolean = false, @SerialName("hostkey_ok") val hostkeyOk: Boolean = false)

/** `copy-status`: the migrate-status shape plus what the apply step reports (paths only). */
@Serializable
data class CopyStatus(
    /** send, apply or none. */
    val job: String = "none",
    /** transfer, apply, done or failed. */
    val phase: String = "",
    val message: String = "",
    val pct: Int = 0,
    val bytes: Long = 0,
    /** The copy file's name on the receiving server. */
    val file: String = "",
    val started: JsonElement = JsonNull,
    val updated: JsonElement = JsonNull,
    val error: String? = null,
    val skipped: List<String> = emptyList(),
    val refused: List<String> = emptyList(),
    @SerialName("key_removed") val keyRemoved: Boolean = false,
)

val COPY_FILE_RE = Regex("^copy-[A-Za-z0-9._-]{1,80}\\.tgz$")

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
