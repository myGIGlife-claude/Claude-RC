package life.mygig.clauderc.api

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** What's newest on GitHub: the last commit that changed server/, and the latest-main APK. */
data class Latest(val serverCommit: String?, val appVersionCode: Int?, val apkUrl: String?)

object Updates {
    const val REPO = "myGIGlife-claude/Claude-RC"

    /** Oldest server script API this app works with (claude-setup.sh SCRIPT_API). */
    const val MIN_SCRIPT_API = 24

    /** What the server accepts as a custom key name (claude-setup.sh CUSTOM_NAME_RE). */
    val CUSTOM_NAME = Regex("^[A-Z][A-Z0-9_]{0,55}_(KEY|TOKEN|SECRET|PASSWORD|USERNAME|USER|SERVER|HOST|URL|ID|EMAIL|REGION|PROJECT|ENDPOINT|ORG|ACCOUNT)$")

    /** Downloads and installs the server scripts (no clone), authorizing [key] if given. Rerun it to update. */
    fun installCommand(key: String? = null) =
        "curl -fsSL https://raw.githubusercontent.com/$REPO/main/server/install.sh | bash" +
            (key?.let { " -s -- '$it'" } ?: "")

    /** Two unauthenticated GitHub API calls; GitHub allows 60 an hour per IP, we make 2. */
    suspend fun fetch(): Latest = withContext(Dispatchers.IO) {
        val commits = get("https://api.github.com/repos/$REPO/commits?path=server&per_page=1") as? JsonArray
        val serverCommit = commits?.firstOrNull()?.jsonObject?.get("sha")?.jsonPrimitive?.content
        val release = get("https://api.github.com/repos/$REPO/releases/tags/latest-main") as? JsonObject
        // CI names the APK cLaudeRC-<version>-build.<run>.apk and uses versionCode 100 + run.
        val apk = release?.get("assets")?.jsonArray?.map { it.jsonObject }
            ?.firstOrNull { it["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true }
        val run = apk?.get("name")?.jsonPrimitive?.content
            ?.let { Regex("""build\.(\d+)\.apk$""").find(it)?.groupValues?.get(1)?.toIntOrNull() }
        Latest(serverCommit, run?.let { 100 + it }, apk?.get("browser_download_url")?.jsonPrimitive?.content)
    }

    private fun get(url: String) = (URL(url).openConnection() as HttpURLConnection).run {
        connectTimeout = 10_000
        readTimeout = 10_000
        setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (responseCode != 200) null else Json.parseToJsonElement(inputStream.bufferedReader().use { it.readText() })
        } finally {
            disconnect()
        }
    }
}
