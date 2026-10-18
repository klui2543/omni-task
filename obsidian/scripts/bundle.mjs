// What the plugin's build and the preview share: one copy of the shared core, and the app's styles scoped to its tab.
import { readFileSync } from 'node:fs'
import { readFile } from 'node:fs/promises'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { scopeCss } from './scopeCss.mjs'

const here = join(dirname(fileURLToPath(import.meta.url)), '..')
const web = join(here, '..', 'web', 'src')

/** One copy of the shared core for the plugin and the app, though each imports its own (src/kotlin, web/src/kotlin). */
export const oneCore = {
  name: 'one-core',
  setup(b) {
    b.onResolve({ filter: /kotlin\/OmniTask-shared\.mjs$/ }, () => ({ path: join(here, 'src', 'kotlin', 'OmniTask-shared.mjs') }))
  },
}

/** Each `import './x.css'` of the app hands its scoped text to embedCss.ts, which the tab puts in its shadow root. */
export const appCss = {
  name: 'app-css',
  setup(b) {
    b.onLoad({ filter: /\.css$/ }, async (args) => ({
      contents: `import { addCss } from ${JSON.stringify(join(web, 'embedCss.ts'))}\naddCss(${JSON.stringify(scopeCss(await readFile(args.path, 'utf8')))})\n`,
      loader: 'js',
      resolveDir: dirname(args.path),
    }))
  },
}


/** The web app's OAuth client ID (web/.env.production), for Google Calendar in the Omni Task tab. */
export function googleClientId() {
  const env = readFileSync(join(here, '..', 'web', '.env.production'), 'utf8')
  return /^VITE_GOOGLE_CLIENT_ID=(.+)$/m.exec(env)?.[1].trim() ?? ''
}
