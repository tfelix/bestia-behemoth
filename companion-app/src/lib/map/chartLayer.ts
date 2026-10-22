import L from 'leaflet';
import { call, SignedOut } from '$lib/api/client';
import type { MapMeta, Place } from '$lib/api/model';
import type { ChartCache } from './chartCache';
import { isTransient, MAX_ATTEMPTS, retryDelayMillis } from './retry';
import { inWorld, serverTileOf, type ServerTile } from './tiles';

/** One step past 1 m per pixel: tiles of level 0, drawn twice their size. */
export const MAX_ZOOM = 1;

/**
 * The master's charted map as a Leaflet layer. Tiles are fetched with the session's token, so they cannot be
 * plain <img> URLs. Every request is spent from the account's budget, so a tile already fetched is taken from
 * the [cache], a failure that may pass (over budget, a busy world) is asked again, and a tile that has left the
 * view stops being asked for. Only a 404 is uncharted ground, drawn as fog.
 */
class ChartLayer extends L.GridLayer {
	private readonly urls = new Map<string, string>();
	private readonly requests = new Map<string, AbortController>();

	constructor(
		private readonly masterId: number,
		private readonly meta: MapMeta,
		private readonly cache: ChartCache
	) {
		// A layer has a zoom range of its own, 0 and up by default, and this map lives at negative zooms.
		super({
			tileSize: meta.tileSize,
			minZoom: -meta.maxLevel,
			maxZoom: MAX_ZOOM,
			minNativeZoom: -meta.maxLevel,
			maxNativeZoom: 0 - meta.minLevel,
			// Ask once the view has settled, not on every frame of a drag or a zoom: most of what is asked for
			// mid-gesture is out of view a moment later.
			updateWhenIdle: true,
			updateWhenZooming: false
		});
		this.on('tileunload', (event: L.TileEvent) => this.release(event.coords));

		// The charts changed: the tiles of the view are asked for again, and the cache has just been emptied.
		let unsubscribe: (() => void) | null = null;
		this.on('add', () => (unsubscribe = this.cache.onClear(() => this.redraw())));
		this.on('remove', () => unsubscribe?.());
	}

	protected createTile(coords: L.Coords, done: L.DoneCallback): HTMLElement {
		const img = document.createElement('img');
		img.alt = '';
		img.className = 'chart-tile';
		const tile = serverTileOf(coords);

		if (!inWorld(tile, this.meta.tileSize, this.meta.worldWidthMetres, this.meta.worldHeightMetres)) {
			img.classList.add('fog');
			queueMicrotask(() => done(undefined, img));
			return img;
		}

		const request = new AbortController();
		this.requests.set(key(coords), request);
		void this.fetchTile(tile, coords, img, request.signal).then(() => done(undefined, img));
		return img;
	}

	private async fetchTile(tile: ServerTile, coords: L.Coords, img: HTMLImageElement, signal: AbortSignal): Promise<void> {
		const path = `/v1/companion/masters/${this.masterId}/map/t/${tile.level}/${tile.x}/${tile.y}.png`;

		const known = this.cache.tiles.get(path);
		if (known !== undefined) {
			this.show(img, coords, known);
			return;
		}

		for (let attempt = 1; ; attempt++) {
			let retryAfter: string | null = null;
			try {
				const response = await call(path, { signal });
				if (response.ok) {
					const blob = await response.blob();
					this.cache.tiles.set(path, blob);
					// Gone from the view while the bytes came in: nobody will revoke a URL made now.
					if (!signal.aborted) this.show(img, coords, blob);
					return;
				}
				if (response.status === 404) {
					this.cache.tiles.set(path, null);
					this.show(img, coords, null);
					return;
				}
				if (!isTransient(response.status)) return;
				retryAfter = response.headers.get('Retry-After');
			} catch (e) {
				if (signal.aborted || e instanceof SignedOut) return;
			}

			if (attempt >= MAX_ATTEMPTS) return;
			await sleep(retryDelayMillis(attempt, retryAfter), signal);
			if (signal.aborted) return;
		}
	}

	/** The tile's bytes, or null for ground that is not charted. */
	private show(img: HTMLImageElement, coords: L.Coords, blob: Blob | null): void {
		if (blob === null) {
			img.classList.add('fog');
			return;
		}
		const url = URL.createObjectURL(blob);
		this.urls.set(key(coords), url);
		img.src = url;
	}

	private release(coords: L.Coords): void {
		this.requests.get(key(coords))?.abort();
		this.requests.delete(key(coords));
		const url = this.urls.get(key(coords));
		if (url) URL.revokeObjectURL(url);
		this.urls.delete(key(coords));
	}
}

export function chartLayer(masterId: number, meta: MapMeta, cache: ChartCache): L.GridLayer {
	return new ChartLayer(masterId, meta, cache);
}

/** The named places of the tiles in view, as labels over the symbols the tiles already draw. */
export function placeLabels(masterId: number, map: L.Map, meta: MapMeta, cache: ChartCache): L.LayerGroup {
	const group = L.layerGroup();
	const loaded = new Set<string>();
	// Bumped at every zoom, so an answer for the level just left does not add its labels to the new one.
	let generation = 0;

	const show = (places: Place[]) => {
		for (const place of places) {
			if (!place.name) continue;
			L.marker([place.y, place.x], {
				interactive: false,
				icon: L.divIcon({ className: 'place-label', html: escape(place.name), iconSize: undefined })
			}).addTo(group);
		}
	};

	const refresh = () => {
		const current = generation;
		const level = Math.min(Math.max(Math.round(-map.getZoom()), meta.minLevel), meta.maxLevel);
		const span = meta.tileSize * 2 ** level;
		const bounds = map.getBounds();

		for (let x = Math.floor(bounds.getWest() / span); x <= Math.floor(bounds.getEast() / span); x++) {
			for (let y = Math.floor(bounds.getSouth() / span); y <= Math.floor(bounds.getNorth() / span); y++) {
				const tile = { level, x, y };
				const id = `${level}/${x}/${y}`;
				if (loaded.has(id) || !inWorld(tile, meta.tileSize, meta.worldWidthMetres, meta.worldHeightMetres)) continue;
				loaded.add(id);

				const known = cache.places.get(id);
				if (known) {
					show(known);
					continue;
				}

				call(`/v1/companion/masters/${masterId}/map/p/${id}.json`)
					.then((r) => {
						if (r.ok) return r.json() as Promise<Place[]>;
						// Only a 404 is "no places here"; anything else is asked again at the next move.
						if (r.status === 404) return [];
						throw new Error(`Places of ${id}: ${r.status}`);
					})
					.then((places) => {
						cache.places.set(id, places);
						if (current === generation) show(places);
					})
					.catch(() => {
						if (current === generation) loaded.delete(id);
					});
			}
		}
	};

	// Labels are drawn per level, so a zoom, or charts that changed, starts them over: from the cache where it can.
	const restart = () => {
		generation++;
		group.clearLayers();
		loaded.clear();
		refresh();
	};

	map.on('zoomend', restart);
	map.on('moveend', refresh);
	map.on('unload', cache.onClear(restart));
	// The map may not have a view yet, and getBounds() throws until it has one.
	map.whenReady(refresh);
	return group;
}

/** Resolves after [millis], or at once when [signal] aborts. */
function sleep(millis: number, signal: AbortSignal): Promise<void> {
	return new Promise((resolve) => {
		const timer = setTimeout(resolve, millis);
		signal.addEventListener(
			'abort',
			() => {
				clearTimeout(timer);
				resolve();
			},
			{ once: true }
		);
	});
}

function key(coords: L.Coords): string {
	return `${coords.z}/${coords.x}/${coords.y}`;
}

function escape(text: string): string {
	return text.replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]!);
}
