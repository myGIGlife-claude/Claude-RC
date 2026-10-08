package life.mygig.clauderc.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import life.mygig.clauderc.api.ApiException
import life.mygig.clauderc.api.Codes

/** Plain-language message, button label and fix for each error code. */
fun friendly(e: ApiException): Triple<String, String?, Fix?> = when (e.code) {
    Codes.NOT_LOGGED_IN_CLAUDE -> Triple(e.message, "Log in", Fix.Login(LoginKind.CLAUDE))
    Codes.NOT_LOGGED_IN_GITHUB -> Triple(e.message, "Log in", Fix.Login(LoginKind.GITHUB))
    Codes.MISSING_SCOPES ->
        Triple(
            "The GitHub token is missing: ${e.missing.joinToString(", ").ifEmpty { "some scopes" }}.",
            "New token",
            Fix.Login(LoginKind.GITHUB),
        )
    Codes.NOT_LOGGED_IN_AWS -> Triple(e.message, "Log in", Fix.Login(LoginKind.AWS))
    Codes.REPO_EXISTS -> Triple(e.message, "Projects", Fix.GoTo(Tab.PROJECTS))
    Codes.FOLDER_DIRTY -> {
        val project = e.path?.substringAfterLast('/')
        Triple(
            "The folder has uncommitted changes, so it wasn't updated. Start anyway?",
            if (project != null) "Start anyway" else null,
            project?.let { Fix.StartAnyway(it) },
        )
    }
    Codes.INVALID_NAME -> Triple(e.message, null, null)
    Codes.BUSY -> Triple("The server is busy with another create/open. Try again in a moment.", null, null)
    Codes.FORBIDDEN ->
        Triple("The server refused this action. Update claude-launcher-api on the server.", null, null)
    Codes.HOST_KEY_CHANGED ->
        Triple(
            "The server's host key changed, so the app refused to connect. " +
                "If you didn't reinstall the server, someone may be intercepting the connection.",
            "Settings",
            Fix.OpenSettings,
        )
    Codes.AUTH_FAILED ->
        Triple(
            "The server didn't accept this phone's key. Run install-launcher-key.sh with the key from Settings.",
            "Settings",
            Fix.OpenSettings,
        )
    Codes.NOT_CONFIGURED -> Triple("Set up the server first.", "Settings", Fix.OpenSettings)
    Codes.KEY_ERROR -> Triple(e.message, "Settings", Fix.OpenSettings)
    Codes.NETWORK -> Triple("Can't reach the server: ${e.message}", null, null)
    Codes.TIMEOUT -> Triple("The server took too long to answer.", null, null)
    else -> Triple(e.message, null, null)
}

const val CLAUDE_PACKAGE = "com.anthropic.claude"

/** Launch the Claude app so the user can pick the Remote Control session; fall back to the web. */
fun openInClaude(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(CLAUDE_PACKAGE)
    if (launch != null) {
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
    } else {
        openUrl(context, "https://claude.ai/code")
    }
}

fun openUrl(context: Context, url: String) {
    val uri = Uri.parse(url)
    if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrEmpty()) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    }
}

fun formatUptime(seconds: Long): String {
    val d = seconds / 86_400
    val h = (seconds % 86_400) / 3_600
    val m = (seconds % 3_600) / 60
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${m}m"
        else -> "${m}m"
    }
}
