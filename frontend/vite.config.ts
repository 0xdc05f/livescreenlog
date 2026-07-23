import { defineConfig } from 'vite'
import { svelte } from '@sveltejs/vite-plugin-svelte'

// https://vite.dev/config/
export default defineConfig({
  plugins: [svelte()],
  build: {
    outDir: '../src/main/resources/static',
    // Keep sample-*.html and livescreenlog.js that live alongside the SPA
    emptyOutDir: false,
  }
})
