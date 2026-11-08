// Textos es/en. Español neutro, con tuteo. El idioma lo decide el toggle de
// <dotrino-topbar> (clave común 'dotrino.lang'); aquí solo se sigue su evento.
import { ref } from 'vue'

const messages = {
  es: {
    boot: { loading: 'Abriendo tus notas…', retry: 'Reintentar' },
    list: {
      title: 'Tus notas',
      collapse: 'Plegar el panel',
      expand: 'Ver tus notas',
      new: 'Nueva nota',
      search: 'Buscar en tus notas',
      empty: 'Todavía no tienes notas. Crea la primera con «Nueva nota».',
      noResults: 'Ninguna nota tiene esas palabras.',
      untitled: 'Nota sin título',
      pinned: 'Fijada',
    },
    editor: {
      placeholder: 'Escribe aquí. La primera línea es el título.',
      pick: 'Elige una nota del panel o crea una nueva.',
      pin: 'Fijar arriba',
      unpin: 'Quitar de arriba',
      remove: 'Eliminar',
      removeTitle: '¿Eliminar esta nota?',
      removeBody: 'Se borra en todos tus aparatos. No se puede deshacer.',
      removeConfirm: 'Sí, eliminar',
      cancel: 'Cancelar',
      removed: 'Nota eliminada.',
      saving: 'Guardando…',
      saved: 'Guardada',
      edited: 'Editada {when}',
      tooLong: 'La nota es demasiado larga ({count} de {max} caracteres). Pártela en varias.',
    },
    sync: {
      button: 'Sincronizar',
      busy: 'Sincronizando…',
      done: 'Sincronizado.',
      green: 'Tus notas están en tu bóveda y llegan a tus otros aparatos.',
      yellow: 'Hay notas que solo están en este aparato.',
      checking: 'Comprobando tu bóveda…',
      pending: '{count} cambios por subir.',
      lastSync: 'Última sincronización: {when}',
      tooLarge: '{count} notas no caben en el respaldo. Pártelas en varias.',
      howToLink: 'Cómo enlazar este aparato a tu bóveda',
      off: {
        'no-identity': 'Este aparato no tiene un perfil abierto.',
        'not-paired': 'Este perfil no está enlazado a una bóveda: tus notas se quedan en este aparato.',
        revoked: 'Este aparato ya no es parte de tu cuenta.',
      },
      errors: {
        'no-content-key': 'Este aparato todavía no tiene la llave de tu cuenta para cifrar tus notas. La recibe cuando otro aparato de tu cuenta se conecta.',
        'vault-no-reply': 'Tu bóveda no responde. Comprueba que está encendida.',
        'vault-outdated': 'Tu bóveda necesita actualizarse para guardar tus notas.',
        'not-paired': 'Este perfil no está enlazado a una bóveda.',
        'vault-off': 'Este perfil no está enlazado a una bóveda.',
        other: 'No se pudo sincronizar con tu bóveda: {message}',
      },
    },
    errors: {
      'identity-unreachable': 'No se pudo abrir tu identidad. Revisa la conexión e inténtalo de nuevo.',
      'store-unreachable': 'No se pudo abrir tu almacén. Revisa la conexión e inténtalo de nuevo.',
      'store-no-profile': 'No hay un perfil activo. Crea uno o elige uno desde el botón de perfil.',
      'store-identity-mismatch': 'El almacén quedó abierto con otro perfil. Recarga la página.',
      'store-profile-mismatch': 'El almacén no corresponde al perfil activo. Recarga la página.',
      'bad-note': 'La nota no tiene un formato válido.',
      'note-too-long': 'La nota es demasiado larga. Pártela en varias.',
      unknown: 'Algo salió mal: {message}',
    },
  },
  en: {
    boot: { loading: 'Opening your notes…', retry: 'Try again' },
    list: {
      title: 'Your notes',
      collapse: 'Collapse the panel',
      expand: 'Show your notes',
      new: 'New note',
      search: 'Search your notes',
      empty: 'You have no notes yet. Create the first one with “New note”.',
      noResults: 'No note has those words.',
      untitled: 'Untitled note',
      pinned: 'Pinned',
    },
    editor: {
      placeholder: 'Write here. The first line is the title.',
      pick: 'Pick a note from the panel or create a new one.',
      pin: 'Pin to top',
      unpin: 'Unpin',
      remove: 'Delete',
      removeTitle: 'Delete this note?',
      removeBody: 'It is deleted on all your devices. This cannot be undone.',
      removeConfirm: 'Yes, delete',
      cancel: 'Cancel',
      removed: 'Note deleted.',
      saving: 'Saving…',
      saved: 'Saved',
      edited: 'Edited {when}',
      tooLong: 'The note is too long ({count} of {max} characters). Split it into several.',
    },
    sync: {
      button: 'Sync',
      busy: 'Syncing…',
      done: 'Synced.',
      green: 'Your notes are in your vault and reach your other devices.',
      yellow: 'Some notes are only on this device.',
      checking: 'Checking your vault…',
      pending: '{count} changes waiting to upload.',
      lastSync: 'Last sync: {when}',
      tooLarge: '{count} notes do not fit in the backup. Split them into several.',
      howToLink: 'How to link this device to your vault',
      off: {
        'no-identity': 'This device has no open profile.',
        'not-paired': 'This profile is not linked to a vault: your notes stay on this device.',
        revoked: 'This device is no longer part of your account.',
      },
      errors: {
        'no-content-key': 'This device does not have your account key yet to encrypt your notes. It gets it when another device of your account connects.',
        'vault-no-reply': 'Your vault is not answering. Check that it is on.',
        'vault-outdated': 'Your vault needs an update to store your notes.',
        'not-paired': 'This profile is not linked to a vault.',
        'vault-off': 'This profile is not linked to a vault.',
        other: 'Could not sync with your vault: {message}',
      },
    },
    errors: {
      'identity-unreachable': 'Your identity could not be opened. Check the connection and try again.',
      'store-unreachable': 'Your storage could not be opened. Check the connection and try again.',
      'store-no-profile': 'There is no active profile. Create or choose one from the profile button.',
      'store-identity-mismatch': 'The storage was opened with another profile. Reload the page.',
      'store-profile-mismatch': 'The storage does not match the active profile. Reload the page.',
      'bad-note': 'The note does not have a valid format.',
      'note-too-long': 'The note is too long. Split it into several.',
      unknown: 'Something went wrong: {message}',
    },
  },
}

/** @typedef {import('./types').Lang} Lang */

/** @type {import('vue').Ref<Lang>} */
export const lang = ref(initialLang())

/** @returns {Lang} */
function initialLang () {
  try {
    const saved = localStorage.getItem('dotrino.lang')
    if (saved === 'es' || saved === 'en') return saved
  } catch { /* modo privado */ }
  return (navigator.language || 'es').toLowerCase().startsWith('es') ? 'es' : 'en'
}

/**
 * Una fecha en el idioma activo (es-EC / en-US). `opts` son los de Intl.DateTimeFormat.
 * @param {Date | string | number} value
 * @param {Intl.DateTimeFormatOptions} [opts]
 * @returns {string}
 */
export function formatDate (value, opts) {
  return new Intl.DateTimeFormat(lang.value === 'es' ? 'es-EC' : 'en-US', opts).format(value instanceof Date ? value : new Date(value))
}

/**
 * Fecha corta para la lista: la hora si es de hoy, el día si no.
 * @param {number} ts
 * @returns {string}
 */
export function shortWhen (ts) {
  const d = new Date(ts)
  const today = new Date()
  const sameDay = d.toDateString() === today.toDateString()
  return sameDay ? formatDate(d, { timeStyle: 'short' }) : formatDate(d, { dateStyle: 'medium' })
}

/** @param {unknown} l */
export function setLang (l) {
  if (l !== 'es' && l !== 'en') throw new Error(`unsupported language: ${l}`)
  lang.value = l
  document.documentElement.lang = l
}

/**
 * El texto de una clave ('list.new'), con `{var}` sustituidas.
 * @param {string} path
 * @param {Record<string, unknown>} [vars]
 * @returns {string}
 */
export function t (path, vars) {
  const value = lookup(path)
  if (typeof value !== 'string') throw new Error(`missing translation: ${lang.value}.${path}`)
  return vars ? value.replace(/\{(\w+)\}/g, (_, /** @type {string} */ k) => (k in vars ? String(vars[k]) : `{${k}}`)) : value
}

/**
 * ¿Hay texto para esta clave? Para códigos que vienen de fuera y pueden ser nuevos.
 * @param {string} path
 * @returns {boolean}
 */
export function hasText (path) {
  return typeof lookup(path) === 'string'
}

/**
 * @param {string} path
 * @returns {unknown}
 */
function lookup (path) {
  return path.split('.').reduce((/** @type {unknown} */ o, k) => (o == null ? o : /** @type {Record<string, unknown>} */ (o)[k]), messages[lang.value])
}

/**
 * Mensaje para un error con `code`; los desconocidos dicen lo que pasó, tal cual.
 * @param {unknown} e
 * @returns {string}
 */
export function errorText (e) {
  const err = /** @type {{ code?: unknown, message?: unknown } | null | undefined} */ (e)
  const code = err?.code
  /** @type {Record<string, string>} */
  const errors = messages[lang.value].errors
  const known = typeof code === 'string' && code ? errors[code] : undefined
  return known || t('errors.unknown', { message: err?.message || String(e) })
}
