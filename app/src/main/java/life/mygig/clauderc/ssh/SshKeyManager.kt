package life.mygig.clauderc.ssh

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The app's own Ed25519 key. The 32-byte private seed is encrypted with an
 * AES-GCM key that lives in the Android Keystore and never leaves it; only the
 * ciphertext is stored in app-private preferences. The private key is never
 * exported, shown or backed up.
 */
class SshKeyManager(context: Context) {

    private val prefs = context.getSharedPreferences("ssh_key", Context.MODE_PRIVATE)

    /** Public key in authorized_keys format, generating the key on first use. */
    @Synchronized
    fun publicKey(): String {
        prefs.getString(PREF_PUB, null)?.let { return it }
        return generate()
    }

    /** Throw away the current key and make a new one. */
    @Synchronized
    fun regenerate(): String = generate()

    /** Decrypted identity for a single connection. */
    @Synchronized
    fun identity(): Ed25519Identity {
        publicKey()
        val blob = Base64.decode(prefs.getString(PREF_SEED, null), Base64.NO_WRAP)
        val iv = blob.copyOfRange(0, IV_LEN)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, iv))
        val seed = cipher.doFinal(blob, IV_LEN, blob.size - IV_LEN)
        try {
            return Ed25519Identity(seed)
        } finally {
            seed.fill(0)
        }
    }

    private fun generate(): String {
        val seed = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val identity = Ed25519Identity(seed)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        val enc = cipher.iv + cipher.doFinal(seed)
        seed.fill(0)
        val pub = identity.authorizedKey(COMMENT)
        prefs.edit()
            .putString(PREF_SEED, Base64.encodeToString(enc, Base64.NO_WRAP))
            .putString(PREF_PUB, pub)
            .commit()
        return pub
    }

    private fun wrappingKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .apply {
                    // The key can't be used while the phone is locked.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setUnlockedDeviceRequired(true)
                }
                .build(),
        )
        return gen.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "clauderc_ssh_wrap"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LEN = 12
        const val PREF_SEED = "seed_enc"
        const val PREF_PUB = "public_key"
        const val COMMENT = "clauderc"
    }
}
