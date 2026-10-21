/// <reference types="@sveltejs/kit" />
/// <reference no-default-lib="true"/>
/// <reference lib="esnext" />
/// <reference lib="webworker" />

/**
 * The app's own files, cached so it opens without a network; and the notifications. API answers are not cached
 * here: they belong to one account, and a shared device must not show the last player's Masters to the next.
 */
import { build, files, version } from '$service-worker';

const worker = self as unknown as ServiceWorkerGlobalScope;
const SHELL = `shell-${version}`;
const ASSETS = [...build, ...files];

worker.addEventListener('install', (event) => {
	event.waitUntil(
		caches
			.open(SHELL)
			.then((cache) => cache.addAll([...ASSETS, '/']))
			.then(() => worker.skipWaiting())
	);
});

worker.addEventListener('activate', (event) => {
	event.waitUntil(
		caches
			.keys()
			.then((keys) => Promise.all(keys.filter((k) => k !== SHELL).map((k) => caches.delete(k))))
			.then(() => worker.clients.claim())
	);
});

worker.addEventListener('fetch', (event) => {
	const request = event.request;
	const url = new URL(request.url);
	if (request.method !== 'GET' || url.origin !== location.origin) return;
	if (url.pathname.startsWith('/v1/') || url.pathname.startsWith('/api/')) return;

	// Every route is the same single page, so a navigation offline gets the cached shell.
	if (request.mode === 'navigate') {
		event.respondWith(fetch(request).catch(async () => (await caches.match('/')) ?? Response.error()));
		return;
	}

	if (ASSETS.includes(url.pathname)) {
		event.respondWith(caches.match(url.pathname).then((hit) => hit ?? fetch(request)));
	}
});

type Payload = { title: string; body: string; url: string; tag: string };

worker.addEventListener('push', (event) => {
	const payload = (event.data?.json() ?? { title: 'Bestia', body: '', url: '/', tag: 'bestia' }) as Payload;

	event.waitUntil(
		worker.registration.showNotification(payload.title, {
			body: payload.body,
			tag: payload.tag,
			icon: '/icons/icon-192.png',
			badge: '/icons/icon-192.png',
			data: { url: payload.url }
		})
	);
});

worker.addEventListener('notificationclick', (event) => {
	event.notification.close();
	const url = (event.notification.data as { url?: string } | null)?.url ?? '/';

	event.waitUntil(
		worker.clients.matchAll({ type: 'window', includeUncontrolled: true }).then(async (windows) => {
			const open = windows[0] as WindowClient | undefined;
			if (open) {
				await open.focus();
				await open.navigate(url);
				return;
			}
			await worker.clients.openWindow(url);
		})
	);
});
