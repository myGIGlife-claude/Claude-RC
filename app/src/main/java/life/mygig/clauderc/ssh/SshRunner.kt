package life.mygig.clauderc.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.UserInfo
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64

data class ServerConfig(
    val host: String,
    val port: Int,
    val user: String,
    /** Pinned host key: algorithm name and base64 wire blob. */
    val hostKeyType: String,
    val hostKeyBlob: String,
)

data class HostKeyInfo(val type: String, val blob: String) {
    val fingerprint: String get() = fingerprintOf(Base64.getDecoder().decode(blob))
}

fun fingerprintOf(blob: ByteArray): String =
    "SHA256:" + Base64.getEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(blob))

/** Why a call failed before the server could answer with JSON. */
class SshFailure(val kind: Kind, message: String, cause: Throwable? = null) : IOException(message, cause) {
    enum class Kind { HOST_KEY_CHANGED, AUTH_FAILED, NETWORK, TIMEOUT, NOT_CONFIGURED }
}

/**
 * One short SSH exec per call: connect, run one allowlisted action, read its
 * output, disconnect. The server's forced command ignores anything but the
 * action string, so this never gets (or asks for) a shell.
 */
object SshRunner {

    init {
        // JSch picks its Ed25519 / X25519 code from Java 15+ multi-release
        // classes, which Android ignores. Point it at the Bouncy Castle ones.
        JSch.setConfig("ssh-ed25519", "com.jcraft.jsch.bc.SignatureEd25519")
        JSch.setConfig("ssh-ed448", "com.jcraft.jsch.bc.SignatureEd448")
        JSch.setConfig("keypairgen.eddsa", "com.jcraft.jsch.bc.KeyPairGenEdDSA")
        JSch.setConfig("xdh", "com.jcraft.jsch.bc.XDH")
    }

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val DEFAULT_CALL_TIMEOUT_MS = 90_000L

    /**
     * Connect once without authenticating, only to learn the server's host key
     * so the user can compare the fingerprint before trusting it.
     */
    fun probeHostKey(host: String, port: Int, user: String, preferType: String? = null): HostKeyInfo {
        val jsch = JSch()
        val recorder = RecordingRepository()
        jsch.hostKeyRepository = recorder
        val session = jsch.getSession(user.ifBlank { "probe" }, host, port)
        session.setConfig("StrictHostKeyChecking", "yes")
        // Ask for the pinned key type first, so a server that has *added* a
        // key type isn't mistaken for one whose key changed.
        if (preferType != null) {
            val algs = if (preferType == "ssh-rsa") "rsa-sha2-512,rsa-sha2-256" else preferType
            session.setConfig("server_host_key", algs + "," + JSch.getConfig("server_host_key"))
        }
        session.setConfig("PreferredAuthentications", "none")
        try {
            session.connect(CONNECT_TIMEOUT_MS)
        } catch (e: JSchException) {
            recorder.seen?.let { return it }
            throw SshFailure(SshFailure.Kind.NETWORK, "Could not reach $host:$port (${e.message})", e)
        } finally {
            session.disconnect()
        }
        recorder.seen?.let { return it }
        throw SshFailure(SshFailure.Kind.NETWORK, "The server did not present a host key")
    }

    /**
     * Run [command] (becomes SSH_ORIGINAL_COMMAND on the server). [stdin] is
     * written and closed for actions that take secrets, so they never appear in
     * the command line.
     */
    fun exec(
        config: ServerConfig,
        identity: Ed25519Identity,
        command: String,
        stdin: String? = null,
        timeoutMs: Long = DEFAULT_CALL_TIMEOUT_MS,
    ): String {
        if (config.host.isBlank() || config.user.isBlank() || config.hostKeyBlob.isBlank()) {
            throw SshFailure(SshFailure.Kind.NOT_CONFIGURED, "Server settings are incomplete")
        }
        val jsch = JSch()
        jsch.addIdentity("clauderc", identity.privatePem(), identity.publicBlob, null)
        val pinned = PinnedRepository(config.host, config.port, config.hostKeyType, config.hostKeyBlob)
        jsch.hostKeyRepository = pinned

        val session = jsch.getSession(config.user, config.host, config.port)
        session.setConfig("StrictHostKeyChecking", "yes")
        session.setConfig("PreferredAuthentications", "publickey")
        // Only accept the pinned key type, so the server can't pick another one.
        session.setConfig(
            "server_host_key",
            if (config.hostKeyType == "ssh-rsa") "rsa-sha2-512,rsa-sha2-256" else config.hostKeyType,
        )
        session.userInfo = SilentUserInfo
        session.timeout = CONNECT_TIMEOUT_MS

        try {
            try {
                session.connect(CONNECT_TIMEOUT_MS)
            } catch (e: JSchException) {
                val msg = e.message.orEmpty()
                when {
                    pinned.mismatch -> throw SshFailure(
                        SshFailure.Kind.HOST_KEY_CHANGED,
                        "The server's host key has changed. Refusing to connect.",
                        e,
                    )
                    msg.contains("Auth fail", true) || msg.contains("auth cancel", true) ->
                        throw SshFailure(
                            SshFailure.Kind.AUTH_FAILED,
                            "The server didn't accept this phone's key.",
                            e,
                        )
                    msg.contains("Algorithm negotiation fail", true) && msg.contains("server_host_key", true) ->
                        throw SshFailure(
                            SshFailure.Kind.HOST_KEY_CHANGED,
                            "The server no longer offers the pinned host key type.",
                            e,
                        )
                    msg.contains("timeout", true) ->
                        throw SshFailure(SshFailure.Kind.TIMEOUT, "Timed out connecting to ${config.host}", e)
                    else -> throw SshFailure(
                        SshFailure.Kind.NETWORK,
                        "Could not connect to ${config.host}:${config.port} ($msg)",
                        e,
                    )
                }
            }

            val channel = session.openChannel("exec") as ChannelExec
            channel.setCommand(command)
            channel.setPty(false)
            val out = channel.inputStream
            val input = channel.outputStream
            channel.connect(CONNECT_TIMEOUT_MS)
            try {
                if (stdin != null) input.write((stdin + "\n").toByteArray())
                input.flush()
            } finally {
                input.close() // EOF, so nothing on the server waits for input
            }

            val buf = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            val deadline = System.currentTimeMillis() + timeoutMs
            while (true) {
                while (out.available() > 0) {
                    val n = out.read(chunk)
                    if (n < 0) break
                    buf.write(chunk, 0, n)
                }
                if (channel.isClosed && out.available() <= 0) break
                if (System.currentTimeMillis() > deadline) {
                    channel.disconnect()
                    throw SshFailure(SshFailure.Kind.TIMEOUT, "The server took too long to answer")
                }
                Thread.sleep(50)
            }
            channel.disconnect()
            return buf.toString(Charsets.UTF_8.name())
        } finally {
            session.disconnect()
        }
    }

    private object SilentUserInfo : UserInfo {
        override fun getPassphrase(): String? = null
        override fun getPassword(): String? = null
        override fun promptPassword(message: String?) = false
        override fun promptPassphrase(message: String?) = false
        override fun promptYesNo(message: String?) = false
        override fun showMessage(message: String?) {}
    }

    /** Accepts only the one pinned key; remembers if the server showed another. */
    private class PinnedRepository(
        host: String,
        port: Int,
        private val type: String,
        blob: String,
    ) : HostKeyRepository {
        private val pinned: ByteArray = Base64.getDecoder().decode(blob)
        private val hostId = if (port == 22) host else "[$host]:$port"
        var mismatch = false

        override fun check(host: String?, key: ByteArray?): Int {
            if (key != null && MessageDigest.isEqual(key, pinned)) return HostKeyRepository.OK
            mismatch = true
            return HostKeyRepository.CHANGED
        }

        override fun add(hostkey: HostKey?, ui: UserInfo?) {}
        override fun remove(host: String?, type: String?) {}
        override fun remove(host: String?, type: String?, key: ByteArray?) {}
        override fun getKnownHostsRepositoryID(): String = "clauderc-pinned"
        override fun getHostKey(): Array<HostKey> = arrayOf(HostKey(hostId, pinned))
        override fun getHostKey(host: String?, type: String?): Array<HostKey> =
            if (type == null || type == this.type) getHostKey() else emptyArray()
    }

    /** Trusts nothing; just records the key the server presented. */
    private class RecordingRepository : HostKeyRepository {
        var seen: HostKeyInfo? = null

        override fun check(host: String?, key: ByteArray?): Int {
            if (key != null) {
                seen = HostKeyInfo(keyType(key), Base64.getEncoder().encodeToString(key))
            }
            return HostKeyRepository.NOT_INCLUDED
        }

        override fun add(hostkey: HostKey?, ui: UserInfo?) {}
        override fun remove(host: String?, type: String?) {}
        override fun remove(host: String?, type: String?, key: ByteArray?) {}
        override fun getKnownHostsRepositoryID(): String = "clauderc-probe"
        override fun getHostKey(): Array<HostKey> = emptyArray()
        override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
    }

    /** The algorithm name is the first SSH string inside the key blob. */
    private fun keyType(blob: ByteArray): String {
        if (blob.size < 4) return "unknown"
        val len = ((blob[0].toInt() and 0xff) shl 24) or ((blob[1].toInt() and 0xff) shl 16) or
            ((blob[2].toInt() and 0xff) shl 8) or (blob[3].toInt() and 0xff)
        if (len <= 0 || 4 + len > blob.size) return "unknown"
        return String(blob, 4, len, Charsets.US_ASCII)
    }
}
