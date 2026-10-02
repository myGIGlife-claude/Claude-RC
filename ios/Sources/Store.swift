import Foundation
import Security
import SwiftUI

enum Keychain {
    static func get(_ account: String) -> String? {
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrAccount as String: account, kSecReturnData as String: true]
        var out: CFTypeRef?
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess, let d = out as? Data else { return nil }
        return String(data: d, encoding: .utf8)
    }
    static func set(_ account: String, _ value: String?) {
        let base: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrAccount as String: account]
        SecItemDelete(base as CFDictionary)
        guard let value else { return }
        var add = base
        add[kSecValueData as String] = Data(value.utf8)
        add[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        SecItemAdd(add as CFDictionary, nil)
    }
}

@MainActor
final class AppModel: ObservableObject {
    @Published var server: ServerConfig {
        didSet { if let d = try? JSONEncoder().encode(server) { UserDefaults.standard.set(d, forKey: "server") } }
    }
    @Published var pendingHostKey: HostKeyInfo?
    @Published var status: StatusData?
    @Published var sessions: [Session] = []
    @Published var repos: [Repo] = []
    @Published var owners: Owners?
    @Published var banner: String?
    @Published var busy: String?
    @Published var chatSession: Session?

    init() {
        if let d = UserDefaults.standard.data(forKey: "server"), let s = try? JSONDecoder().decode(ServerConfig.self, from: d) { server = s } else { server = ServerConfig() }
    }

    var api: LauncherApi { LauncherApi(config: server) }
    var fingerprint: String { HostKeyInfo(type: server.hostKeyType, blob: server.hostKeyBlob).fingerprint }

    func say(_ t: String) { banner = t }
    func fail(_ e: Error) { banner = (e as? LocalizedError)?.errorDescription ?? e.localizedDescription }

    /// Runs [block] with a busy label; errors become the banner.
    func action(_ label: String, _ block: @escaping () async throws -> Void) {
        Task { [self] in
            busy = label
            defer { busy = nil }
            do { try await block() } catch { fail(error) }
        }
    }

    // MARK: server setup

    func saveAndProbe(host: String, port: Int, user: String) {
        let h = host.trimmingCharacters(in: .whitespaces), u = user.trimmingCharacters(in: .whitespaces)
        action("Contacting \(h)…") { [self] in
            if server.host != h || server.port != port {
                server = ServerConfig(host: h, port: port, user: u)
                status = nil; sessions = []; repos = []; owners = nil
                Keychain.set("chat_pin", nil)
            } else { server.user = u }
            let info = try await SshRunner.probeHostKey(host: h, port: port)
            if info.blob == server.hostKeyBlob { say("Host key matches the pinned one"); refreshStatus() } else { pendingHostKey = info }
        }
    }

    func acceptHostKey(_ accept: Bool) {
        guard let info = pendingHostKey else { return }
        pendingHostKey = nil
        guard accept else { return }
        server.hostKeyType = info.type
        server.hostKeyBlob = info.blob
        say("Server trusted. Checking connection…")
        refreshStatus()
    }

    func forgetServer() {
        server = ServerConfig()
        status = nil; sessions = []; repos = []; owners = nil
        Keychain.set("chat_pin", nil)
    }

    // MARK: data

    func refreshStatus() {
        guard server.isConfigured else { return }
        Task { [self] in do { status = try await api.status() } catch { fail(error) } }
    }

    func refreshSessions() async {
        guard server.isConfigured else { return }
        do { sessions = try await api.sessions() } catch { fail(error) }
    }

    func refreshRepos(force: Bool) async {
        guard server.isConfigured else { return }
        do { repos = try await api.repos(refresh: force) } catch { fail(error) }
    }

    func loadOwners() async {
        if owners == nil { owners = try? await api.owners() }
    }

    func start(_ project: String) {
        action("Starting \(project)…") { [self] in
            _ = try await api.start(project)
            say("Claude started for \(project)")
            await refreshSessions(); await refreshRepos(force: false)
        }
    }

    func stop(_ target: String) {
        action("Stopping \(target)…") { [self] in
            let r = try await api.stop(target)
            say(r.stopped ? "Stopped \(target)" : "\(target) wasn't running")
            await refreshSessions(); await refreshRepos(force: false)
        }
    }

    func restart(_ target: String) {
        action("Restarting \(target)…") { [self] in
            _ = try await api.restart(target)
            say("Restarted \(target)")
            await refreshSessions()
        }
    }

    func openRepo(_ repo: Repo, start: Bool) {
        action(repo.local ? "Updating \(repo.name)…" : "Cloning \(repo.name)…") { [self] in
            let r = try await api.open(repo.fullName, start: start)
            if r.pending { say("Cloning \(repo.name) in the background. Pull to refresh in a moment.") }
            else { say(r.session != nil ? "Ready: \(repo.name) — Claude started" : "Ready: \(repo.name)") }
            await refreshRepos(force: false); await refreshSessions()
        }
    }

    func createProject(name: String, owner: String, isPrivate: Bool, start: Bool) {
        action("Creating \(name)…") { [self] in
            let r = try await api.newProject(name: name, owner: owner, isPrivate: isPrivate, start: start)
            say("Created \(r.repo)")
            await refreshRepos(force: true); await refreshSessions()
        }
    }
}

// MARK: - Chat

@MainActor
final class ChatModel: ObservableObject {
    let session: String
    private var app: AppModel!
    @Published var chat: ChatData?
    @Published var pending: [String] = []
    @Published var error: String?
    @Published var needsPin = false
    @Published var pinIsSet = true
    private var pin: String?
    private var poller: Task<Void, Never>?

    init(session: String) { self.session = session }

    private var api: LauncherApi { app.api }

    func start(app: AppModel) {
        self.app = app
        pin = Keychain.get("chat_pin")
        if pin != nil { startPolling(); return }
        Task { [self] in
            do { pinIsSet = try await api.chatPinStatus().isSet } catch { self.error = msg(error) }
            needsPin = true
        }
    }

    func stop() { poller?.cancel() }

    private func msg(_ e: Error) -> String { (e as? LocalizedError)?.errorDescription ?? e.localizedDescription }

    func unlock(pin p: String, remember: Bool) {
        error = nil
        Task { [self] in
            do {
                if !pinIsSet { _ = try await api.chatPinSet(p) }
                _ = try await api.chatOpen(session, pin: p)
                pin = p
                Keychain.set("chat_pin", remember ? p : nil)
                needsPin = false
                startPolling()
            } catch { self.error = msg(error) }
        }
    }

    private func startPolling() {
        poller?.cancel()
        poller = Task { [self] in
            while !Task.isCancelled {
                guard let p = pin else { break }
                do {
                    setChat(try await api.chatHistory(session, pin: p))
                    error = nil
                } catch let e as ApiError {
                    if ["wrong_pin", "chat_locked", "pin_not_set"].contains(e.code) {
                        pin = nil; Keychain.set("chat_pin", nil); error = e.friendly; needsPin = true; break
                    }
                    error = e.friendly
                } catch { self.error = msg(error) }
                try? await Task.sleep(nanoseconds: (chat?.busy == true ? 2 : 4) * 1_000_000_000)
            }
        }
    }

    private func norm(_ t: String) -> String {
        String(t.trimmingCharacters(in: .whitespacesAndNewlines).split(whereSeparator: \.isWhitespace).joined(separator: " ").prefix(7000))
    }

    private func setChat(_ c: ChatData) {
        chat = c
        let shown = Set(c.messages.filter { $0.role == "user" }.map { norm($0.text) })
        pending.removeAll { shown.contains(norm($0)) }
    }

    private func run(_ block: @escaping (String) async throws -> Void) {
        guard let p = pin else { return }
        Task { [self] in do { try await block(p) } catch { self.error = msg(error) } }
    }

    private func refresh(_ p: String, after ms: UInt64 = 800) async throws {
        try await Task.sleep(nanoseconds: ms * 1_000_000)
        setChat(try await api.chatHistory(session, pin: p))
    }

    /// Shows the message as pending at once; the SSH round trip takes seconds. A failure puts the text back in [onFail].
    func send(_ text: String, onFail: @escaping (String) -> Void) {
        let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !t.isEmpty, let p = pin else { return }
        let tracked = !t.hasPrefix("/")   // a slash command never shows as a message, so it would stay "queued"
        if tracked { pending.append(t) }
        Task { [self] in
            do { _ = try await api.chatSend(session, pin: p, text: text); try await refresh(p) } catch {
                if tracked, let i = pending.firstIndex(of: t) { pending.remove(at: i) }
                onFail(text); self.error = msg(error)
            }
        }
    }

    func interrupt() { run { [self] p in _ = try await api.chatInterrupt(session, pin: p); try await refresh(p, after: 500) } }

    func key(_ k: String) { run { [self] p in _ = try await api.keys(session, [k]); setChat(try await api.chatHistory(session, pin: p)) } }

    /// Answers Claude's question screen key by key: a number picks, a multi-choice list is toggled then Down to its Submit row.
    func answer(_ qs: [AskQuestion], picks: [Set<Int>], others: [String]) {
        run { [self] p in
            var keys: [String] = []
            func flush() async throws {
                var i = 0
                while i < keys.count { _ = try await api.keys(session, Array(keys[i..<min(i + 5, keys.count)])); i += 5 }
                keys = []
            }
            for (qi, q) in qs.enumerated() {
                let n = q.options.count
                let other = others[qi].trimmingCharacters(in: .whitespacesAndNewlines)
                if q.multiSelect {
                    keys += picks[qi].sorted().map { String($0 + 1) }
                    keys += Array(repeating: "Down", count: n + 1)
                    keys.append("Enter")
                } else if !other.isEmpty {
                    keys.append(String(n + 1))
                    try await flush()
                    _ = try await api.chatSend(session, pin: p, text: other)
                    try await Task.sleep(nanoseconds: 800_000_000)
                } else {
                    keys.append(String((picks[qi].min() ?? 0) + 1))
                }
            }
            try await flush()
            try await Task.sleep(nanoseconds: 800_000_000)
            var h = try await api.chatHistory(session, pin: p)
            // A "Review your answers" screen after several questions: the first entry is Submit.
            if h.screen?.contains("Submit answers") == true {
                _ = try await api.keys(session, ["Enter"])
                try await Task.sleep(nanoseconds: 500_000_000)
                h = try await api.chatHistory(session, pin: p)
            }
            setChat(h)
        }
    }
}
