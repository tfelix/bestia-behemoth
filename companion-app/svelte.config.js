import adapter from '@sveltejs/adapter-static';
import { vitePreprocess } from '@sveltejs/vite-plugin-svelte';

/**
 * A single-page app: every route renders in the browser, and the static host answers every path with
 * index.html. The service worker is SvelteKit's own (src/service-worker.ts).
 */
export default {
  preprocess: vitePreprocess(),
  kit: {
    adapter: adapter({ fallback: 'index.html' })
  }
};
