<script setup>
// El panel de las notas: aquí se administran (crear, buscar, abrir, fijar, borrar) y se ve si
// están en la bóveda. El editor solo escribe.
import { computed, ref } from 'vue'
import { t, shortWhen, errorText } from '../i18n.js'
import { state, newNote, openNote, togglePin, deleteNote, toggleSidebar, toast } from '../state.js'
import { sortNotes, filterNotes, titleOf, previewOf } from '../lib/notes.js'
import SyncBar from './SyncBar.vue'

const query = ref('')
const visible = computed(() => sortNotes(filterNotes(state.notes, query.value)))
/** La nota que espera confirmación para borrarse. */
const confirming = ref(/** @type {import('../types').Note | null} */ (null))

/** @param {import('../types').Note} n */
function onOpen (n) {
  openNote(n).catch(report)
}

/** @param {import('../types').Note} n */
function onPin (n) {
  togglePin(n.id).catch(report)
}

async function onRemove () {
  const n = confirming.value
  confirming.value = null
  if (!n) return
  try {
    await deleteNote(n.id)
    toast(t('editor.removed'))
  } catch (e) {
    report(e)
  }
}

/** @param {unknown} e */
function report (e) {
  console.error('[notes] sidebar:', e)
  toast(errorText(e), 'error')
}
</script>

<template>
  <aside class="sidebar" :aria-label="t('list.title')" data-testid="sidebar">
    <div class="sidebar-head">
      <h2>{{ t('list.title') }}</h2>
      <button
        type="button" class="icon-btn plain" :aria-label="t('list.collapse')" :title="t('list.collapse')"
        data-testid="sidebar-collapse" @click="toggleSidebar"
      >«</button>
    </div>

    <SyncBar />

    <button type="button" class="btn primary block" data-testid="new-note" @click="newNote">+ {{ t('list.new') }}</button>
    <input
      v-model="query" type="search" class="search" :placeholder="t('list.search')"
      :aria-label="t('list.search')" data-testid="search"
    />

    <p v-if="!state.notes.length" class="muted small center" data-testid="list-empty">{{ t('list.empty') }}</p>
    <p v-else-if="!visible.length" class="muted small center">{{ t('list.noResults') }}</p>

    <ul v-else class="items">
      <li v-for="n in visible" :key="n.id" class="item" :class="{ active: state.editor?.id === n.id }" :data-note-id="n.id" data-testid="note-item">
        <button type="button" class="item-open" @click="onOpen(n)">
          <span class="item-title">{{ titleOf(n.body) || t('list.untitled') }}</span>
          <span v-if="previewOf(n.body)" class="item-preview">{{ previewOf(n.body) }}</span>
          <span class="item-when">{{ shortWhen(n.ts) }}</span>
        </button>
        <div class="item-actions">
          <button
            type="button" class="icon-btn small" :class="{ on: n.pinned }" :aria-pressed="n.pinned"
            :aria-label="n.pinned ? t('editor.unpin') : t('editor.pin')" :title="n.pinned ? t('editor.unpin') : t('editor.pin')"
            data-testid="note-pin" @click="onPin(n)"
          >📌</button>
          <button
            type="button" class="icon-btn small" :aria-label="t('editor.remove')" :title="t('editor.remove')"
            data-testid="note-remove" @click="confirming = n"
          >🗑</button>
        </div>
      </li>
    </ul>

    <div v-if="confirming" class="modal-backdrop" @click.self="confirming = null">
      <div class="modal card" role="dialog" aria-modal="true" :aria-label="t('editor.removeTitle')">
        <h2>{{ t('editor.removeTitle') }}</h2>
        <p><strong>{{ titleOf(confirming.body) || t('list.untitled') }}</strong></p>
        <p>{{ t('editor.removeBody') }}</p>
        <div class="actions">
          <button type="button" class="btn" data-testid="remove-cancel" @click="confirming = null">{{ t('editor.cancel') }}</button>
          <button type="button" class="btn danger" data-testid="remove-confirm" @click="onRemove">{{ t('editor.removeConfirm') }}</button>
        </div>
      </div>
    </div>
  </aside>
</template>
