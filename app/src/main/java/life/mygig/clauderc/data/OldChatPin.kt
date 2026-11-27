package life.mygig.clauderc.data

import android.content.Context
import java.security.KeyStore

/**
 * The chat PIN is gone (App lock is the protection). Older versions could keep it encrypted in the
 * `chat_pin` SharedPreferences under the Keystore key `clauderc_chat_pin`: delete both, never read
 * them. Cheap when they're already gone, so it simply runs at every start (off the main thread).
 */
fun deleteOldChatPin(context: Context) {
    runCatching { context.deleteSharedPreferences("chat_pin") }
    runCatching {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (ks.containsAlias("clauderc_chat_pin")) ks.deleteEntry("clauderc_chat_pin")
    }
}
