// Builds preview/index.html: the Omni Task tab in a page shaped like Obsidian's window, over example notes, to look
// at the app's layout inside a tab without Obsidian (open it in a browser, ?dark for the dark theme, ?page=#/tasks).
import { build } from 'esbuild'
import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { appCss, googleClientId, oneCore } from './bundle.mjs'

const here = join(dirname(fileURLToPath(import.meta.url)), '..')
mkdirSync(join(here, 'preview'), { recursive: true })
await build({
  entryPoints: [join(here, 'scripts', 'preview-entry.ts')],
  bundle: true,
  outfile: join(here, 'preview', 'preview.js'),
  format: 'esm',
  platform: 'browser',
  target: 'es2022',
  jsx: 'automatic',
  jsxImportSource: 'preact',
  nodePaths: [join(here, 'node_modules'), join(here, '..', 'web', 'node_modules')],
  define: { 'import.meta.env.VITE_GOOGLE_CLIENT_ID': JSON.stringify(googleClientId()), 'import.meta.env.PROD': 'true' },
  external: ['@jsquash/webp/encode.js'],
  plugins: [oneCore, appCss],
  logLevel: 'warning',
})
writeFileSync(join(here, 'preview', 'index.html'), `<!doctype html>
<html lang="th"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Omni Task in Obsidian (preview)</title>
<link href="https://fonts.googleapis.com/css2?family=Sarabun:wght@400;500;600&display=swap" rel="stylesheet">
<style>
  html, body { margin: 0; height: 100%; font-family: system-ui, sans-serif; background: #f6f6f8; color: #222; }
  body.theme-dark { background: #1e1e1e; color: #ddd; }
  .app { display: grid; grid-template-columns: 44px var(--side, 260px) 1fr; height: 100%; }
  .ribbon { background: #ececf0; border-right: 1px solid #ddd; }
  .side { background: #f3f3f5; border-right: 1px solid #ddd; padding: 12px; font-size: 13px; }
  body.theme-dark .ribbon, body.theme-dark .side { background: #262626; border-color: #333; }
  .main { display: flex; flex-direction: column; min-width: 0; }
  .tabs { height: 38px; background: #e4e4ea; border-bottom: 1px solid #ddd; display: flex; align-items: end; padding: 0 8px; font-size: 13px; }
  .tabs span { background: #fff; padding: 6px 14px; border-radius: 8px 8px 0 0; }
  body.theme-dark .tabs { background: #161616; border-color: #333; } body.theme-dark .tabs span { background: #1e1e1e; }
  .view-content { flex: 1; min-height: 0; padding: 0; overflow: hidden; }
  /* A button style like Obsidian's own, which must not reach the app. */
  button { background: hotpink !important; border-radius: 0 !important; }
</style></head>
<body><div class="app"><div class="ribbon"></div><div class="side">Vault<br>📁 Folder<br>Daily</div>
<div class="main"><div class="tabs"><span>Omni Task</span></div><div class="view-content"></div></div></div>
<script>const s = new URLSearchParams(location.search).get('side'); if (s) document.documentElement.style.setProperty('--side', s + 'px')</script>
<script type="module" src="./preview.js"></script></body></html>
`)
console.log('preview/index.html built')
