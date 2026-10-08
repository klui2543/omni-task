// Copies the Kotlin/JS build of the shared module into src/kotlin, where the app imports it.
import { cpSync, mkdirSync, rmSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..')
const from = join(root, 'shared/build/compileSync/js/main/productionLibrary/kotlin')
const to = join(root, 'web/src/kotlin')
rmSync(to, { recursive: true, force: true })
mkdirSync(to, { recursive: true })
cpSync(from, to, { recursive: true })
console.log('shared core copied to web/src/kotlin')
