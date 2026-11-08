<script setup>
// El estado del respaldo en la bóveda: verde si todo está allí, amarillo si algo se queda solo en
// este aparato, y el botón «Sincronizar» para hacerlo ya en vez de esperar.
import { computed, ref } from 'vue'
import { t, hasText, errorText, lang, formatDate } from '../i18n.js'
import { state, syncNow, toast } from '../state.js'
import { syncLight } from '../lib/notes.js'

const open = ref(false)
const status = computed(() => state.backup)
const light = computed(() => syncLight(status.value))

const headline = computed(() => {
  if (state.syncing) return t('sync.busy')
  return t(`sync.${light.value}`)
})

const reason = computed(() => {
  const s = status.value
  if (!s) return ''
  if (s.state === 'off') return s.reason && hasText(`sync.off.${s.reason}`) ? t(`sync.off.${s.reason}`) : String(s.reason)
  if (s.state === 'error') {
    const code = s.error?.code
    return code && hasText(`sync.errors.${code}`) ? t(`sync.errors.${code}`) : t('sync.errors.other', { message: s.error?.message })
  }
  return ''
})

const lastSync = computed(() => {
  const at = status.value?.lastSyncAt
  return at ? formatDate(at, { dateStyle: 'medium', timeStyle: 'short' }) : ''
})

// Sin bóveda enlazada no hay con quién sincronizar: el botón se ve, pero apagado.
const cannotSync = computed(() => !status.value || status.value.state === 'off' || state.syncing)

async function onSync () {
  try {
    await syncNow()
    toast(t('sync.done'))
  } catch (e) {
    console.error('[notes] sync:', e)
    const code = /** @type {{ code?: unknown } | null} */ (e)?.code
    toast(typeof code === 'string' && hasText(`sync.errors.${code}`) ? t(`sync.errors.${code}`) : errorText(e), 'error')
  }
}
</script>

<template>
  <section class="sync" :data-light="light" data-testid="sync">
    <div class="sync-row">
      <button
        type="button" class="sync-state" :aria-expanded="open" data-testid="sync-state"
        @click="open = !open"
      >
        <span class="dot" :class="light" aria-hidden="true"></span>
        <span>{{ headline }}</span>
      </button>
      <button type="button" class="btn small" :disabled="cannotSync" data-testid="sync-now" @click="onSync">
        {{ state.syncing ? t('sync.busy') : t('sync.button') }}
      </button>
    </div>
    <div v-if="open || reason" class="sync-detail">
      <p v-if="reason" class="small" data-testid="sync-reason">{{ reason }}</p>
      <template v-if="open">
        <p v-if="status?.pending" class="small" data-testid="sync-pending">{{ t('sync.pending', { count: status.pending }) }}</p>
        <p v-if="status?.tooLarge?.length" class="small warn-text">{{ t('sync.tooLarge', { count: status.tooLarge.length }) }}</p>
        <p v-if="lastSync" class="small muted">{{ t('sync.lastSync', { when: lastSync }) }}</p>
      </template>
      <p v-if="status?.state === 'off' && status.reason === 'not-paired'" class="small">
        <a :href="`https://wiki.dotrino.com/${lang === 'es' ? '' : 'en/'}vault/emparejar/`" target="_blank" rel="noopener">{{ t('sync.howToLink') }}</a>
      </p>
    </div>
  </section>
</template>
