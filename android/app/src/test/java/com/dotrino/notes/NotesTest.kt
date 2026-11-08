package com.dotrino.notes

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

/** The same cases as the PWA's test/notes.test.js: the two versions have to agree. */
class NotesTest {
    private fun n(id: String, body: String, ts: Long, pinned: Boolean = false) = Note(id, body, pinned, ts, ts)

    @Test fun titleIsTheFirstLineWithText() {
        assertEquals("Compras", NotesLogic.titleOf("\n  Compras  \nleche"))
        assertEquals("", NotesLogic.titleOf("   \n "))
        assertEquals("a".repeat(79) + "…", NotesLogic.titleOf("a".repeat(100)))
    }

    @Test fun previewIsWhatFollowsInOneLine() {
        assertEquals("leche pan", NotesLogic.previewOf("Compras\nleche\n\npan"))
        assertEquals("", NotesLogic.previewOf("solo título"))
    }

    @Test fun pinnedFirstThenNewest() {
        val out = NotesLogic.sort(listOf(n("a", "a", 1), n("b", "b", 3), n("c", "c", 2, pinned = true)))
        assertEquals(listOf("c", "b", "a"), out.map { it.id })
    }

    @Test fun searchIgnoresCaseAndAccentsAndNeedsEveryWord() {
        val notes = listOf(n("1", "Canción de cumpleaños", 1), n("2", "Lista de compras", 2))
        assertEquals(listOf("1"), NotesLogic.filter(notes, "CANCION cumple").map { it.id })
        assertEquals(listOf("1", "2"), NotesLogic.filter(notes, "  ").map { it.id })
        assertEquals(emptyList<String>(), NotesLogic.filter(notes, "cancion compras").map { it.id })
    }

    @Test fun greenOnlyWhenEverythingIsInTheVault() {
        assertEquals("checking", NotesLogic.light(null))
        assertEquals("yellow", NotesLogic.light(SyncState(paired = false)))
        assertEquals("yellow", NotesLogic.light(SyncState(paired = true)))   // never synced yet
        assertEquals("green", NotesLogic.light(SyncState(paired = true, lastSyncAt = 1)))
        assertEquals("yellow", NotesLogic.light(SyncState(paired = true, lastSyncAt = 1, pending = 1)))
        assertEquals("yellow", NotesLogic.light(SyncState(paired = true, lastSyncAt = 1, tooLarge = 1)))
        assertEquals("yellow", NotesLogic.light(SyncState(paired = true, lastSyncAt = 1, error = "vault-no-reply")))
    }

    @Test fun mergeOpenKeepsUnsavedWriting() {
        assertEquals("keep", NotesLogic.mergeOpen(true, null))
        assertEquals("replace", NotesLogic.mergeOpen(false, n("a", "x", 1)))
        assertEquals("close", NotesLogic.mergeOpen(false, null))
    }

    @Test fun entryRoundTripsWithThePwaShape() {
        val e = buildJsonObject { put("id", "x"); put("body", "hola"); put("pinned", true); put("created", 5); put("ts", JsonPrimitive(7.0)) }
        val note = NotesLogic.fromEntry(e)
        assertEquals(Note("x", "hola", true, 5, 7), note)
        assertEquals(note, NotesLogic.fromEntry(NotesLogic.toEntry(note)))
    }

    @Test fun everyTextTheScreenUsesExists() {
        val text = javaClass.classLoader!!.getResource("i18n.json").readText()
        for (lang in listOf("es", "en")) {
            I18n.use(I18n.parse(text), lang)
            for (k in listOf("list.title", "list.new", "list.search", "list.empty", "list.noResults", "list.untitled", "list.pinned",
                "editor.placeholder", "editor.pick", "editor.pin", "editor.unpin", "editor.remove", "editor.removeTitle", "editor.removeBody",
                "editor.removeConfirm", "editor.cancel", "editor.removed", "editor.saving", "sync.button", "sync.busy", "sync.done",
                "sync.green", "sync.yellow", "sync.checking", "sync.howToLink", "sync.off.not-paired", "boot.loading", "boot.retry",
                "native.noProfile", "native.noIdentityApp", "native.createProfile", "native.adoptProfile", "native.back")) I18n.t(k)
            I18n.t("editor.edited", "when" to "x"); I18n.t("sync.pending", "count" to 1); I18n.t("sync.lastSync", "when" to "x")
            I18n.t("sync.tooLarge", "count" to 1); I18n.t("sync.errors.other", "message" to "x"); I18n.t("errors.unknown", "message" to "x")
            I18n.t("editor.tooLong", "count" to 1, "max" to 2)
        }
    }
}
