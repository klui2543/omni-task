// Copies the Kotlin/JS build of the shared module into src/kotlin, where the plugin imports it (as web/ does), and into
// web/src/kotlin too: the Omni Task tab is the web app's code, which the type check reads from there. The bundle
// carries one copy (scripts/bundle.mjs).
import { cpSync, mkdirSync, rmSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..')
const from = join(root, 'shared/build/compileSync/js/main/productionLibrary/kotlin')
for (const to of [join(root, 'obsidian/src/kotlin'), join(root, 'web/src/kotlin')]) {
  rmSync(to, { recursive: true, force: true })
  mkdirSync(to, { recursive: true })
  cpSync(from, to, { recursive: true })
}
console.log('shared core copied to obsidian/src/kotlin and web/src/kotlin')
