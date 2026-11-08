import DotrinoNative
import Foundation

/// A note, the same shape the PWA keeps (src/lib/repo.js): `{ id, body, pinned, created, ts }`.
struct Note: Identifiable, Equatable {
    let id: String
    var body: String
    var pinned: Bool
    var created: Int64
    var ts: Int64
}

/// The state of the backup in the vault, for the indicator. `pending` = writes made here that no
/// successful sync has carried yet; `error` = the code of the last failure.
struct SyncState: Equatable {
    var paired: Bool
    var syncing = false
    var lastSyncAt: Int64?
    var error: String?
    var errorMessage: String?
    var pending = 0
    var tooLarge = 0
}

/// The pure rules of the notes, the SAME as the PWA's src/lib/notes.js and Android's Notes.kt.
enum NotesLogic {
    /// The thread, the same as the PWA's: the vault carries it between the two.
    static let thread = "notes.items"
    /// An entry over ~550 KB does not fit through the proxy and is not backed up (@dotrino/store).
    static let maxBody = 100_000

    private static func lines(_ body: String) -> [String] {
        body.components(separatedBy: "\n").map { $0.trimmingCharacters(in: .whitespaces) }
    }

    static func titleOf(_ body: String, max: Int = 80) -> String {
        let line = lines(body).first { !$0.isEmpty } ?? ""
        return line.count > max ? String(line.prefix(max - 1)) + "…" : line
    }

    static func previewOf(_ body: String, max: Int = 140) -> String {
        let rest = lines(body).filter { !$0.isEmpty }.dropFirst().joined(separator: " ")
        return rest.count > max ? String(rest.prefix(max - 1)) + "…" : rest
    }

    /// Pinned first; within each group, the most recent on top.
    static func sort(_ notes: [Note]) -> [Note] {
        notes.sorted { a, b in a.pinned != b.pinned ? a.pinned : a.ts > b.ts }
    }

    /// Case and accent insensitive; every word of the query has to be there.
    static func filter(_ notes: [Note], _ query: String) -> [Note] {
        let words = fold(query).split(whereSeparator: { $0.isWhitespace }).map(String.init)
        if words.isEmpty { return notes }
        return notes.filter { n in let text = fold(n.body); return words.allSatisfy { text.contains($0) } }
    }

    static func fold(_ s: String) -> String {
        s.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "en_US_POSIX")).lowercased()
    }

    /// The length the PWA measures (UTF-16 units), so the limit is the same on both.
    static func length(_ s: String) -> Int { s.utf16.count }

    /// `green` only when everything is in the vault and nothing is waiting; `yellow` otherwise.
    static func light(_ s: SyncState?) -> String {
        guard let s else { return "checking" }
        return s.paired && s.error == nil && s.lastSyncAt != nil && s.pending == 0 && s.tooLarge == 0 ? "green" : "yellow"
    }

    /// What to do with the open note when another device's version arrives (`mergeOpen` in the PWA).
    static func mergeOpen(dirty: Bool, incoming: Note?) -> String { dirty ? "keep" : incoming != nil ? "replace" : "close" }

    private static func num(_ v: JSON?) -> Int64? {
        switch v { case .int(let n)?: return n; case .double(let d)?: return Int64(d); default: return nil }
    }

    static func fromEntry(_ e: JSON) -> Note? {
        guard let id = e["id"]?.string else { return nil }
        let ts = num(e["ts"]) ?? 0
        return Note(id: id, body: e["body"]?.string ?? "", pinned: e["pinned"]?.bool == true, created: num(e["created"]) ?? ts, ts: ts)
    }

    static func toEntry(_ n: Note) -> JSON {
        ["id": .string(n.id), "body": .string(n.body), "pinned": .bool(n.pinned), "created": .int(n.created), "ts": .int(n.ts)]
    }
}
