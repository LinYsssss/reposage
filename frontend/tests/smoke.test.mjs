import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFile } from 'node:fs/promises'

test('index declares the Vue entrypoint', async () => {
  const html = await readFile(new URL('../index.html', import.meta.url), 'utf8')
  assert.match(html, /src="\/src\/main\.js"/)
})
