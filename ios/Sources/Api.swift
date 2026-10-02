import Foundation

// Server errors plus local connection failures, with the codes the Android app uses.
struct ApiError: Error, LocalizedError {
    let code: String
    let message: String
    var path: String?
    var errorDescription: String? { friendly }

    var friendly: String {
        switch code {
        case "host_key_changed":
            return "The server's host key changed, so the app refused to connect. If you didn't reinstall the server, someone may be intercepting the connection."
        case "auth_failed":
            return "The server didn't accept this iPhone's key. Run install-launcher-key.sh with the key from Settings."
        case "not_configured": return "Set up the server first."
        case "busy": return "The server is busy with another create/open. Try again in a moment."
        case "forbidden": return "The server refused this action. Update claude-launcher-api on the server."
        case "timeout": return "The server took too long to answer."
        case "network": return "Can't reach the server: \(message)"
        case "folder_dirty": return "The folder has uncommitted changes, so it wasn't updated."
        default: return message
        }
    }
}

let projectNameRE = try! NSRegularExpression(pattern: "^[A-Za-z0-9._][A-Za-z0-9._-]{0,99}$")

extension KeyedDecodingContainer {
    /// A value, or [def] when the key is missing or null (the server's JSON grows over time).
    func v<T: Decodable>(_ k: Key, _ def: T) -> T { (try? decodeIfPresent(T.self, forKey: k)) ?? def }
}

struct Session: Decodable, Identifiable, Hashable {
    var id: String { name }
    let name: String, project: String, preview: String
    let uptimeSeconds: Int, waiting: Bool, busy: Bool, pushDone: Bool
    enum K: String, CodingKey { case name, project, preview, waiting, busy
        case uptimeSeconds = "uptime_seconds", pushDone = "push_done" }
    init(from d: Decoder) throws {
        let c = try d.container(keyedBy: K.self)
        name = try c.decode(String.self, forKey: .name)
        project = c.v(.project, name); preview = c.v(.preview, "")
        uptimeSeconds = c.v(.uptimeSeconds, 0); waiting = c.v(.waiting, false)
        busy = c.v(.busy, false); pushDone = c.v(.pushDone, false)
    }
}
struct SessionsData: Decodable { let sessions: [Session]
    enum K: String, CodingKey { case sessions }
    init(from d: Decoder) throws { sessions = try d.container(keyedBy: K.self).v(.sessions, []) } }

struct Repo: Decodable, Identifiable, Hashable {
    var id: String { fullName }
    let fullName: String, name: String, owner: String
    let isPrivate: Bool, local: Bool, running: Bool, cloning: Bool, archived: Bool
    enum K: String, CodingKey { case name, owner, `private`, local, running, cloning, archived, fullName = "full_name" }
    init(from d: Decoder) throws {
        let c = try d.container(keyedBy: K.self)
        fullName = try c.decode(String.self, forKey: .fullName)
        name = c.v(.name, fullName); owner = c.v(.owner, "")
        isPrivate = c.v(.private, false); local = c.v(.local, false); running = c.v(.running, false)
        cloning = c.v(.cloning, false); archived = c.v(.archived, false)
    }
}
struct ReposData: Decodable { let repos: [Repo]
    enum K: String, CodingKey { case repos }
    init(from d: Decoder) throws { repos = try d.container(keyedBy: K.self).v(.repos, []) } }

struct Owners: Decodable { let user: String, orgs: [String], defaultOwner: String?
    enum K: String, CodingKey { case user, orgs, defaultOwner = "default_owner" }
    struct Org: Decodable { let login: String }
    init(from d: Decoder) throws {
        let c = try d.container(keyedBy: K.self)
        user = try c.decode(String.self, forKey: .user)
        orgs = c.v(.orgs, [Org]()).map(\.login); defaultOwner = try? c.decodeIfPresent(String.self, forKey: .defaultOwner)
    } }

struct ServiceStatus: Decodable { let loggedIn: Bool
    enum K: String, CodingKey { case loggedIn = "logged_in" }
    init(from d: Decoder) throws { loggedIn = try d.container(keyedBy: K.self).v(.loggedIn, false) } }
struct StatusData: Decodable {
    let hostname: String, scriptApi: Int, claude: Bool, github: Bool, githubUser: String?
    enum K: String, CodingKey { case hostname, claude, github, scriptApi = "script_api" }
    enum G: String, CodingKey { case user }
    init(from d: Decoder) throws {
        let c = try d.container(keyedBy: K.self)
        hostname = c.v(.hostname, ""); scriptApi = c.v(.scriptApi, 0)
        claude = c.v(.claude, ServiceStatus(loggedIn: false)).loggedIn
        github = c.v(.github, ServiceStatus(loggedIn: false)).loggedIn
        githubUser = try? c.nestedContainer(keyedBy: G.self, forKey: .github).decodeIfPresent(String.self, forKey: .user)
    } }
extension ServiceStatus { init(loggedIn: Bool) { self.loggedIn = loggedIn } }

struct OpenResult: Decodable { let action: String, pending: Bool, session: String?
    enum K: String, CodingKey { case action, pending, session }
    init(from d: Decoder) throws {
        let c = try d.container(keyedBy: K.self)
        action = c.v(.action, ""); pending = c.v(.pending, false); session = try? c.decodeIfPresent(String.self, forKey: .session)
    } }
struct NewResult: Decodable { let repo: String, session: String?
    enum K: String, CodingKey { case repo, session }
    init(from d: Decoder) throws {
        let c = try d.container(keyedBy: K.self)
        repo = c.v(.repo, ""); session = try? c.decodeIfPresent(String.self, forKey: .session)
    } }
struct StopResult: Decodable { let stopped: Bool
    enum K: String, CodingKey { case stopped }
    init(from d: Decoder) throws { stopped = try d.container(keyedBy: K.self).v(.stopped, false) } }
struct TailResult: Decodable { let text: String
    enum K: String, CodingKey { case text }
    init(from d: Decoder) throws { text = try d.container(keyedBy: K.self).v(.text, "") } }
struct PinStatus: Decodable { let isSet: Bool
    enum K: String, CodingKey { case isSet = "set" }
    init(from d: Decoder) throws { isSet = try d.container(keyedBy: K.self).v(.isSet, false) } }
struct Empty: Decodable { init(from d: Decoder) throws {} }

struct ChatMessage: Decodable, Identifiable, Hashable {
    let id: String, role: String, text: String, files: [String]
    enum K: String, CodingKey { case id, role, text, files }
    init(from d: Decoder) throws {
        let c = try d.container(keyedBy: K.self)
        id = c.v(.id, UUID().uuidString); role = c.v(.role, "assistant"); text = c.v(.text, ""); files = c.v(.files, [])
    }
}
struct AskOption: Decodable, Hashable { let label: String, description: String
    enum K: String, CodingKey { case label, description }
    init(from d: Decoder) throws { let c = try d.container(keyedBy: K.self); label = c.v(.label, ""); description = c.v(.description, "") } }
struct AskQuestion: Decodable, Hashable { let question: String, header: String, multiSelect: Bool, options: [AskOption]
    enum K: String, CodingKey { case question, header, multiSelect, options }
    init(from d: Decoder) throws {
        let c = try d.container(keyedBy: K.self)
        question = c.v(.question, ""); header = c.v(.header, ""); multiSelect = c.v(.multiSelect, false); options = c.v(.options, [])
    } }
struct ChatData: Decodable {
    let messages: [ChatMessage], waiting: Bool, busy: Bool, mode: String, model: String
    let ask: [AskQuestion]?, screen: String?
    enum K: String, CodingKey { case messages, waiting, busy, mode, model, ask, screen }
    init(from d: Decoder) throws {
        let c = try d.container(keyedBy: K.self)
        messages = c.v(.messages, []); waiting = c.v(.waiting, false); busy = c.v(.busy, false)
        mode = c.v(.mode, "default"); model = c.v(.model, "")
        ask = try? c.decodeIfPresent([AskQuestion].self, forKey: .ask); screen = try? c.decodeIfPresent(String.self, forKey: .screen)
    }
}

/// Typed calls to `claude-launcher-api` on the server, one SSH exec each.
struct LauncherApi {
    let config: ServerConfig

    func status() async throws -> StatusData { try await call("status") }
    func owners() async throws -> Owners { try await call("owners") }
    func repos(refresh: Bool) async throws -> [Repo] { (try await call(refresh ? "repos --refresh" : "repos") as ReposData).repos }
    func sessions() async throws -> [Session] { (try await call("sessions") as SessionsData).sessions }

    func newProject(name: String, owner: String, isPrivate: Bool, start: Bool) async throws -> NewResult {
        try requireName(name)
        return try await call("new \(name) --owner \(owner) --visibility \(isPrivate ? "private" : "public")" + (start ? " --start" : ""), timeout: 180_000)
    }
    func open(_ fullName: String, start: Bool) async throws -> OpenResult {
        try await call("open \(fullName)" + (start ? " --start" : ""), timeout: 120_000)
    }
    func start(_ project: String) async throws -> Empty { try requireName(project); return try await call("start \(project)") }
    func stop(_ target: String) async throws -> StopResult { try requireName(target); return try await call("stop \(target)") }
    func tail(_ target: String, lines: Int = 120) async throws -> TailResult {
        try requireName(target); return try await call("tail \(target) --lines \(min(max(lines, 1), 200))")
    }
    /// Allowlisted keys (1-9, Enter, Escape, arrows…); returns the screen after.
    func keys(_ target: String, _ keys: [String]) async throws -> TailResult {
        try requireName(target); return try await call("keys \(target) " + keys.joined(separator: " "))
    }
    func restart(_ target: String) async throws -> Empty { try requireName(target); return try await call("restart \(target)", timeout: 120_000) }

    // In-app chat: every call carries the PIN on stdin; the server checks it.
    func chatPinStatus() async throws -> PinStatus { try await call("chat-pin-status") }
    func chatPinSet(_ pin: String) async throws -> Empty { try await call("chat-pin-set", stdin: pin + "\n") }
    func chatOpen(_ s: String, pin: String) async throws -> Empty { try requireName(s); return try await call("chat-open \(s)", stdin: pin) }
    func chatHistory(_ s: String, pin: String) async throws -> ChatData { try requireName(s); return try await call("chat-history \(s)", stdin: pin) }
    func chatSend(_ s: String, pin: String, text: String) async throws -> Empty {
        try requireName(s); return try await call("chat-send \(s)", stdin: pin + "\n" + text)
    }
    func chatInterrupt(_ s: String, pin: String) async throws -> Empty { try requireName(s); return try await call("chat-interrupt \(s)", stdin: pin) }

    private func requireName(_ n: String) throws {
        let r = NSRange(n.startIndex..., in: n)
        if projectNameRE.firstMatch(in: n, range: r) == nil {
            throw ApiError(code: "invalid_name", message: "Names may use letters, digits, '.', '_' and '-' (not first), up to 100.")
        }
    }

    private func call<T: Decodable>(_ command: String, stdin: String? = nil, timeout: Int = 90_000) async throws -> T {
        let cfg = config
        let raw: String
        do { raw = try await SshRunner.exec(cfg, command: command, stdin: stdin, timeoutMs: timeout) } catch let f as SshFailure {
            let code: String
            switch f.kind {
            case .hostKeyChanged: code = "host_key_changed"
            case .authFailed: code = "auth_failed"
            case .timeout: code = "timeout"
            case .notConfigured: code = "not_configured"
            default: code = "network"
            }
            throw ApiError(code: code, message: f.message)
        }
        let data = try Self.parseEnvelope(raw)
        do { return try JSONDecoder().decode(T.self, from: data) } catch {
            throw ApiError(code: "bad_response", message: "Unexpected answer from the server: \(error.localizedDescription)")
        }
    }

    /// Returns `data` (re-encoded) from `{"ok":true,...}` or throws the server's error.
    static func parseEnvelope(_ raw: String) throws -> Data {
        guard let line = raw.split(whereSeparator: \.isNewline).map({ $0.trimmingCharacters(in: .whitespaces) }).last(where: { $0.hasPrefix("{") }),
              let obj = (try? JSONSerialization.jsonObject(with: Data(line.utf8))) as? [String: Any]
        else { throw ApiError(code: "bad_response", message: "The server sent no JSON. Is claude-launcher-api installed?") }
        if obj["ok"] as? Bool == true {
            return try JSONSerialization.data(withJSONObject: obj["data"] ?? [String: Any]())
        }
        let e = obj["error"] as? [String: Any]
        throw ApiError(code: e?["code"] as? String ?? "internal", message: e?["message"] as? String ?? "The server reported an error.", path: e?["path"] as? String)
    }
}
