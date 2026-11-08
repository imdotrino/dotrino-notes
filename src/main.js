import { createApp } from 'vue'
import App from './App.vue'
import '@dotrino/theme/theme.css'
import './style.css'
// Botón «Instalar» PWA unificado (§3). La barra superior se importa en App.vue.
import '@dotrino/install'
import { createBackNav } from '@dotrino/nav'
import { declare } from '@dotrino/compat'
import { registerSW } from 'virtual:pwa-register'
import { setLang, lang } from './i18n.js'

/** Qué versión soy (§14). Notes no habla con nadie por su cuenta: la sincronización es del almacén. */
export const MINE = declare({ product: 'notes', version: __APP_VERSION__, protocol: 1, speaks: [1] })

// SW con auto-actualización real: recarga al tomar control el SW nuevo y re-chequea
// cada 30 min (si no, la PWA instalada se queda en la versión vieja).
registerSW({
  immediate: true,
  onRegisteredSW (_url, reg) {
    if (!reg) return
    setInterval(() => { reg.update().catch((e) => console.error('[notes] sw update:', e)) }, 30 * 60_000)
  },
})

setLang(lang.value)
createBackNav()

createApp(App).mount('#app')
