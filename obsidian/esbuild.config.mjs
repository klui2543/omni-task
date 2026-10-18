// Bundles the plugin into one main.js, as Obsidian loads plugins: Ygdrasil, the shared Kotlin core it calls, and the
// whole Omni web app (../web/src) for the Omni Task tab, with the app's styles scoped to that tab (scripts/scopeCss.mjs).
import { build } from 'esbuild'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { appCss, oneCore } from './scripts/bundle.mjs'

const here = dirname(fileURLToPath(import.meta.url))

await build({
  entryPoints: ['src/main.ts'],
  bundle: true,
  outfile: 'main.js',
  format: 'cjs',
  platform: 'browser',
  target: 'es2020',
  // The web app's JSX, as its own build reads it (web/tsconfig.json).
  jsx: 'automatic',
  jsxImportSource: 'preact',
  // The web app's own packages, when only the plugin's are installed.
  nodePaths: [join(here, 'node_modules'), join(here, '..', 'web', 'node_modules')],
  loader: { '.wasm': 'binary' },
  // What Vite fills in for the web page: the tab needs no Google client.
  define: { 'import.meta.env.VITE_GOOGLE_CLIENT_ID': 'undefined', 'import.meta.env.PROD': 'true' },
  // The web page's WebP encoder fetches its .wasm from beside it; the tab carries its own (src/webp.ts).
  external: ['obsidian', 'electron', '@codemirror/*', '@lezer/*', '@jsquash/webp/encode.js'],
  plugins: [oneCore, appCss],
  logLevel: 'info',
  sourcemap: false,
  minify: false,
})
