import SwiftUI
import UIKit

struct SetupView: View {
    @EnvironmentObject var app: AppModel
    @State private var host = ""
    @State private var port = "22"
    @State private var user = ""

    var body: some View {
        Form {
            Section("Your server") {
                TextField("Host or IP", text: $host).textInputAutocapitalization(.never).autocorrectionDisabled()
                TextField("SSH port", text: $port).keyboardType(.numberPad)
                TextField("Username", text: $user).textInputAutocapitalization(.never).autocorrectionDisabled()
            }
            Section {
                Button("Check connection") { app.saveAndProbe(host: host, port: Int(port) ?? 22, user: user) }
                    .disabled(host.isEmpty || user.isEmpty)
            } footer: { Text("The app first shows the server's fingerprint for you to trust, then connects with this iPhone's own key.") }
            KeySection()
        }
        .navigationTitle("Set up cLaudeRC")
        .onAppear { host = app.server.host; user = app.server.user; port = String(app.server.port) }
    }
}

/// This iPhone's public key and the command that authorizes it on the server.
struct KeySection: View {
    @State private var key = KeyStore.publicKeyLine()
    @State private var confirmRegen = false

    var body: some View {
        Section {
            Text(key).font(.system(.caption, design: .monospaced)).textSelection(.enabled)
            HStack {
                Button("Copy") { UIPasteboard.general.string = key }
                Spacer()
                ShareLink("Share", item: key)
            }
            Button("Regenerate key…", role: .destructive) { confirmRegen = true }
        } header: { Text("This iPhone's key") } footer: {
            Text("On the server run `~/bin/install-launcher-key.sh '<this key>'`. The private half never leaves the Keychain.")
        }
        .confirmationDialog("Make a new key? The server stops accepting this iPhone until you authorize the new one.", isPresented: $confirmRegen, titleVisibility: .visible) {
            Button("Regenerate", role: .destructive) { KeyStore.regenerate(); key = KeyStore.publicKeyLine() }
        }
    }
}

struct SettingsView: View {
    @EnvironmentObject var app: AppModel
    @State private var confirmForget = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    LabeledContent("Host", value: "\(app.server.host):\(app.server.port)")
                    LabeledContent("User", value: app.server.user)
                    if let s = app.status {
                        LabeledContent("Name", value: s.hostname)
                        LabeledContent("Claude", value: s.claude ? "Signed in" : "Not signed in")
                        LabeledContent("GitHub", value: s.github ? (s.githubUser ?? "Signed in") : "Not signed in")
                        if s.scriptApi < 30 { Text("The server scripts are older than this app expects. Update them from the Android app or re-run the installer.").font(.footnote).foregroundStyle(.orange) }
                    }
                    Button("Refresh") { app.refreshStatus() }
                    Button("Re-check host key") { app.saveAndProbe(host: app.server.host, port: app.server.port, user: app.server.user) }
                    Button("Remove this server", role: .destructive) { confirmForget = true }
                } header: { Text("Server") } footer: { Text("Host key \(app.fingerprint)").font(.system(.caption2, design: .monospaced)) }
                KeySection()
            }
            .navigationTitle("Settings")
            .confirmationDialog("Forget this server?", isPresented: $confirmForget, titleVisibility: .visible) {
                Button("Remove", role: .destructive) { app.forgetServer() }
            }
        }
    }
}
