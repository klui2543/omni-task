import { defineConfig } from 'vite'
import preact from '@preact/preset-vite'

// Served from a sub-path on GitHub Pages (/omni-task/), so assets are relative.
export default defineConfig({
  base: './',
  plugins: [preact()],
  build: { target: 'es2022' },
})
