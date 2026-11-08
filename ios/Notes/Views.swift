import DotrinoNative
import DotrinoNativeUI
import SwiftUI

/// The app, the same as the PWA: the panel with the notes (create, search, open, pin, delete, and
/// whether they are in the vault) and, from there, the editor, which only writes. SwiftUI, no
/// WebView (CONVENCIONES §16.2).
struct RootView: View {
    @StateObject private var m = AppModel()
    @ObservedObject private var lang = DotrinoLang.shared
    @Environment(\.scenePhase) private var scene
    /// Creating or adopting the first profile, inside the app (DotrinoWebSheet).
    @State private var profileWeb: URL?

    var body: some View {
        VStack(spacing: 0) {
            DotrinoTopbar(repo: "imdotrino/dotrino-notes", brand: .init(name: "Notes", image: Image("Brand")),
                          profile: m.topbarProfile, onProfileChanged: { Task { await m.reboot() } }) { EmptyView() }
            content.frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .background(Palette.bg.ignoresSafeArea())
        .task { await m.boot() }
        .onChange(of: scene) { phase in
            if phase == .active { m.syncSoon(0) } else if phase == .background { Task { await m.flush() } }
        }
        .fullScreenCover(isPresented: Binding(get: { profileWeb != nil }, set: { if !$0 { profileWeb = nil } })) {
            if let u = profileWeb { DotrinoWebSheet(url: u) { profileWeb = nil; Task { await m.reboot() } } }
        }
        .overlay(alignment: .bottom) {
            if let msg = m.toast {
                Text(msg).font(.subheadline).foregroundColor(.white).padding(.horizontal, 16).padding(.vertical, 10)
                    .background(Capsule().fill(Palette.text.opacity(0.92))).padding(.bottom, 24)
                    .accessibilityIdentifier("toast")
                    .task(id: msg) { try? await Task.sleep(nanoseconds: 3_000_000_000); if m.toast == msg { m.toast = nil } }
            }
        }
        .id(lang.code)
    }

    @ViewBuilder private var content: some View {
        if let p = m.problem {
            VStack(spacing: 18) {
                Text(p).foregroundColor(Palette.muted).multilineTextAlignment(.center).accessibilityIdentifier("boot-error")
                if m.noProfile {
                    Button(t("native.createProfile")) { profileWeb = DotrinoTopbarURLs.create }.buttonStyle(Pill(filled: true))
                    Button(t("native.adoptProfile")) { profileWeb = DotrinoTopbarURLs.adopt }.buttonStyle(Pill(filled: false))
                }
                Button(t("boot.retry")) { Task { await m.reboot() } }.buttonStyle(Pill(filled: !m.noProfile)).accessibilityIdentifier("boot-retry")
            }.padding(32)
        } else if !m.booted {
            VStack(spacing: 12) { ProgressView(); Text(t("boot.loading")).foregroundColor(Palette.muted) }
        } else if let ed = m.editor {
            EditorView(m: m, key: ed.key).id(ed.key)
        } else {
            PanelView(m: m)
        }
    }
}

/// A pill: the «Cool & Cozy» button. `filled` = the main action.
struct Pill: ButtonStyle {
    var filled = false
    var color = Palette.accent
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.font(.body.weight(.bold))
            .foregroundColor(filled ? Palette.onAccent : Palette.text)
            .padding(.horizontal, 20).padding(.vertical, 12)
            .background(Capsule().fill(filled ? color : Palette.card))
            .overlay(Capsule().stroke(filled ? Color.clear : Palette.line2))
            .opacity(configuration.isPressed ? 0.8 : 1)
    }
}

struct PanelView: View {
    @ObservedObject var m: AppModel
    @State private var query = ""
    @State private var confirming: Note?

    var body: some View {
        let visible = NotesLogic.sort(NotesLogic.filter(m.notes, query))
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text(t("list.title")).font(.title2.bold()).foregroundColor(Palette.text)
                SyncBar(m: m)
                Button { Task { await m.newNote() } } label: { Text("+ " + t("list.new")).frame(maxWidth: .infinity) }
                    .buttonStyle(Pill(filled: true)).accessibilityIdentifier("new-note")
                TextField(t("list.search"), text: $query).textFieldStyle(.plain).autocorrectionDisabled()
                    .padding(12).background(RoundedRectangle(cornerRadius: 16).fill(Palette.card))
                    .overlay(RoundedRectangle(cornerRadius: 16).stroke(Palette.line2)).accessibilityIdentifier("search")
                if m.notes.isEmpty {
                    Text(t("list.empty")).foregroundColor(Palette.muted).multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity).padding(.top, 24).accessibilityIdentifier("list-empty")
                } else if visible.isEmpty {
                    Text(t("list.noResults")).foregroundColor(Palette.muted).frame(maxWidth: .infinity).padding(.top, 24)
                } else {
                    ForEach(visible) { n in row(n) }
                }
            }.padding(14)
        }
        .confirmationDialog(t("editor.removeTitle"), isPresented: Binding(get: { confirming != nil }, set: { if !$0 { confirming = nil } }),
                            titleVisibility: .visible, presenting: confirming) { n in
            Button(t("editor.removeConfirm"), role: .destructive) { Task { await m.remove(n.id) } }.accessibilityIdentifier("remove-confirm")
            Button(t("editor.cancel"), role: .cancel) {}
        } message: { n in
            Text((NotesLogic.titleOf(n.body).isEmpty ? t("list.untitled") : NotesLogic.titleOf(n.body)) + "\n" + t("editor.removeBody"))
        }
    }

    private func row(_ n: Note) -> some View {
        HStack(spacing: 4) {
            Button { Task { await m.open(n) } } label: {
                VStack(alignment: .leading, spacing: 3) {
                    let title = NotesLogic.titleOf(n.body)
                    Text(title.isEmpty ? t("list.untitled") : title).font(.body.bold()).foregroundColor(Palette.text).lineLimit(1)
                    let preview = NotesLogic.previewOf(n.body)
                    if !preview.isEmpty { Text(preview).font(.subheadline).foregroundColor(Palette.muted).lineLimit(2) }
                    Text(I18n.shortWhen(n.ts)).font(.caption).foregroundColor(Palette.muted)
                }.frame(maxWidth: .infinity, alignment: .leading).contentShape(Rectangle())
            }.buttonStyle(.plain).accessibilityIdentifier("note-item")
            Button { Task { await m.togglePin(n.id) } } label: { Text("📌").opacity(n.pinned ? 1 : 0.35).padding(8) }
                .accessibilityLabel(n.pinned ? t("editor.unpin") : t("editor.pin")).accessibilityIdentifier("note-pin")
            Button { confirming = n } label: { Text("🗑").padding(8) }
                .accessibilityLabel(t("editor.remove")).accessibilityIdentifier("note-remove")
        }
        .padding(.leading, 14).padding(.vertical, 10).padding(.trailing, 4)
        .background(RoundedRectangle(cornerRadius: 16).fill(Palette.card))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Palette.line))
    }
}

/// Green if everything is in the vault, yellow if something stays only on this phone, and the
/// «Sync» button to do it now instead of waiting.
struct SyncBar: View {
    @ObservedObject var m: AppModel
    @State private var open = false

    var body: some View {
        let s = m.sync
        let light = NotesLogic.light(s)
        let busy = s?.syncing == true
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) {
                Button { open.toggle() } label: {
                    HStack(spacing: 8) {
                        Circle().fill(light == "green" ? Palette.green : light == "yellow" ? Palette.yellow : Palette.line2).frame(width: 12, height: 12)
                        Text(busy ? t("sync.busy") : t("sync.\(light)")).font(.footnote).foregroundColor(Palette.text)
                            .multilineTextAlignment(.leading)
                    }.frame(maxWidth: .infinity, alignment: .leading).contentShape(Rectangle())
                }.buttonStyle(.plain).accessibilityIdentifier("sync-state").accessibilityValue(light)
                // Without a paired vault there is nobody to sync with: the button is there, but off.
                Button(busy ? t("sync.busy") : t("sync.button")) { Task { await m.syncButton() } }
                    .font(.footnote.bold()).foregroundColor(Palette.text)
                    .padding(.horizontal, 12).padding(.vertical, 6)
                    .background(Capsule().fill(Palette.card2)).overlay(Capsule().stroke(Palette.line2))
                    .disabled(s == nil || s?.paired == false || busy).opacity(s?.paired == true && !busy ? 1 : 0.45)
                    .accessibilityIdentifier("sync-now")
            }
            if let reason = reason(s) { Text(reason).font(.caption).foregroundColor(Palette.muted).accessibilityIdentifier("sync-reason") }
            if open, let s {
                if s.pending > 0 { Text(t("sync.pending", ["count": s.pending])).font(.caption).foregroundColor(Palette.muted) }
                if s.tooLarge > 0 { Text(t("sync.tooLarge", ["count": s.tooLarge])).font(.caption).foregroundColor(Palette.danger) }
                if let at = s.lastSyncAt { Text(t("sync.lastSync", ["when": I18n.dateTime(at)])).font(.caption).foregroundColor(Palette.muted) }
            }
            if s?.paired == false {
                Link(t("sync.howToLink"), destination: URL(string: "https://wiki.dotrino.com/" + (I18n.lang == "es" ? "" : "en/") + "vault/emparejar/")!)
                    .font(.caption.bold()).foregroundColor(Palette.accent)
            }
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 16).fill(Palette.card))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Palette.line))
        .accessibilityElement(children: .contain).accessibilityIdentifier("sync")
    }

    private func reason(_ s: SyncState?) -> String? {
        guard let s else { return nil }
        if !s.paired { return t("sync.off.not-paired") }
        if let e = s.error { return AppModel.errorText(e, s.errorMessage) }
        return nil
    }
}

struct EditorView: View {
    @ObservedObject var m: AppModel
    let key: String
    @State private var text = ""
    @FocusState private var focused: Bool

    var body: some View {
        let ed = m.editor
        let saved = m.notes.first { $0.id == ed?.id }
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 10) {
                Button { Task { await m.close() } } label: { Text("‹").font(.system(size: 30)).foregroundColor(Palette.accent).padding(.horizontal, 4) }
                    .accessibilityLabel(t("native.back")).accessibilityIdentifier("editor-back")
                Group {
                    if ed?.dirty == true { Text(t("editor.saving")) }
                    else if let saved { Text(t("editor.edited", ["when": I18n.shortWhen(saved.ts)]) + (ed?.pinned == true ? "  ·  📌 " + t("list.pinned") : "")) }
                }.font(.footnote).foregroundColor(Palette.muted).accessibilityIdentifier("editor-status")
                Spacer()
            }
            ZStack(alignment: .topLeading) {
                if text.isEmpty {
                    Text(t("editor.placeholder")).foregroundColor(Palette.muted).padding(.horizontal, 21).padding(.vertical, 22)
                }
                TextEditor(text: $text).font(.system(size: 17)).foregroundColor(Palette.text)
                    .scrollContentBackground(.hidden).padding(12).focused($focused)
                    .accessibilityLabel(t("editor.placeholder")).accessibilityIdentifier("editor-body")
            }
            .background(RoundedRectangle(cornerRadius: 16).fill(Palette.card))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(Palette.line))
            if let ed, NotesLogic.length(ed.body) > NotesLogic.maxBody {
                Text(t("editor.tooLong", ["count": NotesLogic.length(ed.body), "max": NotesLogic.maxBody])).font(.footnote).foregroundColor(Palette.danger)
            }
        }
        .padding(12)
        .onAppear {
            text = m.editor?.body ?? ""
            // A new note is written without touching anything else.
            if m.editor?.id == nil { focused = true }
        }
        .onChange(of: text) { m.edit($0) }
        // What arrived from another device (only when nothing is being written here).
        .onChange(of: m.editor?.body) { b in if let b, b != text { text = b } }
    }
}
