package life.mygig.clauderc.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.UIKeyboardInteractive
import com.jcraft.jsch.UserInfo
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class ServerConfig(
    val host: String,
    val port: Int,
    val user: String,
    /** Pinned host key: algorithm name and base64 wire blob. */
    val hostKeyType: String,
    val hostKeyBlob: String,
    /** Optional second address of the same server (its Tailscale address), tried first; "" = none. */
    val tailnetHost: String = "",
)

/**
 * Which addresses [SshRunner] dials for [cfg], in order. With a Tailscale address it goes first and [ServerConfig.host]
 * is the fallback, unless [remembered] (the address that worked last, for a few minutes) is the normal host: then
 * straight there. The same host key is pinned for every address.
 */
internal fun addressesToTry(cfg: ServerConfig, remembered: String?): List<String> {
    val alt = cfg.tailnetHost.trim()
    if (alt.isEmpty() || alt.equals(cfg.host, ignoreCase = true)) return listOf(cfg.host)
    return if (remembered == cfg.host) listOf(cfg.host) else listOf(alt, cfg.host)
}

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

/** The user stopped a password setup run. */
class SshCancelled : IOException("Cancelled")

/** How a failed password login reads; the key-based [SshRunner.exec] keeps its own wording. */
fun passwordConnectFailure(msg: String, hostKeyMismatch: Boolean, host: String, port: Int, cause: Throwable? = null): SshFailure = when {
    hostKeyMismatch -> SshFailure(SshFailure.Kind.HOST_KEY_CHANGED, "The server's host key is not the one you confirmed. Refusing to connect.", cause)
    msg.contains("Auth fail", true) || msg.contains("auth cancel", true) || msg.contains("USERAUTH fail", true) ||
        msg.contains("Too many authentication failures", true) ->
        SshFailure(SshFailure.Kind.AUTH_FAILED, "Wrong user name or password.", cause)
    msg.contains("Algorithm negotiation fail", true) && msg.contains("server_host_key", true) ->
        SshFailure(SshFailure.Kind.HOST_KEY_CHANGED, "The server no longer offers the host key type you confirmed.", cause)
    msg.contains("timeout", true) || msg.contains("timed out", true) -> SshFailure(SshFailure.Kind.TIMEOUT, "Timed out connecting to $host", cause)
    else -> SshFailure(SshFailure.Kind.NETWORK, "Could not connect to $host:$port ($msg)", cause)
}

/**
 * What a cached connection belongs to: another server, pinned host key or phone key means another
 * connection, so a re-pinned key or a regenerated phone key is always checked by a fresh handshake.
 */
internal data class SshConnectionKey(
    val host: String,
    val port: Int,
    val user: String,
    val hostKeyType: String,
    val hostKeyBlob: String,
    /** Base64 of the phone's public key blob (a ByteArray would compare by identity). */
    val identityBlob: String,
    /** Part of the server's settings: editing it starts a fresh connection (and forgets which address worked). */
    val tailnetHost: String = "",
) {
    companion object {
        fun of(config: ServerConfig, identity: Ed25519Identity) = SshConnectionKey(
            config.host, config.port, config.user, config.hostKeyType, config.hostKeyBlob,
            Base64.getEncoder().encodeToString(identity.publicBlob), config.tailnetHost.trim(),
        )
    }
}

/**
 * Whether a channel that failed to open may be tried once more on a fresh connection: only when the
 * connection was a reused one, only once, and only for JSch's channel-open failures (sent before the
 * exec request). "channel request: timeout" / "failed to send channel request" mean the command may
 * already run on the server, so they never retry.
 */
internal fun mayRetryOnFreshConnection(reused: Boolean, attempt: Int, message: String?): Boolean =
    reused && attempt == 0 && (message == "session is down" || message == "channel is not opened.")

/**
 * One SSH exec channel per call on a cached connection: run one allowlisted action,
 * read its output, close the channel. The server's forced command ignores anything
 * but the action string, so this never gets (or asks for) a shell.
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
     *
     * Each call is its own exec channel on a shared, cached connection (one per
     * [SshConnectionKey]), so parallel screens don't each pay a TCP+SSH handshake.
     * [timeoutMs] counts from when the connection is ready and covers waiting for
     * a free channel plus the answer.
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
        val key = SshConnectionKey.of(config, identity)
        var deadline = 0L
        var attempt = 0
        while (true) {
            val (pooled, reused) = lease(key, config, identity)
            try {
                if (deadline == 0L) deadline = System.currentTimeMillis() + timeoutMs
                val wait = deadline - System.currentTimeMillis()
                if (wait <= 0 || !pooled.slots.tryAcquire(wait, TimeUnit.MILLISECONDS)) {
                    throw SshFailure(SshFailure.Kind.TIMEOUT, "The server is busy with other calls. Try again.")
                }
                try {
                    val openTimeout = if (reused) REUSED_OPEN_TIMEOUT_MS else CONNECT_TIMEOUT_MS
                    return runChannel(pooled.session, command, stdin, openTimeout, deadline)
                } catch (e: ChannelOpenFailed) {
                    // Nothing reached the server's command yet. A reused connection that can't open a
                    // channel is likely dead (network change, server restart): stop handing it out.
                    if (reused) retire(key, pooled)
                    if (!mayRetryOnFreshConnection(reused, attempt, e.cause.message)) throw e.cause
                    attempt++
                } finally {
                    pooled.slots.release()
                }
            } finally {
                release(pooled)
            }
        }
    }

    /**
     * The phone changed networks: stop handing out cached connections (their socket may be on the old network)
     * so the next call opens a fresh one. Calls still running on them finish or fail on their own.
     */
    fun retireAll() {
        val idle = synchronized(poolLock) {
            generation++   // a connect in progress may be on the old network too: use it for its own call only
            workedAt.clear()   // on another network the other address may be the quick one
            pool.entries.toList().filter { (k, p) -> retireLocked(k, p) }.map { it.value }
        }
        if (idle.isEmpty()) return
        Thread { idle.forEach { runCatching { it.session.disconnect() } } }.apply { isDaemon = true; start() }
    }

    /** Close every cached connection (server switched, removed or changed). Calls still on them fail. */
    fun closeAll() {
        val all = synchronized(poolLock) {
            generation++
            workedAt.clear()
            val list = pool.values.toList()
            pool.clear()
            list.forEach { it.retired = true; it.closed = true }
            stopSweeperIfEmpty()
            list
        }
        if (all.isEmpty()) return
        // Off the caller's thread: the UI calls this, and disconnect writes to the socket.
        Thread { all.forEach { runCatching { it.session.disconnect() } } }.apply { isDaemon = true; start() }
    }

    private const val MAX_CHANNELS = 8   // server sshd MaxSessions is 10
    private const val REUSED_OPEN_TIMEOUT_MS = 10_000   // a dead cached connection costs this before the retry
    private const val IDLE_CLOSE_MS = 60_000L
    private const val KEEPALIVE_MS = 15_000
    private const val KEEPALIVE_COUNT = 2

    private class Pooled(val session: Session) {
        val slots = Semaphore(MAX_CHANNELS)
        // Guarded by poolLock.
        var users = 0
        var lastUsed = System.currentTimeMillis()
        var retired = false
        var closed = false
    }

    private data class Lease(val pooled: Pooled, val reused: Boolean)

    /** Thrown before the command reached the server; carries JSch's own exception. */
    private class ChannelOpenFailed(override val cause: JSchException) : Exception(cause)

    private val poolLock = Any()
    private val pool = HashMap<SshConnectionKey, Pooled>()
    private var generation = 0
    private var sweeper: Timer? = null
    // One connect at a time per key, so parallel callers share it instead of each opening one.
    private val connectLocks = ConcurrentHashMap<SshConnectionKey, Any>()

    private const val TAILNET_CONNECT_TIMEOUT_MS = 4_000
    private const val REMEMBER_ADDRESS_MS = 5 * 60_000L
    private data class Worked(val address: String, val at: Long)
    // Guarded by poolLock: which address of a server connected last, so the next connect goes straight there.
    private val workedAt = HashMap<SshConnectionKey, Worked>()

    private fun rememberedAddress(key: SshConnectionKey): String? = synchronized(poolLock) {
        workedAt[key]?.takeIf { System.currentTimeMillis() - it.at < REMEMBER_ADDRESS_MS }?.address
    }

    private fun lease(key: SshConnectionKey, config: ServerConfig, identity: Ed25519Identity): Lease {
        synchronized(connectLocks.computeIfAbsent(key) { Any() }) {
            var gen = 0
            var stale: Pooled? = null
            synchronized(poolLock) {
                val p = pool[key]
                val now = System.currentTimeMillis()
                // Idle too long counts as stale too: the sweeper may not have run (phone asleep).
                if (p != null && !p.retired && p.session.isConnected && (p.users > 0 || now - p.lastUsed < IDLE_CLOSE_MS)) {
                    p.users++
                    p.lastUsed = now
                    return Lease(p, reused = true)
                }
                if (p != null && retireLocked(key, p)) stale = p
                gen = generation
            }
            stale?.let { runCatching { it.session.disconnect() } }
            val remembered = rememberedAddress(key)
            val (session, address) = connect(config, identity, remembered)
            val fresh = Pooled(session)
            synchronized(poolLock) {
                fresh.users++
                // closeAll ran during the connect: use it for this call only (and don't remember its address).
                if (gen == generation) {
                    pool[key] = fresh; startSweeper()
                    // Only a change restarts the clock, so a skipped Tailscale address is tried again after a while.
                    if (address != remembered) workedAt[key] = Worked(address, System.currentTimeMillis())
                } else fresh.retired = true
            }
            return Lease(fresh, reused = false)
        }
    }

    private fun release(p: Pooled) {
        val close = synchronized(poolLock) {
            p.users--
            p.lastUsed = System.currentTimeMillis()
            (p.retired && p.users == 0 && !p.closed).also { if (it) p.closed = true }
        }
        if (close) runCatching { p.session.disconnect() }
    }

    private fun retire(key: SshConnectionKey, p: Pooled) {
        if (synchronized(poolLock) { retireLocked(key, p) }) runCatching { p.session.disconnect() }
    }

    /** Stops handing [p] out; true if the caller must disconnect it now (no call is using it). */
    private fun retireLocked(key: SshConnectionKey, p: Pooled): Boolean {
        p.retired = true
        if (pool[key] === p) pool.remove(key)
        stopSweeperIfEmpty()
        return (p.users == 0 && !p.closed).also { if (it) p.closed = true }
    }

    private fun startSweeper() {
        if (sweeper != null) return
        sweeper = Timer("ssh-idle", true).apply {
            schedule(object : TimerTask() { override fun run() = sweepIdle() }, KEEPALIVE_MS.toLong(), KEEPALIVE_MS.toLong())
        }
    }

    private fun stopSweeperIfEmpty() {
        if (pool.isEmpty()) { sweeper?.cancel(); sweeper = null }
    }

    private fun sweepIdle() {
        val idle = synchronized(poolLock) {
            val now = System.currentTimeMillis()
            pool.entries.map { it.key to it.value }
                .filter { (_, p) -> p.users == 0 && (now - p.lastUsed >= IDLE_CLOSE_MS || !p.session.isConnected) }
                .filter { (k, p) -> retireLocked(k, p) }
                .map { it.second }
                .also { stopSweeperIfEmpty() }
        }
        idle.forEach { runCatching { it.session.disconnect() } }
    }

    /**
     * A connected, authenticated session to [config] and the address it used. Each address in [addressesToTry]
     * is dialled in turn (a Tailscale address with a short timeout, the last one with the normal one); any failure
     * moves on to the next EXCEPT a host-key mismatch, which stops at once. Every address must show the same
     * pinned key. When all fail, the normal host's error is the one thrown.
     */
    private fun connect(config: ServerConfig, identity: Ed25519Identity, remembered: String?): Pair<Session, String> {
        val addresses = addressesToTry(config, remembered)
        var last: SshFailure? = null
        for ((i, address) in addresses.withIndex()) {
            val isLast = i == addresses.lastIndex
            try {
                return connectTo(config, address, identity, if (isLast) CONNECT_TIMEOUT_MS else TAILNET_CONNECT_TIMEOUT_MS) to address
            } catch (e: SshFailure) {
                if (e.kind == SshFailure.Kind.HOST_KEY_CHANGED || isLast) throw e
                last = e
            }
        }
        throw last ?: SshFailure(SshFailure.Kind.NOT_CONFIGURED, "Server settings are incomplete")
    }

    /** One connect to [address] (the server's host or its Tailscale address) with the pinned host key; same checks and wording as ever. */
    private fun connectTo(config: ServerConfig, address: String, identity: Ed25519Identity, connectTimeoutMs: Int): Session {
        val jsch = JSch()
        jsch.addIdentity("clauderc", identity.privatePem(), identity.publicBlob, null)
        // Same pinned key whichever address is dialled: check() compares the key bytes only.
        val pinned = PinnedRepository(address, config.port, config.hostKeyType, config.hostKeyBlob)
        jsch.hostKeyRepository = pinned
        val viaTailnet = address != config.host

        val session = jsch.getSession(config.user, address, config.port)
        session.setConfig("StrictHostKeyChecking", "yes")
        session.setConfig("PreferredAuthentications", "publickey")
        // Only accept the pinned key type, so the server can't pick another one.
        session.setConfig(
            "server_host_key",
            if (config.hostKeyType == "ssh-rsa") "rsa-sha2-512,rsa-sha2-256" else config.hostKeyType,
        )
        session.userInfo = SilentUserInfo
        // Chat histories are large, repetitive JSON: compress when the server agrees (after auth, OpenSSH's default).
        session.setConfig("compression.s2c", "zlib@openssh.com,none")
        session.setConfig("compression.c2s", "zlib@openssh.com,none")
        // This is also the socket read timeout once connected (JSch's setTimeout): each quiet 15 s sends a
        // keepalive, and two unanswered in a row drop the session, so a dead link is noticed in ~45 s.
        session.setServerAliveInterval(KEEPALIVE_MS)
        session.setServerAliveCountMax(KEEPALIVE_COUNT)
        session.setDaemonThread(true)

        try {
            session.connect(connectTimeoutMs)
            return session
        } catch (e: JSchException) {
            session.disconnect()
            val msg = e.message.orEmpty()
            when {
                pinned.mismatch -> throw SshFailure(
                    SshFailure.Kind.HOST_KEY_CHANGED,
                    if (viaTailnet) "The host key at the Tailscale address $address is not the pinned one. Refusing to connect."
                    else "The server's host key has changed. Refusing to connect.",
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
                    throw SshFailure(SshFailure.Kind.TIMEOUT, "Timed out connecting to $address", e)
                else -> throw SshFailure(
                    SshFailure.Kind.NETWORK,
                    "Could not connect to $address:${config.port} ($msg)",
                    e,
                )
            }
        }
    }

    /** One exec channel on [session]: command, stdin then EOF, output to EOF. Only ever closes the channel. */
    private fun runChannel(session: Session, command: String, stdin: String?, openTimeoutMs: Int, deadline: Long): String {
        val channel = try {
            session.openChannel("exec") as? ChannelExec
        } catch (e: JSchException) {
            throw ChannelOpenFailed(e)
        } ?: throw ChannelOpenFailed(JSchException("session is down"))
        try {
            channel.setCommand(command)
            channel.setPty(false)
            val out = channel.inputStream
            val input = channel.outputStream
            try {
                channel.connect(openTimeoutMs)
            } catch (e: JSchException) {
                throw ChannelOpenFailed(e)
            }
            try {
                if (stdin != null) input.write((stdin + "\n").toByteArray())
                input.flush()
            } finally {
                input.close() // EOF, so nothing on the server waits for input
            }

            // Blocking reads, not available()+sleep polling: JSch's pipe writer only
            // wakes when a reader finds it empty, else after 1 s, so polling capped
            // answers at ~32 KB/s (a 3 MB file took over 2 minutes).
            // On timeout the watchdog closes the channel (never the shared session), which ends the read.
            val buf = ByteArrayOutputStream()
            val chunk = ByteArray(65536)
            val timedOut = AtomicBoolean(false)
            val watchdog = Timer(true)
            watchdog.schedule(object : TimerTask() {
                override fun run() { timedOut.set(true); channel.disconnect() }
            }, maxOf(1L, deadline - System.currentTimeMillis()))
            try {
                while (true) {
                    val n = out.read(chunk)
                    if (n < 0) break
                    buf.write(chunk, 0, n)
                }
            } catch (e: IOException) {
                if (!timedOut.get()) throw e
            } finally {
                watchdog.cancel()
            }
            if (timedOut.get()) throw SshFailure(SshFailure.Kind.TIMEOUT, "The server took too long to answer")
            return buf.toString(Charsets.UTF_8.name())
        } finally {
            channel.disconnect()
        }
    }

    /** What a password-login command printed and its exit status (-1 when the server didn't say). */
    data class PasswordExecResult(val output: String, val exitStatus: Int)

    /**
     * One command over a PASSWORD login, only for "Connect with a password" (setting up a server that doesn't
     * know this phone's key yet). The host key must be the one the user confirmed ([pinned]); the password is
     * offered once per call (to "password" or "keyboard-interactive", whichever the server asks for), never
     * stored and never logged. [onOutput] gets the output as it arrives; [isCancelled] is polled to stop early.
     */
    fun execWithPassword(
        host: String,
        port: Int,
        user: String,
        password: String,
        pinned: HostKeyInfo,
        command: String,
        stdin: String? = null,
        timeoutMs: Long = DEFAULT_CALL_TIMEOUT_MS,
        onOutput: (String) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): PasswordExecResult {
        if (host.isBlank() || user.isBlank() || password.isEmpty()) {
            throw SshFailure(SshFailure.Kind.NOT_CONFIGURED, "Enter the address, user name and password")
        }
        val jsch = JSch()
        val repo = PinnedRepository(host, port, pinned.type, pinned.blob)
        jsch.hostKeyRepository = repo
        val session = jsch.getSession(user, host, port)
        session.setConfig("StrictHostKeyChecking", "yes")
        session.setConfig("PreferredAuthentications", "password,keyboard-interactive")
        session.setConfig("server_host_key", if (pinned.type == "ssh-rsa") "rsa-sha2-512,rsa-sha2-256" else pinned.type)
        session.userInfo = OncePassword(password)
        session.timeout = CONNECT_TIMEOUT_MS
        try {
            try {
                session.connect(CONNECT_TIMEOUT_MS)
            } catch (e: JSchException) {
                throw passwordConnectFailure(e.message.orEmpty(), repo.mismatch, host, port, e)
            }
            val channel = session.openChannel("exec") as ChannelExec
            channel.setCommand(command)
            channel.setPty(false)
            val out = channel.inputStream
            val err = channel.extInputStream
            val input = channel.outputStream
            channel.connect(CONNECT_TIMEOUT_MS)
            try {
                if (stdin != null) input.write((stdin + "\n").toByteArray())
                input.flush()
            } finally {
                input.close()
            }
            // stderr (progress, notes) is shown too: read it on its own thread so neither stream blocks the other.
            val lock = Any()
            val text = StringBuilder()
            fun emit(s: String) = synchronized(lock) {
                text.append(s)
                if (text.length > MAX_PASSWORD_OUTPUT) text.delete(0, text.length - MAX_PASSWORD_OUTPUT)
                onOutput(s)
            }
            val errThread = Thread { runCatching { drain(err, ::emit) } }.apply { isDaemon = true; start() }
            val timedOut = AtomicBoolean(false)
            val stopped = AtomicBoolean(false)
            val deadline = System.currentTimeMillis() + timeoutMs
            val watchdog = Timer(true)
            watchdog.schedule(object : TimerTask() {
                override fun run() {
                    when {
                        isCancelled() -> { stopped.set(true); channel.disconnect() }
                        System.currentTimeMillis() > deadline -> { timedOut.set(true); channel.disconnect() }
                    }
                }
            }, 250, 250)
            try {
                drain(out, ::emit)
            } catch (e: IOException) {
                if (!timedOut.get() && !stopped.get()) throw e
            } finally {
                watchdog.cancel()
            }
            errThread.join(2_000)
            if (stopped.get()) throw SshCancelled()
            if (timedOut.get()) throw SshFailure(SshFailure.Kind.TIMEOUT, "The server took too long to finish the setup")
            // The exit status arrives with the channel's close, just after the last output.
            val waitUntil = System.currentTimeMillis() + 2_000
            while (!channel.isClosed && System.currentTimeMillis() < waitUntil) Thread.sleep(50)
            val status = channel.exitStatus
            channel.disconnect()
            return PasswordExecResult(synchronized(lock) { text.toString() }, status)
        } finally {
            session.disconnect()
        }
    }

    private const val MAX_PASSWORD_OUTPUT = 256 * 1024

    private fun drain(stream: java.io.InputStream, emit: (String) -> Unit) {
        // A Reader keeps a UTF-8 character that is split across two reads in one piece.
        val reader = stream.reader(Charsets.UTF_8)
        val chunk = CharArray(8192)
        while (true) {
            val n = reader.read(chunk)
            if (n < 0) break
            if (n > 0) emit(String(chunk, 0, n))
        }
    }

    /** Answers the password prompt once (password or keyboard-interactive): a wrong password is one try, no loop. */
    private class OncePassword(private val password: String) : UserInfo, UIKeyboardInteractive {
        private var answered = false
        override fun getPassphrase(): String? = null
        override fun getPassword(): String? = if (answered) null else password.also { answered = true }
        override fun promptPassword(message: String?) = !answered
        override fun promptPassphrase(message: String?) = false
        override fun promptYesNo(message: String?) = false
        override fun showMessage(message: String?) {}
        override fun promptKeyboardInteractive(
            destination: String?,
            name: String?,
            instruction: String?,
            prompt: Array<String>?,
            echo: BooleanArray?,
        ): Array<String>? = when {
            prompt == null || prompt.isEmpty() -> emptyArray()   // an info-only round: nothing to answer
            answered || prompt.size != 1 || echo?.getOrNull(0) == true -> null   // null = cancel
            else -> { answered = true; arrayOf(password) }
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
