package life.mygig.clauderc.data

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The chat PIN while "don't ask again for" is on, so it survives the app being
 * closed. Encrypted with an Android Keystore key; tied to this boot (the boot
 * count must match and the phone's uptime must be before the deadline), so a
 * restart forfeits the rest of the window.
 */
class ChatPinVault(private val context: Context) {

    private val prefs = context.getSharedPreferences("chat_pin", Context.MODE_PRIVATE)

    fun save(pin: String, minutes: Int) {
        // No boot count on this phone: a restart couldn't be told apart, so keep nothing.
        if (bootCount() < 0) { clear(); return }
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val enc = cipher.iv + cipher.doFinal(pin.toByteArray())
            prefs.edit()
                .putString(PREF_PIN, Base64.encodeToString(enc, Base64.NO_WRAP))
                .putInt(PREF_BOOT, bootCount())
                .putLong(PREF_UNTIL, SystemClock.elapsedRealtime() + minutes * 60_000L)
                .commit()
        }.onFailure { clear() }
    }

    /** The PIN, or null when there's none, its time ran out, or the phone restarted. */
    fun load(): String? {
        val blob = prefs.getString(PREF_PIN, null) ?: return null
        if (prefs.getInt(PREF_BOOT, -1) != bootCount() || SystemClock.elapsedRealtime() >= prefs.getLong(PREF_UNTIL, 0)) {
            clear()
            return null
        }
        return runCatching {
            val b = Base64.decode(blob, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, b, 0, IV_LEN))
            String(cipher.doFinal(b, IV_LEN, b.size - IV_LEN))
        }.getOrElse { clear(); null }
    }

    fun clear() { prefs.edit().clear().commit() }

    private fun bootCount(): Int = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "clauderc_chat_pin"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LEN = 12
        const val PREF_PIN = "pin_enc"
        const val PREF_BOOT = "boot"
        const val PREF_UNTIL = "until"
    }
}
