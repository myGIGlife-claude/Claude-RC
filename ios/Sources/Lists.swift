import SwiftUI

func healthColor(busy: Bool, waiting: Bool) -> Color { waiting ? .red : busy ? .orange : .green }

func uptime(_ s: Int) -> String {
    let d = s / 86_400, h = s % 86_400 / 3_600, m = s % 3_600 / 60
    return d > 0 ? "\(d)d \(h)h" : h > 0 ? "\(h)h \(m)m" : "\(m)m"
}

struct SessionsView: View {
    @EnvironmentObject var app: AppModel
    @State private var terminal: Session?

    var body: some View {
        NavigationStack {
            List {
                if app.sessions.isEmpty { Text("No Claude sessions running. Start one from Projects.").foregroundStyle(.secondary) }
                ForEach(app.sessions) { s in
                    Button { app.chatSession = s } label: {
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Circle().fill(healthColor(busy: s.busy, waiting: s.waiting)).frame(width: 10, height: 10)
                                Text(s.project).font(.headline)
                                Spacer()
                                Text(uptime(s.uptimeSeconds)).font(.caption).foregroundStyle(.secondary)
                            }
                            if s.waiting { Text("Waiting for your answer").font(.caption).foregroundStyle(.red) }
                            Text(s.preview.split(separator: "\n").suffix(2).joined(separator: "\n"))
                                .font(.system(.caption, design: .monospaced)).foregroundStyle(.secondary).lineLimit(2)
                        }
                    }
                    .tint(.primary)
                    .swipeActions {
                        Button("Stop", role: .destructive) { app.stop(s.name) }
                        Button("Terminal") { terminal = s }.tint(.blue)
                        Button("Restart") { app.restart(s.name) }.tint(.orange)
                    }
                }
            }
            .navigationTitle("Sessions")
            .refreshable { await app.refreshSessions() }
            .task {
                while !Task.isCancelled { await app.refreshSessions(); try? await Task.sleep(nanoseconds: 5_000_000_000) }
            }
            .sheet(item: $terminal) { TerminalView(session: $0.name).environmentObject(app) }
        }
    }
}

struct ProjectsView: View {
    @EnvironmentObject var app: AppModel
    @State private var search = ""
    @State private var picked: Repo?
    @State private var showNew = false

    var body: some View {
        NavigationStack {
            List(app.repos.filter { search.isEmpty || $0.fullName.localizedCaseInsensitiveContains(search) }) { r in
                Button { picked = r } label: {
                    HStack {
                        VStack(alignment: .leading) {
                            Text(r.name).font(.headline)
                            Text(r.owner).font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                        if r.isPrivate { Image(systemName: "lock.fill").foregroundStyle(.secondary) }
                        if r.running { Circle().fill(.green).frame(width: 10, height: 10) }
                        else if r.cloning { ProgressView() }
                        else if r.local { Image(systemName: "externaldrive").foregroundStyle(.secondary) }
                    }
                }.tint(.primary)
            }
            .searchable(text: $search)
            .navigationTitle("Projects")
            .toolbar { Button { showNew = true } label: { Image(systemName: "plus") } }
            .refreshable { await app.refreshRepos(force: true) }
            .task { await app.refreshRepos(force: false) }
            .sheet(isPresented: $showNew) { NewProjectView().environmentObject(app) }
            .confirmationDialog(picked?.fullName ?? "", isPresented: Binding(get: { picked != nil }, set: { if !$0 { picked = nil } }), titleVisibility: .visible) {
                if let r = picked {
                    if r.running { Button("Stop Claude", role: .destructive) { app.stop(r.name) } }
                    else if r.local { Button("Start Claude") { app.start(r.name) } }
                    Button(r.local ? "Update from GitHub" : "Clone") { app.openRepo(r, start: false) }
                    if !r.running { Button(r.local ? "Update and start Claude" : "Clone and start Claude") { app.openRepo(r, start: true) } }
                }
            }
        }
    }
}

struct NewProjectView: View {
    @EnvironmentObject var app: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var owner = ""
    @State private var isPrivate = true
    @State private var start = true

    var valid: Bool { name.range(of: "^[A-Za-z0-9._][A-Za-z0-9._-]{0,99}$", options: .regularExpression) != nil }

    var body: some View {
        NavigationStack {
            Form {
                TextField("Project name", text: $name).textInputAutocapitalization(.never).autocorrectionDisabled()
                if let o = app.owners {
                    Picker("Owner", selection: $owner) {
                        ForEach([o.user] + o.orgs, id: \.self) { Text($0 == o.user ? "\($0) (you)" : $0).tag($0) }
                    }
                }
                Toggle("Private repository", isOn: $isPrivate)
                Toggle("Start Claude after creating", isOn: $start)
                Button("Create") { app.createProject(name: name, owner: owner, isPrivate: isPrivate, start: start); dismiss() }
                    .disabled(!valid || owner.isEmpty)
            }
            .navigationTitle("New project")
            .toolbar { Button("Cancel") { dismiss() } }
            .task { await app.loadOwners(); if owner.isEmpty, let o = app.owners { owner = o.defaultOwner ?? o.user } }
        }
    }
}

/// The session's screen as text, with the keys Claude's prompts need.
struct TerminalView: View {
    @EnvironmentObject var app: AppModel
    @Environment(\.dismiss) private var dismiss
    let session: String
    @State private var text = ""

    var body: some View {
        NavigationStack {
            VStack(spacing: 8) {
                ScrollView { Text(text).font(.system(size: 11, design: .monospaced)).frame(maxWidth: .infinity, alignment: .leading).textSelection(.enabled).padding(8) }
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack {
                        ForEach([("1", "1"), ("2", "2"), ("3", "3"), ("Up", "↑"), ("Down", "↓"), ("y", "y"), ("n", "n"), ("Escape", "Esc"), ("Enter", "Enter ⏎")], id: \.0) { k in
                            Button(k.1) { send(k.0) }.buttonStyle(.bordered)
                        }
                    }.padding(.horizontal)
                }
            }
            .navigationTitle(session).navigationBarTitleDisplayMode(.inline)
            .toolbar { Button("Done") { dismiss() } }
            .task {
                while !Task.isCancelled {
                    if let r = try? await app.api.tail(session) { text = r.text }
                    try? await Task.sleep(nanoseconds: 3_000_000_000)
                }
            }
        }
    }

    private func send(_ k: String) {
        Task { do { text = try await app.api.keys(session, [k]).text } catch { app.fail(error) } }
    }
}
