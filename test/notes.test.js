import { test } from 'node:test'
import assert from 'node:assert/strict'
import { titleOf, previewOf, sortNotes, filterNotes, validateNote, syncLight, mergeOpen, MAX_BODY } from '../src/lib/notes.js'

/** @param {Partial<import('../src/types').Note>} n */
const note = (n) => ({ id: 'x', body: '', pinned: false, created: 0, ts: 0, ...n })

test('titleOf: first non-empty line, trimmed and cut', () => {
  assert.equal(titleOf('\n\n  Compras  \nleche'), 'Compras')
  assert.equal(titleOf(''), '')
  assert.equal(titleOf('a'.repeat(100), 10), 'aaaaaaaaa…')
})

test('previewOf: the rest in one line', () => {
  assert.equal(previewOf('Compras\nleche\n\npan'), 'leche pan')
  assert.equal(previewOf('solo título'), '')
})

test('sortNotes: pinned first, then newest', () => {
  const out = sortNotes([note({ id: 'a', ts: 3 }), note({ id: 'b', ts: 1, pinned: true }), note({ id: 'c', ts: 5 })])
  assert.deepEqual(out.map((n) => n.id), ['b', 'c', 'a'])
})

test('filterNotes: every word, ignoring case and accents', () => {
  const notes = [note({ id: 'a', body: 'Reunión con Ana' }), note({ id: 'b', body: 'reunion de equipo' }), note({ id: 'c', body: 'pan' })]
  assert.deepEqual(filterNotes(notes, 'REUNION').map((n) => n.id), ['a', 'b'])
  assert.deepEqual(filterNotes(notes, 'reunión ana').map((n) => n.id), ['a'])
  assert.equal(filterNotes(notes, '  ').length, 3)
})

test('validateNote', () => {
  assert.equal(validateNote({ body: 'ok' }), null)
  assert.equal(validateNote({ body: 3 }), 'bad-note')
  assert.equal(validateNote({ body: 'a'.repeat(MAX_BODY + 1) }), 'note-too-long')
})

test('syncLight: green only when everything is in the vault', () => {
  assert.equal(syncLight(null), 'checking')
  const base = { state: 'synced', reason: null, error: null, lastSyncAt: 1, pending: 0, tooLarge: [] }
  assert.equal(syncLight(/** @type {any} */ (base)), 'green')
  assert.equal(syncLight(/** @type {any} */ ({ ...base, pending: 2 })), 'yellow')
  assert.equal(syncLight(/** @type {any} */ ({ ...base, tooLarge: ['notes.items'] })), 'yellow')
  assert.equal(syncLight(/** @type {any} */ ({ ...base, state: 'off', reason: 'not-paired' })), 'yellow')
  assert.equal(syncLight(/** @type {any} */ ({ ...base, state: 'error' })), 'yellow')
  assert.equal(syncLight(/** @type {any} */ ({ ...base, state: 'syncing' })), 'yellow')
})

test('mergeOpen: local unsaved edits win', () => {
  assert.equal(mergeOpen({ dirty: true }, undefined), 'keep')
  assert.equal(mergeOpen({ dirty: false }, note({})), 'replace')
  assert.equal(mergeOpen({ dirty: false }, undefined), 'close')
})
