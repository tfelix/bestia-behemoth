/// <reference types="vitest/config" />
import adapter from '@sveltejs/adapter-static';
import { sveltekit } from '@sveltejs/kit/vite';
import { vitePreprocess } from '@sveltejs/vite-plugin-svelte';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { defineConfig, type Plugin } from 'vite';
import { parseCsv } from './src/lib/csv.ts';

const LOCALIZATION = fileURLToPath(new URL('../bestia-client/src/Localization/', import.meta.url));
const NAMES_MODULE = 'virtual:bestia-names';

/**
 * The English names of items, bestias and biomes, from the game client's own translation tables, so the app and the
 * game never disagree on what a thing is called. Read at build time; the server only ever sends identifiers.
 */
function bestiaNames(): Plugin {
	return {
		name: 'bestia-names',
		resolveId(id) {
			return id === NAMES_MODULE ? '\0' + NAMES_MODULE : null;
		},
		load(id) {
			if (id !== '\0' + NAMES_MODULE) return null;

			const names: Record<string, string> = {};
			// general.csv is the client's UI text; only its biome names are wanted here.
			for (const [table, wanted] of [['items.csv', ''], ['bestias.csv', ''], ['general.csv', 'BIOME_']]) {
				const path = LOCALIZATION + table;
				this.addWatchFile(path);
				for (const [key, english] of parseCsv(readFileSync(path, 'utf8')).slice(1)) {
					if (key?.startsWith(wanted) && english) names[key] = english;
				}
			}
			return `export default ${JSON.stringify(names)};`;
		}
	};
}

// The dev server stands in for the production proxy: one origin for the app, the login server and the
// api-server, so no request is cross-origin and the refresh cookie is first-party. IPv4 literals, because
// `localhost` may resolve to ::1 where the servers are not listening.
export default defineConfig({
	plugins: [
		// A single-page app: every route renders in the browser, and the static host answers every path with
		// index.html. The service worker is SvelteKit's own (src/service-worker.ts).
		sveltekit({
			preprocess: vitePreprocess(),
			adapter: adapter({ fallback: 'index.html' })
		}),
		bestiaNames()
	],
	server: {
		// The bestia portraits are the game client's own files, imported from beside this app.
		fs: { allow: ['.', '../bestia-client/src/Game/Bestia'] },
		port: 5173,
		strictPort: true,
		proxy: {
			'/api/v1/auth': 'http://127.0.0.1:8080',
			'/v1/companion/ws': { target: 'ws://127.0.0.1:8095', ws: true },
			'/v1/companion': 'http://127.0.0.1:8095'
		}
	},
	test: {
		include: ['src/**/*.test.ts']
	}
});
