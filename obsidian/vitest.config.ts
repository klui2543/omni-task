import { defineConfig } from 'vitest/config'
import { fileURLToPath } from 'node:url'

// The web app's files read the shared core from web/src/kotlin; the tests give them the plugin's copy, as the build does.
export default defineConfig({
  resolve: {
    alias: [{ find: /^\.\/kotlin\/OmniTask-shared\.mjs$/, replacement: fileURLToPath(new URL('./src/kotlin/OmniTask-shared.mjs', import.meta.url)) }],
  },
})
