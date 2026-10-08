package life.mygig.clauderc.data

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Passphrase-encrypted app backup container: magic, salt, IV, authenticated ciphertext. */
object AppBackup {
    private val magicV1 = "CLRCBK1".toByteArray(Charsets.US_ASCII)
    private val magicV2 = "CLRCBK2".toByteArray(Charsets.US_ASCII)
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val ITERATIONS_V1 = 210_000
    private const val ITERATIONS_V2 = 600_000

    fun encrypt(plain: ByteArray, passphrase: CharArray): ByteArray {
        require(passphrase.size >= 12) { "Passphrase must be at least 12 characters." }
        val salt = ByteArray(SALT_SIZE).also(SecureRandom()::nextBytes)
        val iv = ByteArray(IV_SIZE).also(SecureRandom()::nextBytes)
        val key = derive(passphrase, salt, ITERATIONS_V2)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            magicV2 + salt + iv + cipher.doFinal(plain)
        } finally { passphrase.fill('\u0000') }
    }

    fun decrypt(file: ByteArray, passphrase: CharArray): ByteArray {
        require(passphrase.size >= 12) { "Passphrase must be at least 12 characters." }
        try {
            val magic = when {
                file.size >= magicV2.size && file.copyOfRange(0, magicV2.size).contentEquals(magicV2) -> magicV2
                file.size >= magicV1.size && file.copyOfRange(0, magicV1.size).contentEquals(magicV1) -> magicV1
                else -> throw IllegalArgumentException("This is not a cLaudeRC app backup.")
            }
            require(file.size >= magic.size + SALT_SIZE + IV_SIZE + 16) { "Backup file is incomplete." }
            val iterations = if (magic === magicV2) ITERATIONS_V2 else ITERATIONS_V1
            val salt = file.copyOfRange(magic.size, magic.size + SALT_SIZE)
            val iv = file.copyOfRange(magic.size + SALT_SIZE, magic.size + SALT_SIZE + IV_SIZE)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, derive(passphrase, salt, iterations), GCMParameterSpec(128, iv))
            return cipher.doFinal(file, magic.size + SALT_SIZE + IV_SIZE, file.size - magic.size - SALT_SIZE - IV_SIZE)
        } finally { passphrase.fill('\u0000') }
    }

    private fun derive(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, 256)
        return try { SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES") }
        finally { spec.clearPassword() }
    }
}

@Serializable
data class AppBackupPayload(val servers: List<Server>, val activeId: String, val appLock: Boolean, val notify: Boolean, val theme: String, val sshSeed: String)

val appBackupJson = Json { encodeDefaults = true }
