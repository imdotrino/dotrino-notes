package com.dotrino.notes

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.Normalizer

/** A note, the same shape the PWA keeps (src/lib/repo.js): `{ id, body, pinned, created, ts }`. */
data class Note(val id: String, val body: String, val pinned: Boolean, val created: Long, val ts: Long)

/**
 * The state of the backup in the vault, for the indicator. [pending] = writes made here that no
 * successful sync has carried yet; [error] = the code of the last failure.
 */
data class SyncState(
    val paired: Boolean,
    val syncing: Boolean = false,
    val lastSyncAt: Long? = null,
    val error: String? = null,
    val errorMessage: String? = null,
    val pending: Int = 0,
    val tooLarge: Int = 0,
)

/**
 * The pure rules of the notes, the SAME as the PWA's src/lib/notes.js (title, order, search,
 * limit, indicator colour). No store and no views, so the JVM tests cover them.
 */
object NotesLogic {
    /** The thread, the same as the PWA's: the vault carries it between the two. */
    const val THREAD = "notes.items"
    /** An entry over ~550 KB does not fit through the proxy and is not backed up (@dotrino/store). */
    const val MAX_BODY = 100_000

    private fun lines(body: String) = body.split("\n").map { it.trim() }

    fun titleOf(body: String, max: Int = 80): String {
        val line = lines(body).firstOrNull { it.isNotEmpty() } ?: ""
        return if (line.length > max) line.take(max - 1) + "…" else line
    }

    fun previewOf(body: String, max: Int = 140): String {
        val rest = lines(body).filter { it.isNotEmpty() }.drop(1).joinToString(" ")
        return if (rest.length > max) rest.take(max - 1) + "…" else rest
    }

    /** Pinned first; within each group, the most recent on top. */
    fun sort(notes: List<Note>): List<Note> =
        notes.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.ts })

    /** Case and accent insensitive; every word of the query has to be there. */
    fun filter(notes: List<Note>, query: String): List<Note> {
        val words = fold(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return notes
        return notes.filter { n -> val text = fold(n.body); words.all { text.contains(it) } }
    }

    private val marks = Regex("[̀-ͯ]")
    fun fold(s: String): String = marks.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase()

    /** `green` only when everything is in the vault and nothing is waiting; `yellow` otherwise. */
    fun light(s: SyncState?): String = when {
        s == null -> "checking"
        s.paired && s.error == null && s.lastSyncAt != null && s.pending == 0 && s.tooLarge == 0 -> "green"
        else -> "yellow"
    }

    /** What to do with the open note when another device's version arrives (`mergeOpen` in the PWA). */
    fun mergeOpen(dirty: Boolean, incoming: Note?): String = if (dirty) "keep" else if (incoming != null) "replace" else "close"

    private fun num(o: JsonObject, k: String): Long? = (o[k] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toLong()

    fun fromEntry(e: JsonObject): Note {
        val id = (e["id"] as? JsonPrimitive)?.content ?: throw IllegalStateException("note without id")
        val body = (e["body"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: ""
        val ts = num(e, "ts") ?: 0L
        return Note(id, body, (e["pinned"] as? JsonPrimitive)?.booleanOrNull == true, num(e, "created") ?: ts, ts)
    }

    fun toEntry(n: Note): JsonObject = buildJsonObject {
        put("id", n.id); put("body", n.body); put("pinned", n.pinned); put("created", n.created); put("ts", n.ts)
    }
}
