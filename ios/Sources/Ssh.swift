import Foundation
import CryptoKit
import NIOCore
import NIOPosix
import NIOSSH
import Security

struct ServerConfig: Codable, Equatable {
    var host = ""
    var port = 22
    var user = ""
    /// Pinned host key: algorithm name and base64 wire blob.
    var hostKeyType = ""
    var hostKeyBlob = ""

    var isConfigured: Bool { !host.isEmpty && !user.isEmpty && !hostKeyBlob.isEmpty }
}

struct HostKeyInfo: Equatable {
    let type: String
    let blob: String
    var fingerprint: String {
        guard let d = Data(base64Encoded: blob) else { return "?" }
        let b64 = Data(SHA256.hash(data: d)).base64EncodedString().replacingOccurrences(of: "=", with: "")
        return "SHA256:" + b64
    }
}

/// Why a call failed before the server could answer with JSON.
struct SshFailure: Error, LocalizedError {
    enum Kind { case hostKeyChanged, authFailed, network, timeout, notConfigured, probe }
    let kind: Kind
    let message: String
    var errorDescription: String? { message }
}

// MARK: - The phone's Ed25519 key (kept in the Keychain)

enum KeyStore {
    private static let account = "ssh_seed"

    private static func seed() -> Data? {
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrAccount as String: account,
                                kSecReturnData as String: true]
        var out: CFTypeRef?
        return SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess ? out as? Data : nil
    }

    private static func save(_ data: Data) {
        let base: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrAccount as String: account]
        SecItemDelete(base as CFDictionary)
        var add = base
        add[kSecValueData as String] = data
        add[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        SecItemAdd(add as CFDictionary, nil)
    }

    /// The key, made on first use. Never shown or exported; only the public half leaves the phone.
    static func privateKey() -> Curve25519.Signing.PrivateKey {
        if let s = seed(), let k = try? Curve25519.Signing.PrivateKey(rawRepresentation: s) { return k }
        return regenerate()
    }

    @discardableResult
    static func regenerate() -> Curve25519.Signing.PrivateKey {
        let k = Curve25519.Signing.PrivateKey()
        save(k.rawRepresentation)
        return k
    }

    /// `ssh-ed25519 AAAA… clauderc`, ready for authorized_keys.
    static func publicKeyLine() -> String {
        var blob = Data()
        func put(_ d: Data) {
            var n = UInt32(d.count).bigEndian
            blob.append(Data(bytes: &n, count: 4))
            blob.append(d)
        }
        put(Data("ssh-ed25519".utf8))
        put(privateKey().publicKey.rawRepresentation)
        return "ssh-ed25519 \(blob.base64EncodedString()) clauderc"
    }
}

// MARK: - SSH exec

private final class HostKeyDelegate: NIOSSHClientServerAuthenticationDelegate, @unchecked Sendable {
    let pinned: String?
    var seen: HostKeyInfo?
    var mismatch = false
    init(pinned: String?) { self.pinned = pinned }

    func validateHostKey(hostKey: NIOSSHPublicKey, validationCompletePromise: EventLoopPromise<Void>) {
        let parts = String(openSSHPublicKey: hostKey).split(separator: " ").map(String.init)
        guard parts.count >= 2 else {
            validationCompletePromise.fail(SshFailure(kind: .network, message: "Unreadable host key"))
            return
        }
        seen = HostKeyInfo(type: parts[0], blob: parts[1])
        if let pinned {
            if pinned == parts[1] { validationCompletePromise.succeed(()) } else {
                mismatch = true
                validationCompletePromise.fail(SshFailure(kind: .hostKeyChanged, message: "The server's host key has changed. Refusing to connect."))
            }
        } else {
            // Probe: we only wanted to see the key, so stop before authenticating.
            validationCompletePromise.fail(SshFailure(kind: .probe, message: "probe"))
        }
    }
}

private final class KeyAuthDelegate: NIOSSHClientUserAuthenticationDelegate, @unchecked Sendable {
    let user: String
    let key: NIOSSHPrivateKey?
    var tried = false
    var rejected = false
    init(user: String, key: NIOSSHPrivateKey?) { self.user = user; self.key = key }

    func nextAuthenticationType(availableMethods: NIOSSHAvailableUserAuthenticationMethods,
                                nextChallengePromise: EventLoopPromise<NIOSSHUserAuthenticationOffer?>) {
        guard let key, !tried, availableMethods.contains(.publicKey) else {
            if tried { rejected = true }
            nextChallengePromise.succeed(nil)
            return
        }
        tried = true
        nextChallengePromise.succeed(.init(username: user, serviceName: "", offer: .privateKey(.init(privateKey: key))))
    }
}

/// Runs one exec request, collects stdout, and finishes once.
private final class ExecHandler: ChannelDuplexHandler {
    typealias InboundIn = SSHChannelData
    typealias OutboundIn = ByteBuffer
    typealias OutboundOut = SSHChannelData

    private let command: String
    private let stdin: Data?
    private let done: (Result<Data, Error>) -> Void
    private var out = Data()

    init(command: String, stdin: Data?, done: @escaping (Result<Data, Error>) -> Void) {
        self.command = command; self.stdin = stdin; self.done = done
    }

    func handlerAdded(context: ChannelHandlerContext) {
        context.channel.setOption(ChannelOptions.allowRemoteHalfClosure, value: true).whenFailure { context.fireErrorCaught($0) }
    }

    func channelActive(context: ChannelHandlerContext) {
        context.fireChannelActive()
        let req = SSHChannelRequestEvent.ExecRequest(command: command, wantReply: false)
        context.triggerUserOutboundEvent(req, promise: nil)
        if let stdin, !stdin.isEmpty {
            var buf = context.channel.allocator.buffer(capacity: stdin.count)
            buf.writeBytes(stdin)
            context.writeAndFlush(wrapOutboundOut(SSHChannelData(type: .channel, data: .byteBuffer(buf))), promise: nil)
        }
        context.close(mode: .output, promise: nil)   // EOF, so nothing on the server waits for input
    }

    func channelRead(context: ChannelHandlerContext, data: NIOAny) {
        let d = unwrapInboundIn(data)
        guard case .channel = d.type, case .byteBuffer(let b) = d.data else { return }
        out.append(contentsOf: b.readableBytesView)
    }

    func channelInactive(context: ChannelHandlerContext) { done(.success(out)) }
    func errorCaught(context: ChannelHandlerContext, error: Error) { done(.failure(error)); context.close(promise: nil) }
}

private final class Run: @unchecked Sendable {
    private let lock = NSLock()
    private var finished = false
    private var cont: CheckedContinuation<Data, Error>?
    var channel: Channel?
    var timer: Scheduled<Void>?

    func start(_ c: CheckedContinuation<Data, Error>) { cont = c }

    func finish(_ r: Result<Data, Error>) {
        lock.lock()
        if finished { lock.unlock(); return }
        finished = true
        let c = cont
        lock.unlock()
        timer?.cancel()
        channel?.close(promise: nil)
        c?.resume(with: r)
    }
}

enum SshRunner {
    private static let connectTimeout: TimeAmount = .seconds(15)

    /// Connect without authenticating, only to learn the host key so the user can compare the fingerprint.
    static func probeHostKey(host: String, port: Int) async throws -> HostKeyInfo {
        let hk = HostKeyDelegate(pinned: nil)
        let auth = KeyAuthDelegate(user: "probe", key: nil)
        do { _ = try await run(host: host, port: port, hk: hk, auth: auth, command: "", stdin: nil, timeoutMs: 15_000) } catch {
            if let seen = hk.seen { return seen }
            throw map(error, hk: hk, auth: auth, host: host, port: port)
        }
        if let seen = hk.seen { return seen }
        throw SshFailure(kind: .network, message: "The server did not present a host key")
    }

    /// Runs [command] (becomes SSH_ORIGINAL_COMMAND on the server). [stdin] is for secrets: never in the command line.
    static func exec(_ c: ServerConfig, command: String, stdin: String? = nil, timeoutMs: Int = 90_000) async throws -> String {
        guard c.isConfigured else { throw SshFailure(kind: .notConfigured, message: "Server settings are incomplete") }
        let hk = HostKeyDelegate(pinned: c.hostKeyBlob)
        let auth = KeyAuthDelegate(user: c.user, key: NIOSSHPrivateKey(ed25519Key: KeyStore.privateKey()))
        do {
            let data = try await run(host: c.host, port: c.port, hk: hk, auth: auth, command: command,
                                     stdin: stdin.map { Data(($0 + "\n").utf8) }, timeoutMs: timeoutMs)
            return String(decoding: data, as: UTF8.self)
        } catch {
            throw map(error, hk: hk, auth: auth, host: c.host, port: c.port)
        }
    }

    private static func map(_ error: Error, hk: HostKeyDelegate, auth: KeyAuthDelegate, host: String, port: Int) -> SshFailure {
        if let f = error as? SshFailure, f.kind == .timeout || f.kind == .notConfigured { return f }
        if hk.mismatch { return SshFailure(kind: .hostKeyChanged, message: "The server's host key has changed. Refusing to connect.") }
        if auth.rejected || (auth.tried && "\(error)".lowercased().contains("auth")) {
            return SshFailure(kind: .authFailed, message: "The server didn't accept this phone's key.")
        }
        return SshFailure(kind: .network, message: "Could not connect to \(host):\(port) (\(error.localizedDescription))")
    }

    private static func run(host: String, port: Int, hk: HostKeyDelegate, auth: KeyAuthDelegate,
                            command: String, stdin: Data?, timeoutMs: Int) async throws -> Data {
        let group = MultiThreadedEventLoopGroup.singleton
        let state = Run()
        return try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Data, Error>) in
            state.start(cont)
            let bootstrap = ClientBootstrap(group: group)
                .connectTimeout(connectTimeout)
                .channelInitializer { ch in
                    let cfg = SSHClientConfiguration(userAuthDelegate: auth, serverAuthDelegate: hk)
                    return ch.pipeline.addHandler(NIOSSHHandler(role: .client(cfg), allocator: ch.allocator, inboundChildChannelInitializer: nil))
                }
            bootstrap.connect(host: host, port: port).whenComplete { result in
                switch result {
                case .failure(let e): state.finish(.failure(e))
                case .success(let channel):
                    state.channel = channel
                    state.timer = channel.eventLoop.scheduleTask(in: .milliseconds(Int64(timeoutMs))) {
                        state.finish(.failure(SshFailure(kind: .timeout, message: "The server took too long to answer")))
                    }
                    channel.closeFuture.whenComplete { _ in
                        state.finish(.failure(SshFailure(kind: .network, message: "Connection closed")))
                    }
                    channel.pipeline.handler(type: NIOSSHHandler.self).flatMap { ssh -> EventLoopFuture<Channel> in
                        let p = channel.eventLoop.makePromise(of: Channel.self)
                        ssh.createChannel(p) { child, type in
                            guard type == .session else { return channel.eventLoop.makeFailedFuture(SshFailure(kind: .network, message: "Bad channel")) }
                            return child.pipeline.addHandler(ExecHandler(command: command, stdin: stdin) { state.finish($0) })
                        }
                        return p.futureResult
                    }.whenFailure { state.finish(.failure($0)) }
                }
            }
        }
    }
}
