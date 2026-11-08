// Estado compartido de la interfaz. Los pilares (identidad, almacén) NO viven aquí: un Proxy
// reactivo de Vue rompe el postMessage de los iframes. Aquí solo hay datos planos.
import { reactive } from 'vue'
import { getStore } from './services/store.js'
import { listNotes, saveNote, removeNote, isNotesThread } from './lib/repo.js'
import { mergeOpen, sortNotes } from './lib/notes.js'
import { errorText } from './i18n.js'

/** @typedef {import('./types').Note} Note */
/** @typedef {import('@dotrino/store').Store} Store */
/** @typedef {import('@dotrino/store').VaultBackupStatus} VaultBackupStatus */

/**
 * @typedef {object} Editor
 * @property {string} key           estable mientras la nota está abierta (el id nace al guardar)
 * @property {string | null} id      null = nota nueva todavía sin guardar
 * @property {string} body
 * @property {boolean} pinned
 * @property {number} created
 * @property {boolean} dirty          hay cambios que aún no están en el almacén
 */

/**
 * @typedef {object} AppState
 * @property {boolean} booting
 * @property {unknown} bootError
 * @property {Note[]} notes
 * @property {Editor | null} editor
 * @property {VaultBackupStatus | null} backup
 * @property {boolean} syncing          «Sincronizar» en curso
 * @property {boolean} drawer           móvil: el panel de notas abierto como cajón
 * @property {boolean} collapsed        pantalla ancha: el panel de notas plegado
 * @property {import('./types').Toast | null} toast
 */

/** @type {AppState} */
export const state = reactive({
  booting: true,
  bootError: null,
  notes: [],
  editor: null,
  backup: null,
  syncing: false,
  drawer: false,
  collapsed: readCollapsed(),
  toast: null,
})

// Plegado o no es una preferencia de pantalla de este aparato: localStorage (§4).
const COLLAPSED_KEY = 'notes.sidebar.collapsed'

/** @returns {boolean} */
function readCollapsed () {
  try { return localStorage.getItem(COLLAPSED_KEY) === '1' } catch { return false }
}

/** En el móvil el panel es un cajón (se abre encima); en pantalla ancha se pliega a un lado. */
export const NARROW = '(max-width: 799px)'

/** El botón del panel: abre/cierra el cajón en el móvil, pliega/despliega en pantalla ancha. */
export function toggleSidebar () {
  if (matchMedia(NARROW).matches) {
    state.drawer = !state.drawer
    return
  }
  state.collapsed = !state.collapsed
  try { localStorage.setItem(COLLAPSED_KEY, state.collapsed ? '1' : '0') } catch { /* modo privado */ }
}

/** Abre el almacén y carga las notas. Un fallo queda en `bootError`. */
export async function boot () {
  state.booting = true
  state.bootError = null
  try {
    const store = await getStore()
    watchBackup(store)
    state.notes = await listNotes()
    // Se abre la primera nota del panel; sin notas, el editor enseña cómo empezar.
    if (!state.editor) openFirst()
  } catch (e) {
    console.error('[notes] boot:', e)
    state.bootError = e
  } finally {
    state.booting = false
  }
}

let watching = false

/** @param {Store} store */
function watchBackup (store) {
  state.backup = store.vault
  if (watching) return
  watching = true
  store.on('vault', (/** @type {import('@dotrino/store').VaultBackupEvent} */ status) => {
    state.backup = status
    if (status.state !== 'synced' || !status.changed.some(isNotesThread)) return
    // Llegaron notas de otro aparato: se recarga la lista, y la abierta si no tiene cambios aquí.
    refreshFromVault().catch((e) => {
      console.error('[notes] refresh after vault sync:', e)
      toast(errorText(e), 'error')
    })
  })
}

async function refreshFromVault () {
  state.notes = await listNotes()
  const ed = state.editor
  if (!ed || !ed.id) return
  const incoming = state.notes.find((n) => n.id === ed.id)
  const action = mergeOpen(ed, incoming)
  if (action === 'replace' && incoming) {
    ed.body = incoming.body
    ed.pinned = incoming.pinned
  } else if (action === 'close') {
    openFirst()
  }
}

/** «Sincronizar»: sube lo pendiente y baja lo de los otros aparatos ahora. */
export async function syncNow () {
  state.syncing = true
  try {
    await flush()
    const store = await getStore()
    await store.vaultSync()
    state.backup = store.vault
    state.notes = await listNotes()
  } finally {
    state.syncing = false
  }
}

// ---------- el editor ----------

export function newNote () {
  state.editor = { key: crypto.randomUUID(), id: null, body: '', pinned: false, created: Date.now(), dirty: false }
  state.drawer = false
}

/** @param {Note} note */
export async function openNote (note) {
  await flush()
  state.editor = editorFor(note)
  state.drawer = false
}

/** @param {Note} note @returns {Editor} */
function editorFor (note) {
  return { key: note.id, id: note.id, body: note.body, pinned: note.pinned, created: note.created, dirty: false }
}

/** Abre la primera nota del panel (fijadas y recientes primero), o ninguna si no hay. */
function openFirst () {
  const first = sortNotes(state.notes)[0]
  state.editor = first ? editorFor(first) : null
}

/** @type {ReturnType<typeof setTimeout> | null} */
let timer = null
const AUTOSAVE_MS = 800

/** @param {string} body */
export function editBody (body) {
  if (!state.editor) return
  state.editor.body = body
  state.editor.dirty = true
  if (timer) clearTimeout(timer)
  timer = setTimeout(() => { flush().catch(reportSaveError) }, AUTOSAVE_MS)
}

/**
 * Fija o suelta una nota. Si es la abierta, va con el resto de sus cambios.
 * @param {string} id
 */
export async function togglePin (id) {
  const ed = state.editor
  if (ed && ed.id === id) {
    ed.pinned = !ed.pinned
    ed.dirty = true
    await flush()
    return
  }
  const note = state.notes.find((n) => n.id === id)
  if (!note) throw new Error(`togglePin: unknown note ${id}`)
  const saved = await saveNote({ ...note, pinned: !note.pinned })
  state.notes = state.notes.map((n) => (n.id === id ? saved : n))
}

/** @type {Promise<void> | null} */
let saving = null

/** Guarda el editor si tiene cambios. Dos guardados no se pisan: el segundo espera al primero. */
export async function flush () {
  if (timer) { clearTimeout(timer); timer = null }
  while (saving) await saving
  const ed = state.editor
  if (!ed || !ed.dirty) return
  // Una nota nueva que se queda vacía no existe todavía: no se guarda nada.
  if (!ed.id && !ed.body.trim()) { ed.dirty = false; return }
  saving = (async () => {
    const body = ed.body
    const note = await saveNote({ id: ed.id || undefined, body, pinned: ed.pinned, created: ed.created })
    ed.id = note.id
    // Si se siguió escribiendo mientras se guardaba, sigue sucia.
    ed.dirty = ed.body !== body
    const i = state.notes.findIndex((n) => n.id === note.id)
    if (i >= 0) state.notes[i] = { ...note, body }
    else state.notes.push({ ...note, body })
  })()
  try { await saving } finally { saving = null }
  if (state.editor?.dirty) await flush()
}

/**
 * Borra una nota (deja lápida: no vuelve desde otro aparato). Si es la abierta, se abre la siguiente.
 * Sin id, borra la nota nueva todavía sin guardar.
 * @param {string | null} id
 */
export async function deleteNote (id) {
  const ed = state.editor
  const isOpen = !!ed && ed.id === id
  if (isOpen) {
    if (timer) { clearTimeout(timer); timer = null }
    while (saving) await saving
  }
  if (id) {
    await removeNote(id)
    state.notes = state.notes.filter((n) => n.id !== id)
  }
  if (isOpen) openFirst()
}

/** @param {unknown} e */
function reportSaveError (e) {
  console.error('[notes] save:', e)
  toast(errorText(e), 'error')
}

/** @type {ReturnType<typeof setTimeout> | null} */
let toastTimer = null

/**
 * @param {string} message
 * @param {'ok' | 'error'} [kind]
 */
export function toast (message, kind = 'ok') {
  state.toast = { message, kind }
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => { state.toast = null }, kind === 'error' ? 6000 : 2500)
}
