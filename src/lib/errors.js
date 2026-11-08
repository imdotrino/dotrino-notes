/**
 * Un error con `code`: quien lo recibe decide por el código, no por la frase.
 * `extra` añade campos (p. ej. `problems`); `extra.cause` va como causa del Error.
 * @param {string} code
 * @param {string} message
 * @param {{ cause?: unknown, [k: string]: unknown }} [extra]
 * @returns {import('../types').CodedError}
 */
export function codeError (code, message, extra) {
  const { cause, ...rest } = extra || {}
  const e = new Error(message, cause !== undefined ? { cause } : undefined)
  return Object.assign(e, rest, { code })
}
