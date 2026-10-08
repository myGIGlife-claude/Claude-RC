package life.mygig.clauderc

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import life.mygig.clauderc.data.AppBackup
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AppBackupTest {
    @Test fun roundTripWritesV2() {
        val plain = "settings and key seed".toByteArray()
        val file = AppBackup.encrypt(plain, "correct horse".toCharArray())
        assertEquals("CLRCBK2", String(file, 0, 7, Charsets.US_ASCII))
        assertArrayEquals(plain, AppBackup.decrypt(file, "correct horse".toCharArray()))
    }

    @Test fun decryptsV1Fixture() {
        val plain = "old settings and key seed".toByteArray()
        val v1 = encryptV1Fixture(plain, "correct horse".toCharArray())
        assertEquals("CLRCBK1", String(v1, 0, 7, Charsets.US_ASCII))
        assertArrayEquals(plain, AppBackup.decrypt(v1, "correct horse".toCharArray()))
    }

    @Test fun wrongPassphraseFails() {
        val file = AppBackup.encrypt(byteArrayOf(1, 2, 3), "long enough passphrase".toCharArray())
        assertThrows(Exception::class.java) { AppBackup.decrypt(file, "another long wrong phrase".toCharArray()) }
    }

    @Test fun tamperedCiphertextFails() {
        val file = AppBackup.encrypt(byteArrayOf(1, 2, 3), "long enough passphrase".toCharArray())
        file[file.lastIndex] = (file.last().toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { AppBackup.decrypt(file, "long enough passphrase".toCharArray()) }
    }

    @Test fun headerMagicChecked() {
        val file = AppBackup.encrypt(byteArrayOf(1), "long enough passphrase".toCharArray())
        file[0] = 0
        assertThrows(IllegalArgumentException::class.java) { AppBackup.decrypt(file, "long enough passphrase".toCharArray()) }
    }

    @Test fun truncatedFileRejected() {
        val file = AppBackup.encrypt(byteArrayOf(1, 2, 3), "long enough passphrase".toCharArray()).copyOf(20)
        assertThrows(IllegalArgumentException::class.java) { AppBackup.decrypt(file, "long enough passphrase".toCharArray()) }
    }

    private fun encryptV1Fixture(plain: ByteArray, passphrase: CharArray): ByteArray {
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val spec = PBEKeySpec(passphrase, salt, 210_000, 256)
        val key = try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally { spec.clearPassword(); passphrase.fill('\u0000') }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return "CLRCBK1".toByteArray(Charsets.US_ASCII) + salt + iv + cipher.doFinal(plain)
    }
}
