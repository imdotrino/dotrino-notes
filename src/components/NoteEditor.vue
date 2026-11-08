<script setup>
// El editor: solo escribe. Crear, fijar y borrar se hace en el panel (Sidebar.vue).
import { computed, ref, onMounted, nextTick } from 'vue'
import { t, shortWhen } from '../i18n.js'
import { state, editBody, newNote, toggleSidebar } from '../state.js'
import { MAX_BODY } from '../lib/notes.js'

/** @type {import('vue').Ref<HTMLTextAreaElement | null>} */
const area = ref(null)

const ed = computed(() => state.editor)
const saved = computed(() => state.notes.find((n) => n.id === ed.value?.id))
const tooLong = computed(() => (ed.value?.body.length || 0) > MAX_BODY)
// El botón del panel se ve cuando el panel no está: plegado en pantalla ancha, o siempre en el móvil.
const panelHidden = computed(() => state.collapsed)

onMounted(async () => {
  await nextTick()
  // Una nota nueva se empieza a escribir sin tocar nada más.
  if (ed.value && !ed.value.id) area.value?.focus()
})

/** @param {Event} e */
function onInput (e) {
  editBody(/** @type {HTMLTextAreaElement} */ (e.target).value)
}
</script>

<template>
  <section class="editor" data-testid="editor">
    <div class="editor-bar">
      <button
        type="button" class="icon-btn plain open-panel" :class="{ show: panelHidden }"
        :aria-label="t('list.expand')" :title="t('list.expand')" data-testid="sidebar-expand" @click="toggleSidebar"
      >☰</button>
      <span class="status muted small" data-testid="editor-status">
        <template v-if="ed?.dirty">{{ t('editor.saving') }}</template>
        <template v-else-if="saved">{{ t('editor.edited', { when: shortWhen(saved.ts) }) }}</template>
      </span>
      <span v-if="ed?.pinned" class="small muted">📌 {{ t('list.pinned') }}</span>
    </div>

    <template v-if="ed">
      <textarea
        ref="area" class="body" :value="ed.body" :placeholder="t('editor.placeholder')"
        :aria-label="t('editor.placeholder')" spellcheck="true" data-testid="editor-body" @input="onInput"
      ></textarea>
      <p v-if="tooLong" class="banner warn" role="alert">{{ t('editor.tooLong', { count: ed.body.length, max: MAX_BODY }) }}</p>
    </template>

    <div v-else class="editor-empty" data-testid="editor-empty">
      <p class="muted">{{ state.notes.length ? t('editor.pick') : t('list.empty') }}</p>
      <button type="button" class="btn primary" data-testid="empty-new-note" @click="newNote">+ {{ t('list.new') }}</button>
    </div>
  </section>
</template>
