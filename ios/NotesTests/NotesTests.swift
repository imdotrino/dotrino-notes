@testable import Notes
import DotrinoNative
import XCTest

/// The same cases as the PWA's test/notes.test.js and Android's NotesTest.kt: the three agree.
final class NotesTests: XCTestCase {
    private func n(_ id: String, _ body: String, _ ts: Int64, pinned: Bool = false) -> Note { Note(id: id, body: body, pinned: pinned, created: ts, ts: ts) }

    func testTitleIsTheFirstLineWithText() {
        XCTAssertEqual(NotesLogic.titleOf("\n  Compras  \nleche"), "Compras")
        XCTAssertEqual(NotesLogic.titleOf("   \n "), "")
        XCTAssertEqual(NotesLogic.titleOf(String(repeating: "a", count: 100)), String(repeating: "a", count: 79) + "…")
    }

    func testPreviewIsWhatFollowsInOneLine() {
        XCTAssertEqual(NotesLogic.previewOf("Compras\nleche\n\npan"), "leche pan")
        XCTAssertEqual(NotesLogic.previewOf("solo título"), "")
    }

    func testPinnedFirstThenNewest() {
        XCTAssertEqual(NotesLogic.sort([n("a", "a", 1), n("b", "b", 3), n("c", "c", 2, pinned: true)]).map(\.id), ["c", "b", "a"])
    }

    func testSearchIgnoresCaseAndAccentsAndNeedsEveryWord() {
        let notes = [n("1", "Canción de cumpleaños", 1), n("2", "Lista de compras", 2)]
        XCTAssertEqual(NotesLogic.filter(notes, "CANCION cumple").map(\.id), ["1"])
        XCTAssertEqual(NotesLogic.filter(notes, "  ").map(\.id), ["1", "2"])
        XCTAssertEqual(NotesLogic.filter(notes, "cancion compras").map(\.id), [])
    }

    func testGreenOnlyWhenEverythingIsInTheVault() {
        XCTAssertEqual(NotesLogic.light(nil), "checking")
        XCTAssertEqual(NotesLogic.light(SyncState(paired: false)), "yellow")
        XCTAssertEqual(NotesLogic.light(SyncState(paired: true)), "yellow")
        XCTAssertEqual(NotesLogic.light(SyncState(paired: true, lastSyncAt: 1)), "green")
        XCTAssertEqual(NotesLogic.light(SyncState(paired: true, lastSyncAt: 1, pending: 1)), "yellow")
        XCTAssertEqual(NotesLogic.light(SyncState(paired: true, lastSyncAt: 1, tooLarge: 1)), "yellow")
        XCTAssertEqual(NotesLogic.light(SyncState(paired: true, lastSyncAt: 1, error: "vault-no-reply")), "yellow")
    }

    func testMergeOpenKeepsUnsavedWriting() {
        XCTAssertEqual(NotesLogic.mergeOpen(dirty: true, incoming: nil), "keep")
        XCTAssertEqual(NotesLogic.mergeOpen(dirty: false, incoming: n("a", "x", 1)), "replace")
        XCTAssertEqual(NotesLogic.mergeOpen(dirty: false, incoming: nil), "close")
    }

    func testEntryRoundTripsWithThePwaShape() {
        let e: JSON = ["id": "x", "body": "hola", "pinned": true, "created": 5, "ts": .double(7)]
        let note = NotesLogic.fromEntry(e)
        XCTAssertEqual(note, Note(id: "x", body: "hola", pinned: true, created: 5, ts: 7))
        XCTAssertEqual(NotesLogic.fromEntry(NotesLogic.toEntry(note!)), note)
    }

    func testEveryLanguageHasTheSameTexts() {
        XCTAssertEqual(Set(I18n.dict["es"]!.keys), Set(I18n.dict["en"]!.keys))
        for k in ["list.new", "sync.green", "sync.yellow", "sync.off.not-paired", "native.back", "editor.removeTitle"] {
            XCTAssertNotNil(I18n.dict["es"]?[k], k)
        }
    }
}
