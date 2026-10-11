// Bundles the plugin (and the shared Kotlin core it calls) into one main.js, as Obsidian loads plugins.
import { build } from 'esbuild'

await build({
  entryPoints: ['src/main.ts'],
  bundle: true,
  outfile: 'main.js',
  format: 'cjs',
  platform: 'browser',
  target: 'es2020',
  external: ['obsidian', 'electron', '@codemirror/*', '@lezer/*'],
  logLevel: 'info',
  sourcemap: false,
  minify: false,
})
