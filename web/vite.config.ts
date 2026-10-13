import { defineConfig } from 'vite'
import preact from '@preact/preset-vite'

// Served from a sub-path on GitHub Pages (/omni-task/), so assets are relative.
export default defineConfig({
  base: './',
  plugins: [preact()],
  build: { target: 'es2022' },
  // The WebP encoder for Safari finds its .wasm beside its own script (new URL(..., import.meta.url)), which the dev
  // server's pre-bundling would break; the build emits the .wasm as an asset either way.
  optimizeDeps: { exclude: ['@jsquash/webp'] },
})
