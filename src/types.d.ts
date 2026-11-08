// Las formas de datos de la app. Solo tipos: el código es JS con JSDoc.

export type Lang = 'es' | 'en'

/** Una nota. El título no se guarda: es la primera línea del texto. */
export interface Note {
  id: string
  body: string
  pinned: boolean
  /** cuándo se creó (ms) */
  created: number
  /** última escritura (ms); el almacén decide con él qué versión gana entre aparatos */
  ts: number
}

/** Lo que se le pasa a `saveNote`: sin id es una nota nueva. */
export interface NoteDraft {
  id?: string
  body: string
  pinned?: boolean
  created?: number
}

/** Un error con `code`: se decide por el código, no por la frase. */
export interface CodedError extends Error {
  code: string
  [extra: string]: unknown
}

export interface Toast {
  message: string
  kind: 'ok' | 'error'
}

/** El color del indicador de sincronización. */
export type SyncLight = 'green' | 'yellow' | 'checking'
