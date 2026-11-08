// Almacén del usuario (@dotrino/store, obligatorio §4), separado por PERFIL.
//
// El store es un singleton del módulo. Desde @dotrino/store 0.10.0 el almacén adopta la
// identidad aunque la moneda de support lo haya abierto antes sin ella; aun así se comprueba
// que quedó en el perfil activo antes de usarlo: mezclar las notas de dos perfiles no es un
// fallo que se pueda notar tarde.
import { Store } from '@dotrino/store'
import { getIdentity } from './identity.js'
import { codeError } from '../lib/errors.js'

/** @type {Promise<Store> | null} */
let connecting = null

/**
 * El almacén del perfil activo, conectado una vez.
 * @returns {Promise<Store>}
 */
export function getStore () {
  if (!connecting) {
    connecting = connect().catch((e) => {
      connecting = null
      throw e
    })
  }
  return connecting
}

/** @returns {Promise<Store>} */
async function connect () {
  const identity = await getIdentity()
  let store
  try {
    // Todas las notas van en un hilo (ver lib/repo.js); el tope por defecto (1000) es corto.
    // El tope va en `connect` y no después: se aplica antes de la primera sincronización con
    // la bóveda, que si no recortaría lo que baja.
    store = await Store.connect({ identity, maxPerThread: 50000 })
  } catch (e) {
    // «Sin perfil» u «otro perfil» no son «no contesta»: se arreglan distinto, y el
    // código no se pierde al envolver.
    const code = /** @type {{ code?: unknown }} */ (e)?.code
    if (code === 'store-no-profile' || code === 'store-identity-mismatch') throw e
    throw codeError('store-unreachable', `store unreachable: ${e instanceof Error ? e.message : e}`, { cause: e })
  }
  const profile = await identity.currentProfile()
  if (!profile?.id || store.profileId !== profile.id) {
    throw codeError('store-profile-mismatch', `the store is not bound to the active profile (store: ${store.profileId}, profile: ${profile?.id})`)
  }
  return store
}
