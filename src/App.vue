<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import '@dotrino/topbar'
import { useBackLayer } from '@dotrino/nav/vue'
import { lang, setLang, t, errorText } from './i18n.js'
import { state, boot, flush, closeNote } from './state.js'
import { getIdentity } from './services/identity.js'
import { getReputation } from './services/reputation.js'
import { getStore } from './services/store.js'
import SyncBar from './components/SyncBar.vue'
import NoteList from './components/NoteList.vue'
import NoteEditor from './components/NoteEditor.vue'

/** @type {import('vue').Ref<import('@dotrino/topbar').DotrinoTopbar | null>} */
const topbar = ref(null)

// En el móvil el editor tapa la lista: «volver» (botón, gesto o atrás del navegador) lo cierra.
const editorOpen = computed({
  get: () => state.editor !== null,
  set: (v) => { if (!v) closeNote().catch((e) => console.error('[notes] close:', e)) },
})
useBackLayer(editorOpen)

/** @param {CustomEvent<{ lang: string }>} e */
function onLang (e) {
  setLang(e.detail.lang)
}

/** El topbar ya montado; sin él no hay dónde poner la identidad, y se dice. */
function mountedTopbar () {
  if (!topbar.value) throw new Error('topbar is not mounted')
  return topbar.value
}

// Lo escrito y aún no guardado se guarda al irse de la pestaña.
function onHide () {
  if (document.visibilityState === 'hidden') flushNow()
}
function flushNow () {
  flush().catch((e) => console.error('[notes] flush on hide:', e))
}

onMounted(() => {
  boot()
  document.addEventListener('visibilitychange', onHide)
  addEventListener('pagehide', flushNow)
  // Identidad del topbar (§6.1), después del primer pintado.
  getIdentity().then(async (id) => {
    const tb = mountedTopbar()
    tb.identity = id
    // Si la cuenta de este navegador se borra, lo que hay en pantalla ya no existe: se recarga.
    id.onVault?.((e) => { if (e?.phase === 'account-removed') location.reload() })
    tb.reputation = await getReputation()
  }).catch((e) => console.error('[notes] topbar identity:', e))
  // El estado del respaldo, también en el botón de perfil.
  getStore().then((store) => { mountedTopbar().store = store })
    .catch((e) => console.error('[notes] topbar store:', e))
})

onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', onHide)
  removeEventListener('pagehide', flushNow)
})
</script>

<template>
  <dotrino-topbar
    ref="topbar"
    brand="Notes"
    icon="./icon.svg"
    support-repo="imdotrino/dotrino-notes"
    support-discord="https://discord.gg/D648uq7cth"
    profile theme
    @dotrino-lang="onLang"
  >
    <dotrino-install slot="end" class="cc-install sm" :lang="lang"></dotrino-install>
  </dotrino-topbar>

  <main class="main">
    <p v-if="state.booting" class="muted center">{{ t('boot.loading') }}</p>

    <div v-else-if="state.bootError" class="card error-card" role="alert">
      <p>{{ errorText(state.bootError) }}</p>
      <button class="btn" data-testid="boot-retry" @click="boot">{{ t('boot.retry') }}</button>
    </div>

    <div v-else class="layout" :class="{ editing: state.editor }">
      <div class="side">
        <SyncBar />
        <NoteList />
      </div>
      <NoteEditor v-if="state.editor" :key="state.editor.key" />
    </div>
  </main>

  <div v-if="state.toast" class="toast" :class="state.toast.kind" role="status" data-testid="toast">{{ state.toast.message }}</div>
</template>
