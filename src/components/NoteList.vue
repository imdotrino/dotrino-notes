<script setup>
import { computed, ref } from 'vue'
import { t, shortWhen } from '../i18n.js'
import { state, newNote, openNote } from '../state.js'
import { sortNotes, filterNotes, titleOf, previewOf } from '../lib/notes.js'

const query = ref('')
const visible = computed(() => sortNotes(filterNotes(state.notes, query.value)))

/** @param {import('../types').Note} n */
function onOpen (n) {
  openNote(n).catch((e) => console.error('[notes] open:', e))
}
</script>

<template>
  <section class="list">
    <div class="list-head">
      <input
        v-model="query" type="search" class="search" :placeholder="t('list.search')"
        :aria-label="t('list.search')" data-testid="search"
      />
      <button type="button" class="btn primary" data-testid="new-note" @click="newNote">+ {{ t('list.new') }}</button>
    </div>

    <p v-if="!state.notes.length" class="muted center" data-testid="list-empty">{{ t('list.empty') }}</p>
    <p v-else-if="!visible.length" class="muted center">{{ t('list.noResults') }}</p>

    <ul v-else class="items">
      <li v-for="n in visible" :key="n.id">
        <button
          type="button" class="item" :class="{ active: state.editor?.id === n.id }"
          :data-note-id="n.id" data-testid="note-item" @click="onOpen(n)"
        >
          <span class="item-title">
            <span v-if="n.pinned" class="pin" :title="t('list.pinned')" :aria-label="t('list.pinned')">📌</span>
            {{ titleOf(n.body) || t('list.untitled') }}
          </span>
          <span v-if="previewOf(n.body)" class="item-preview">{{ previewOf(n.body) }}</span>
          <span class="item-when">{{ shortWhen(n.ts) }}</span>
        </button>
      </li>
    </ul>
  </section>
</template>
