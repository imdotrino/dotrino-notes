<script setup>
import { computed, ref, onMounted, nextTick } from 'vue'
import { t, shortWhen, errorText } from '../i18n.js'
import { state, editBody, togglePin, closeNote, deleteOpenNote, toast } from '../state.js'
import { MAX_BODY } from '../lib/notes.js'

const confirming = ref(false)
/** @type {import('vue').Ref<HTMLTextAreaElement | null>} */
const area = ref(null)

const ed = computed(() => state.editor)
const saved = computed(() => state.notes.find((n) => n.id === ed.value?.id))
const tooLong = computed(() => (ed.value?.body.length || 0) > MAX_BODY)

onMounted(async () => {
  await nextTick()
  // Una nota nueva se empieza a escribir sin tocar nada más.
  if (!ed.value?.id) area.value?.focus()
})

/** @param {Event} e */
function onInput (e) {
  editBody(/** @type {HTMLTextAreaElement} */ (e.target).value)
}

function onBack () {
  closeNote().catch(report)
}

function onPin () {
  togglePin().catch(report)
}

async function onRemove () {
  try {
    await deleteOpenNote()
    toast(t('editor.removed'))
  } catch (e) {
    report(e)
  } finally {
    confirming.value = false
  }
}

/** @param {unknown} e */
function report (e) {
  console.error('[notes] editor:', e)
  toast(errorText(e), 'error')
}
</script>

<template>
  <section v-if="ed" class="editor" data-testid="editor">
    <div class="editor-bar">
      <button type="button" class="icon-btn back" :aria-label="t('editor.back')" :title="t('editor.back')" data-testid="editor-back" @click="onBack">←</button>
      <span class="status muted small" data-testid="editor-status">
        <template v-if="ed.dirty">{{ t('editor.saving') }}</template>
        <template v-else-if="saved">{{ t('editor.edited', { when: shortWhen(saved.ts) }) }}</template>
      </span>
      <button
        type="button" class="icon-btn" :class="{ on: ed.pinned }" :aria-pressed="ed.pinned"
        :aria-label="ed.pinned ? t('editor.unpin') : t('editor.pin')" :title="ed.pinned ? t('editor.unpin') : t('editor.pin')"
        data-testid="editor-pin" @click="onPin"
      >📌</button>
      <button
        type="button" class="icon-btn" :aria-label="t('editor.remove')" :title="t('editor.remove')"
        data-testid="editor-remove" @click="confirming = true"
      >🗑</button>
    </div>

    <textarea
      ref="area" class="body" :value="ed.body" :placeholder="t('editor.placeholder')"
      :aria-label="t('editor.placeholder')" spellcheck="true" data-testid="editor-body" @input="onInput"
    ></textarea>

    <p v-if="tooLong" class="banner warn" role="alert">{{ t('editor.tooLong', { count: ed.body.length, max: MAX_BODY }) }}</p>

    <div v-if="confirming" class="modal-backdrop" @click.self="confirming = false">
      <div class="modal card" role="dialog" aria-modal="true" :aria-label="t('editor.removeTitle')">
        <h2>{{ t('editor.removeTitle') }}</h2>
        <p>{{ t('editor.removeBody') }}</p>
        <div class="actions">
          <button type="button" class="btn" data-testid="remove-cancel" @click="confirming = false">{{ t('editor.cancel') }}</button>
          <button type="button" class="btn danger" data-testid="remove-confirm" @click="onRemove">{{ t('editor.removeConfirm') }}</button>
        </div>
      </div>
    </div>
  </section>
</template>
