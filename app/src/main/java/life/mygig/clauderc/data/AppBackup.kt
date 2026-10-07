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
    private val magic = "CLRCBK1".toByteArray(Charsets.US_ASCII)
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val ITERATIONS = 210_000

    fun encrypt(plain: ByteArray, passphrase: CharArray): ByteArray {
        require(passphrase.size >= 8) { "Passphrase must be at least 8 characters." }
        val salt = ByteArray(SALT_SIZE).also(SecureRandom()::nextBytes)
        val iv = ByteArray(IV_SIZE).also(SecureRandom()::nextBytes)
        val key = derive(passphrase, salt)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            magic + salt + iv + cipher.doFinal(plain)
        } finally { passphrase.fill('\u0000') }
    }

    fun decrypt(file: ByteArray, passphrase: CharArray): ByteArray {
        require(passphrase.size >= 8) { "Passphrase must be at least 8 characters." }
        try {
            require(file.size >= magic.size + SALT_SIZE + IV_SIZE + 16) { "Backup file is incomplete." }
            require(file.copyOfRange(0, magic.size).contentEquals(magic)) { "This is not a cLaudeRC app backup." }
            val salt = file.copyOfRange(magic.size, magic.size + SALT_SIZE)
            val iv = file.copyOfRange(magic.size + SALT_SIZE, magic.size + SALT_SIZE + IV_SIZE)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, derive(passphrase, salt), GCMParameterSpec(128, iv))
            return cipher.doFinal(file, magic.size + SALT_SIZE + IV_SIZE, file.size - magic.size - SALT_SIZE - IV_SIZE)
        } finally { passphrase.fill('\u0000') }
    }

    private fun derive(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, 256)
        return try { SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES") }
        finally { spec.clearPassword() }
    }
}

@Serializable
data class AppBackupPayload(val servers: List<Server>, val activeId: String, val appLock: Boolean, val notify: Boolean, val theme: String, val sshSeed: String)

val appBackupJson = Json { encodeDefaults = true }
