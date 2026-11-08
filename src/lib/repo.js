// Qué guarda la app en el almacén del usuario y con qué forma.
//
//   notes.items   { id, body, pinned, created, ts }   una nota por entrada; id = uuid
//
// El almacén fusiona por id, y entre aparatos gana el `ts` más alto. Lo que lleva las notas a los
// otros aparatos es el respaldo del almacén en la bóveda de la cuenta (@dotrino/store ≥ 0.11):
// sube solo 1,5 s después de cada escritura y baja al volver a la pestaña y cada 5 minutos.

import { getStore } from '../services/store.js'
import { validateNote } from './notes.js'
import { codeError } from './errors.js'

/** @typedef {import('../types').Note} Note */
/** @typedef {import('../types').NoteDraft} NoteDraft */
/** @typedef {import('@dotrino/store').ThreadEntry} ThreadEntry */

export const NOTES = 'notes.items'

/** @param {string} key */
export function isNotesThread (key) {
  return key === NOTES
}

/**
 * @param {ThreadEntry} entry
 * @returns {Note}
 */
function toNote (entry) {
  const e = /** @type {Partial<Note>} */ (entry)
  return {
    id: String(e.id),
    body: typeof e.body === 'string' ? e.body : '',
    pinned: !!e.pinned,
    created: Number(e.created || e.ts || 0),
    ts: Number(e.ts || 0),
  }
}

/** @returns {Promise<Note[]>} */
export async function listNotes () {
  const store = await getStore()
  return (await store.listThread(NOTES)).map(toNote)
}

/**
 * Guarda (nueva o cambiada). Reescribir sube `ts`: si no, la huella del hilo no cambia y la
 * bóveda no se entera (@dotrino/store, «Límites que se saben»).
 * @param {NoteDraft} draft
 * @returns {Promise<Note>}
 */
export async function saveNote (draft) {
  const problem = validateNote(draft)
  if (problem) throw codeError(problem, `invalid note: ${problem}`)
  const store = await getStore()
  const now = Date.now()
  /** @type {Note} */
  const note = {
    id: draft.id || crypto.randomUUID(),
    body: draft.body,
    pinned: !!draft.pinned,
    created: draft.created || now,
    ts: now,
  }
  await store.appendMessage(NOTES, { ...note })
  return note
}

/** @param {string} id */
export async function removeNote (id) {
  const store = await getStore()
  // Deja lápida: la nota no vuelve desde la bóveda ni desde otro aparato.
  await store.removeMessage(NOTES, id)
}
