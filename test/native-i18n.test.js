// Los textos de las versiones nativas salen de src/i18n.js: el i18n.json de Android y el de iOS
// tienen que estar al día con él (se regeneran con `npm run native:i18n`).
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { build, TARGETS } from '../scripts/native-i18n.mjs'

test('el i18n.json de las apps nativas está al día con src/i18n.js', () => {
  const want = build()
  for (const t of TARGETS) {
    assert.equal(readFileSync(new URL(t, new URL('../scripts/', import.meta.url)), 'utf8'), want, `${t} está desactualizado: npm run native:i18n`)
  }
})
