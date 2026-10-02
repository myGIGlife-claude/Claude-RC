import SwiftUI

@main
struct ClaudeRCApp: App {
    @StateObject private var app = AppModel()
    var body: some Scene {
        WindowGroup { RootView().environmentObject(app) }
    }
}

struct RootView: View {
    @EnvironmentObject var app: AppModel

    var body: some View {
        ZStack(alignment: .bottom) {
            if app.server.isConfigured { MainTabs() } else { NavigationStack { SetupView() } }
            VStack(spacing: 8) {
                if let b = app.banner {
                    Text(b).font(.callout).padding(12).frame(maxWidth: .infinity, alignment: .leading)
                        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
                        .onTapGesture { app.banner = nil }
                        .task(id: b) { try? await Task.sleep(nanoseconds: 6_000_000_000); if app.banner == b { app.banner = nil } }
                }
                if let busy = app.busy {
                    HStack { ProgressView(); Text(busy).font(.callout) }.padding(12)
                        .background(.regularMaterial, in: Capsule())
                }
            }.padding()
        }
        .sheet(isPresented: Binding(get: { app.pendingHostKey != nil }, set: { _ in })) {
            if let i = app.pendingHostKey { HostKeySheet(info: i).environmentObject(app) }
        }
    }
}

struct MainTabs: View {
    @EnvironmentObject var app: AppModel
    var body: some View {
        TabView {
            SessionsView().tabItem { Label("Sessions", systemImage: "bubble.left.and.bubble.right") }
            ProjectsView().tabItem { Label("Projects", systemImage: "folder") }
            SettingsView().tabItem { Label("Settings", systemImage: "gear") }
        }
        .fullScreenCover(item: $app.chatSession) { s in ChatView(session: s.name).environmentObject(app) }
        .task { app.refreshStatus() }
    }
}

struct HostKeySheet: View {
    @EnvironmentObject var app: AppModel
    let info: HostKeyInfo
    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                Text("Trust this server?").font(.title2.bold())
                Text("Compare this fingerprint with the one on your server (`ssh-keygen -lf /etc/ssh/ssh_host_\(info.type.replacingOccurrences(of: "ssh-", with: ""))_key.pub`). The app only talks to a server with this exact key.")
                Text(info.fingerprint).font(.system(.callout, design: .monospaced)).textSelection(.enabled)
                Text(info.type).font(.caption).foregroundStyle(.secondary)
                Spacer()
                Button("Trust this server") { app.acceptHostKey(true) }.buttonStyle(.borderedProminent).frame(maxWidth: .infinity)
                Button("Cancel", role: .cancel) { app.acceptHostKey(false) }.frame(maxWidth: .infinity)
            }.padding()
        }.interactiveDismissDisabled()
    }
}
