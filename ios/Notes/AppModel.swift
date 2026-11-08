import DotrinoNative
import DotrinoNativeUI
import Foundation

/// The notes and what the screens show, on the main thread. The store is THIS account's
/// (`notes-<pid>`), and when the phone is paired the backup in the vault carries the thread
/// `notes.items` to the PWA and the other devices of the account, and back.
@MainActor
final class AppModel: ObservableObject {
    /// The note being written. `id` nil = new and not saved yet (an empty new note is never saved).
    struct Editor: Equatable {
        let key: String
        var id: String?
        var body: String
        var pinned: Bool
        var created: Int64
        var dirty = false
    }

    @Published var problem: String?
    /// There is no profile yet: it is made or adopted HERE (no other app is needed).
    @Published var noProfile = false
    @Published var booted = false
    @Published var notes: [Note] = []
    @Published var editor: Editor?
    @Published var sync: SyncState?
    @Published var topbarProfile: DotrinoTopbarProfile?
    @Published var toast: String?

    private var store: DotrinoStore?
    private var backup: VaultBackup?
    private var syncTask: Task<Void, Never>?
    private var loop: Task<Void, Never>?
    private var autosave: Task<Void, Never>?
    private var saving = false
    private var booting = false

    private static func now() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

    func boot() async {
        if booted || booting { return }
        booting = true
        defer { booting = false }
        do {
            let p = try Profile.fromPhone()
            let account = (p.pid ?? "default").lowercased().replacingOccurrences(of: "[^a-z0-9-]", with: "-", options: .regularExpression)
            let s = try DotrinoStore(app: "notes-" + account)
            store = s
            if p.vault != nil { backup = VaultBackup(profile: p, store: s, owns: { $0 == NotesLogic.thread }) }
            sync = SyncState(paired: backup != nil)
            topbarProfile = p.topbar
            try reload()
            booted = true
            if backup != nil {
                loop = Task { [weak self] in
                    while !Task.isCancelled { self?.syncSoon(0); try? await Task.sleep(nanoseconds: 300_000_000_000) }
                }
            }
        } catch let e as Profile.ProfileError {
            problem = e.code == "no-profile" || e.code == "no-profile-keys" ? t("native.noProfile") : t("errors.unknown", ["message": e.code])
            noProfile = e.code == "no-profile" || e.code == "no-profile-keys"
        } catch { problem = t("errors.unknown", ["message": "\(error)"]) }
    }

    /// ANOTHER PROFILE (switched, created, adopted from the bar's menu): everything starts again with it.
    func reboot() async {
        await flush()
        loop?.cancel(); syncTask?.cancel(); autosave?.cancel()
        store = nil; backup = nil; booted = false; noProfile = false; problem = nil
        notes = []; editor = nil; sync = nil; topbarProfile = nil
        await boot()
    }

    private func reload() throws {
        guard let s = store else { return }
        notes = try s.listThread(NotesLogic.thread).compactMap(NotesLogic.fromEntry)
    }

    /// The notes again from the store, keeping what is being written (a sync brought something).
    private func refresh() {
        do { try reload() } catch { print("notes: reload failed:", error); return }
        guard var ed = editor, let id = ed.id else { return }
        let incoming = notes.first { $0.id == id }
        switch NotesLogic.mergeOpen(dirty: ed.dirty, incoming: incoming) {
        case "replace":
            if let n = incoming, n.body != ed.body || n.pinned != ed.pinned { ed.body = n.body; ed.pinned = n.pinned; editor = ed }
        case "close": editor = nil
        default: break
        }
    }

    // MARK: the backup

    private func wrote() {
        sync?.pending += 1
        syncSoon(1.5)
    }

    /// Reconcile with the vault in `delay` seconds (a moment after a write, or now).
    func syncSoon(_ delay: Double) {
        guard backup != nil else { return }
        syncTask?.cancel()
        syncTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
            if Task.isCancelled { return }
            do { try await self?.syncNow() } catch { print("notes: vault backup failed:", error) }
        }
    }

    static func codeOf(_ e: Error) -> String {
        if let e = e as? VaultBackup.BackupError { return e.code }
        if let e = e as? VaultError { return e.code }
        if let e = e as? ProxyError { return e.code }
        if let e = e as? NotesError { return e.code }
        return "unknown"
    }

    /// Reconcile NOW (the «Sync» button). Throws with the backup's code.
    func syncNow() async throws {
        guard let b = backup else { throw NotesError(description: "this phone is not paired with a vault", code: "not-paired") }
        if sync?.syncing == true { return }
        let carried = sync?.pending ?? 0
        sync?.syncing = true
        do {
            let r = try await b.sync()
            sync?.syncing = false; sync?.error = nil; sync?.errorMessage = nil
            sync?.lastSyncAt = Self.now(); sync?.pending = max(0, (sync?.pending ?? 0) - carried); sync?.tooLarge = r.tooLarge.count
            if !r.changed.isEmpty { refresh() }
        } catch {
            sync?.syncing = false; sync?.error = Self.codeOf(error); sync?.errorMessage = "\(error)"
            throw error
        }
    }

    /// The «Sync» button: what is being written goes first.
    func syncButton() async {
        await flush()
        do { try await syncNow(); toast = t("sync.done") }
        catch { toast = Self.errorText(Self.codeOf(error), "\(error)") }
    }

    static func errorText(_ code: String, _ message: String?) -> String {
        code != "other" && I18n.has("sync.errors.\(code)") ? t("sync.errors.\(code)") : t("sync.errors.other", ["message": message ?? code])
    }

    // MARK: the notes

    private func save(id: String?, body: String, pinned: Bool, created: Int64?) throws -> Note {
        guard let s = store else { throw NotesError(description: "notes: not booted", code: "not-booted") }
        let now = Self.now()
        let n = Note(id: id ?? UUID().uuidString.lowercased(), body: body, pinned: pinned, created: created ?? now, ts: now)
        try s.appendMessage(NotesLogic.thread, NotesLogic.toEntry(n))
        wrote()
        return n
    }

    func newNote() async {
        await flush()
        editor = Editor(key: UUID().uuidString, id: nil, body: "", pinned: false, created: Self.now())
    }

    func open(_ n: Note) async {
        await flush()
        let cur = notes.first { $0.id == n.id } ?? n
        editor = Editor(key: cur.id, id: cur.id, body: cur.body, pinned: cur.pinned, created: cur.created)
    }

    func close() async {
        await flush()
        editor = nil
    }

    func edit(_ body: String) {
        guard var ed = editor, ed.body != body else { return }
        ed.body = body; ed.dirty = true
        editor = ed
        autosave?.cancel()
        autosave = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 800_000_000)
            if Task.isCancelled { return }
            await self?.flush()
        }
    }

    /// Saves the editor if it has changes. Two saves never overlap: the second waits for the first.
    func flush() async {
        autosave?.cancel()
        while saving { try? await Task.sleep(nanoseconds: 20_000_000) }
        guard let ed = editor, ed.dirty else { return }
        // A new note left empty does not exist yet: nothing is saved.
        if ed.id == nil && ed.body.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { editor?.dirty = false; return }
        if NotesLogic.length(ed.body) > NotesLogic.maxBody { return }   // the warning is on screen; nothing is cut
        saving = true
        defer { saving = false }
        do {
            let n = try save(id: ed.id, body: ed.body, pinned: ed.pinned, created: ed.created)
            if editor?.key == ed.key {
                editor?.id = n.id; editor?.created = n.created
                // If the writing went on while saving, it stays dirty.
                editor?.dirty = editor?.body != ed.body
            }
            try reload()
        } catch {
            print("notes: save failed:", error)
            toast = t("errors.unknown", ["message": "\(error)"])
        }
    }

    /// Pin or unpin. If it is the open note, it goes with the rest of its changes.
    func togglePin(_ id: String) async {
        if editor?.id == id {
            editor?.pinned.toggle(); editor?.dirty = true
            await flush()
            return
        }
        guard let n = notes.first(where: { $0.id == id }) else { return }
        do { _ = try save(id: n.id, body: n.body, pinned: !n.pinned, created: n.created); try reload() }
        catch { toast = t("errors.unknown", ["message": "\(error)"]) }
    }

    /// Deletes (with a tombstone: it does not come back from the vault or another device).
    func remove(_ id: String) async {
        let isOpen = editor?.id == id
        if isOpen { autosave?.cancel(); editor?.dirty = false }
        do {
            try store?.removeMessage(NotesLogic.thread, id: id)
            wrote()
            try reload()
            if isOpen { editor = nil }
            toast = t("editor.removed")
        } catch { toast = t("errors.unknown", ["message": "\(error)"]) }
    }
}

/// A failure of the app itself, with a code like the pillar's.
struct NotesError: Error, CustomStringConvertible { let description: String; let code: String }
