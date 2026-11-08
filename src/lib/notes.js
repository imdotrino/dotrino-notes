// Funciones puras de las notas: título, orden, búsqueda, validación y el color del indicador.
// Sin almacén ni DOM, para poder probarlas con `node --test`.

/** @typedef {import('../types').Note} Note */
/** @typedef {import('../types').SyncLight} SyncLight */

// Una entrada de más de ~550 KB no cabe por el proxio y no se respalda (@dotrino/store). Con
// 100 000 caracteres una nota queda lejos de ese tope aunque sea todo texto de 4 bytes.
export const MAX_BODY = 100_000

/**
 * El título es la primera línea con texto, recortada.
 * @param {string} body
 * @param {number} [max]
 * @returns {string} '' si la nota está vacía
 */
export function titleOf (body, max = 80) {
  const line = String(body || '').split('\n').map((l) => l.trim()).find((l) => l) || ''
  return line.length > max ? line.slice(0, max - 1) + '…' : line
}

/**
 * Lo que sigue al título, en una línea, para la vista previa de la lista.
 * @param {string} body
 * @param {number} [max]
 * @returns {string}
 */
export function previewOf (body, max = 140) {
  const lines = String(body || '').split('\n').map((l) => l.trim()).filter((l) => l)
  const rest = lines.slice(1).join(' ')
  return rest.length > max ? rest.slice(0, max - 1) + '…' : rest
}

/**
 * Fijadas primero; dentro de cada grupo, la más reciente arriba.
 * @param {Note[]} notes
 * @returns {Note[]} copia ordenada
 */
export function sortNotes (notes) {
  return [...notes].sort((a, b) => (Number(b.pinned) - Number(a.pinned)) || (b.ts - a.ts))
}

/**
 * Sin distinguir mayúsculas ni tildes. Todas las palabras de la búsqueda tienen que estar.
 * @param {Note[]} notes
 * @param {string} query
 * @returns {Note[]}
 */
export function filterNotes (notes, query) {
  const words = fold(query).split(/\s+/).filter((w) => w)
  if (!words.length) return notes
  return notes.filter((n) => {
    const text = fold(n.body)
    return words.every((w) => text.includes(w))
  })
}

/** @param {string} s */
function fold (s) {
  return String(s || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()
}

/**
 * @param {{ body: unknown }} draft
 * @returns {string | null} el código del problema, o null si vale
 */
export function validateNote (draft) {
  if (typeof draft.body !== 'string') return 'bad-note'
  if (draft.body.length > MAX_BODY) return 'note-too-long'
  return null
}

/**
 * El color del indicador a partir de `store.vault` (@dotrino/store):
 *   verde    → todo está en la bóveda y no queda nada por subir
 *   amarillo → algo queda solo en este aparato: pendiente, sin bóveda enlazada, o con error
 * @param {import('@dotrino/store').VaultBackupStatus | null | undefined} status
 * @returns {SyncLight}
 */
export function syncLight (status) {
  if (!status) return 'checking'
  if (status.state === 'synced' && status.pending === 0 && !(status.tooLarge?.length)) return 'green'
  return 'yellow'
}

/**
 * Qué hacer con la nota abierta cuando llega una versión de otro aparato.
 *   'keep'    → hay cambios sin guardar aquí: se conservan (se guardarán y ganarán por ts)
 *   'replace' → se enseña la que llegó
 *   'close'   → la borraron en otro aparato y aquí no había nada sin guardar
 * @param {{ dirty: boolean }} open
 * @param {Note | undefined} incoming
 * @returns {'keep' | 'replace' | 'close'}
 */
export function mergeOpen (open, incoming) {
  if (open.dirty) return 'keep'
  return incoming ? 'replace' : 'close'
}
