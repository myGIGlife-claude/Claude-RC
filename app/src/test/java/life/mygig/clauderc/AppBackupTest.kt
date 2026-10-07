package life.mygig.clauderc

import life.mygig.clauderc.data.AppBackup
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AppBackupTest {
    @Test fun roundTrip() {
        val plain = "settings and key seed".toByteArray()
        assertArrayEquals(plain, AppBackup.decrypt(AppBackup.encrypt(plain, "correct horse".toCharArray()), "correct horse".toCharArray()))
    }
    @Test fun wrongPassphraseFails() {
        val file = AppBackup.encrypt(byteArrayOf(1, 2, 3), "passphrase".toCharArray())
        assertThrows(Exception::class.java) { AppBackup.decrypt(file, "wrongphrase".toCharArray()) }
    }
    @Test fun tamperedCiphertextFails() {
        val file = AppBackup.encrypt(byteArrayOf(1, 2, 3), "passphrase".toCharArray())
        file[file.lastIndex] = (file.last().toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { AppBackup.decrypt(file, "passphrase".toCharArray()) }
    }
    @Test fun headerMagicChecked() {
        val file = AppBackup.encrypt(byteArrayOf(1), "passphrase".toCharArray())
        file[0] = 0
        assertThrows(IllegalArgumentException::class.java) { AppBackup.decrypt(file, "passphrase".toCharArray()) }
    }
}
