import SwiftUI

struct ChatView: View {
    @EnvironmentObject var app: AppModel
    @StateObject private var model: ChatModel
    @Environment(\.dismiss) private var dismiss
    let session: String
    @State private var text = ""
    @State private var pin = ""
    @State private var remember = true

    init(session: String) {
        self.session = session
        _model = StateObject(wrappedValue: ChatModel(session: session))
    }

    var body: some View {
        NavigationStack {
            Group {
                if model.needsPin { pinForm } else { chatBody }
            }
            .navigationTitle(session).navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Close") { dismiss() } }
                ToolbarItem(placement: .primaryAction) {
                    if model.chat?.busy == true { Button("Stop") { model.interrupt() } }
                }
            }
        }
        .onAppear { model.start(app: app) }
        .onDisappear { model.stop() }
    }

    private var pinForm: some View {
        Form {
            Section {
                SecureField(model.pinIsSet ? "Chat PIN" : "Choose a chat PIN", text: $pin).keyboardType(.numberPad)
                Toggle("Remember on this iPhone", isOn: $remember)
                Button(model.pinIsSet ? "Unlock chat" : "Set PIN and unlock") { model.unlock(pin: pin, remember: remember) }.disabled(pin.count < 4)
            } header: { Text(model.pinIsSet ? "Enter your chat PIN" : "Protect the chat with a PIN") }
              footer: { if let e = model.error { Text(e).foregroundStyle(.red) } }
        }
    }

    private var chatBody: some View {
        VStack(spacing: 0) {
            if let c = model.chat, !c.model.isEmpty { Text("\(c.model) · \(c.mode)").font(.caption2).foregroundStyle(.secondary).padding(.vertical, 2) }
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 10) {
                        ForEach(model.chat?.messages ?? []) { m in Bubble(message: m) }
                        ForEach(model.pending, id: \.self) { p in
                            Bubble(message: ChatMessage(role: "user", text: p), queued: true)
                                .onTapGesture { model.pending.removeAll { $0 == p } }
                        }
                        if model.chat?.busy == true { ProgressView().padding(.leading) }
                        Color.clear.frame(height: 1).id("end")
                    }.padding()
                }
                .onChange(of: model.chat?.messages.count) { _ in withAnimation { proxy.scrollTo("end") } }
                .onChange(of: model.pending.count) { _ in withAnimation { proxy.scrollTo("end") } }
            }
            if let e = model.error { Text(e).font(.caption).foregroundStyle(.red).padding(.horizontal) }
            if let c = model.chat, c.waiting {
                if let ask = c.ask, !ask.isEmpty { AskCard(questions: ask) { p, o in model.answer(ask, picks: p, others: o) } }
                else { waitingCard(c) }
            }
            HStack(alignment: .bottom) {
                TextField("Message Claude", text: $text, axis: .vertical).lineLimit(1...5)
                    .textFieldStyle(.roundedBorder)
                Button { let t = text; text = ""; model.send(t) { text = $0 } } label: { Image(systemName: "arrow.up.circle.fill").font(.title) }
                    .disabled(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }.padding()
        }
    }

    private func waitingCard(_ c: ChatData) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Claude is waiting for you").font(.subheadline.bold())
            if let s = c.screen { Text(s).font(.system(size: 11, design: .monospaced)).lineLimit(12) }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack {
                    ForEach([("1", "1"), ("2", "2"), ("3", "3"), ("y", "y"), ("n", "n"), ("Escape", "Esc"), ("Enter", "Enter ⏎")], id: \.0) { k in
                        Button(k.1) { model.key(k.0) }.buttonStyle(.bordered)
                    }
                }
            }
        }.padding().background(Color.red.opacity(0.1))
    }
}

extension ChatMessage {
    init(role: String, text: String) { self.id = "pending-" + text; self.role = role; self.text = text; self.files = [] }
}

struct Bubble: View {
    let message: ChatMessage
    var queued = false

    var body: some View {
        let user = message.role == "user"
        HStack {
            if user { Spacer(minLength: 40) }
            VStack(alignment: .leading, spacing: 4) {
                if message.role == "file" {
                    ForEach(message.files, id: \.self) { Label(($0 as NSString).lastPathComponent, systemImage: "doc") }
                } else {
                    Text(rendered).textSelection(.enabled)
                }
                if queued { Text("queued · tap to remove").font(.caption2).foregroundStyle(.secondary) }
            }
            .padding(10)
            .background(user ? Color.accentColor.opacity(0.2) : Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 14))
            .opacity(queued ? 0.6 : 1)
            if !user { Spacer(minLength: 40) }
        }
    }

    private var rendered: AttributedString {
        (try? AttributedString(markdown: message.text, options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace))) ?? AttributedString(message.text)
    }
}

struct AskCard: View {
    let questions: [AskQuestion]
    let onSubmit: ([Set<Int>], [String]) -> Void
    @State private var picks: [Set<Int>]
    @State private var others: [String]

    init(questions: [AskQuestion], onSubmit: @escaping ([Set<Int>], [String]) -> Void) {
        self.questions = questions; self.onSubmit = onSubmit
        _picks = State(initialValue: Array(repeating: [], count: questions.count))
        _others = State(initialValue: Array(repeating: "", count: questions.count))
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                ForEach(Array(questions.enumerated()), id: \.offset) { qi, q in
                    VStack(alignment: .leading, spacing: 6) {
                        Text(q.question).font(.subheadline.bold())
                        ForEach(Array(q.options.enumerated()), id: \.offset) { oi, o in
                            Button {
                                if q.multiSelect { if picks[qi].contains(oi) { picks[qi].remove(oi) } else { picks[qi].insert(oi) } }
                                else { picks[qi] = [oi]; others[qi] = "" }
                            } label: {
                                HStack(alignment: .top) {
                                    Image(systemName: picks[qi].contains(oi) ? "checkmark.circle.fill" : "circle")
                                    VStack(alignment: .leading) { Text(o.label); if !o.description.isEmpty { Text(o.description).font(.caption).foregroundStyle(.secondary) } }
                                    Spacer()
                                }
                            }.tint(.primary)
                        }
                        TextField("Something else…", text: $others[qi]).textFieldStyle(.roundedBorder)
                    }
                }
                Button("Send answers") { onSubmit(picks, others) }.buttonStyle(.borderedProminent)
            }.padding()
        }.frame(maxHeight: 320).background(Color.red.opacity(0.1))
    }
}
