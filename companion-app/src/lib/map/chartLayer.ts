import L from 'leaflet';
import { call } from '$lib/api/client';
import type { MapMeta, Place } from '$lib/api/model';
import { inWorld, serverTileOf } from './tiles';

/** One step past 1 m per pixel: tiles of level 0, drawn twice their size. */
export const MAX_ZOOM = 1;

/**
 * The master's charted map as a Leaflet layer. Tiles are fetched with the session's token, so they cannot be
 * plain <img> URLs; a 404 is uncharted ground and is drawn as fog, never retried.
 */
class ChartLayer extends L.GridLayer {
	private readonly urls = new Map<string, string>();

	constructor(
		private readonly masterId: number,
		private readonly meta: MapMeta
	) {
		// A layer has a zoom range of its own, 0 and up by default, and this map lives at negative zooms.
		super({
			tileSize: meta.tileSize,
			minZoom: -meta.maxLevel,
			maxZoom: MAX_ZOOM,
			minNativeZoom: -meta.maxLevel,
			maxNativeZoom: 0 - meta.minLevel
		});
		this.on('tileunload', (event: L.TileEvent) => this.release(event.coords));
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

		call(`/v1/companion/masters/${this.masterId}/map/t/${tile.level}/${tile.x}/${tile.y}.png`)
			.then(async (response) => {
				if (!response.ok) {
					img.classList.add('fog');
					return;
				}
				const url = URL.createObjectURL(await response.blob());
				this.urls.set(key(coords), url);
				img.src = url;
			})
			.catch(() => img.classList.add('fog'))
			.finally(() => done(undefined, img));
		return img;
	}

	private release(coords: L.Coords): void {
		const url = this.urls.get(key(coords));
		if (url) URL.revokeObjectURL(url);
		this.urls.delete(key(coords));
	}
}

export function chartLayer(masterId: number, meta: MapMeta): L.GridLayer {
	return new ChartLayer(masterId, meta);
}

/** The named places of the tiles in view, as labels over the symbols the tiles already draw. */
export function placeLabels(masterId: number, map: L.Map, meta: MapMeta): L.LayerGroup {
	const group = L.layerGroup();
	const loaded = new Set<string>();

	const refresh = () => {
		const level = Math.min(Math.max(Math.round(-map.getZoom()), meta.minLevel), meta.maxLevel);
		const span = meta.tileSize * 2 ** level;
		const bounds = map.getBounds();

		for (let x = Math.floor(bounds.getWest() / span); x <= Math.floor(bounds.getEast() / span); x++) {
			for (let y = Math.floor(bounds.getSouth() / span); y <= Math.floor(bounds.getNorth() / span); y++) {
				const tile = { level, x, y };
				const id = `${level}/${x}/${y}`;
				if (loaded.has(id) || !inWorld(tile, meta.tileSize, meta.worldWidthMetres, meta.worldHeightMetres)) continue;
				loaded.add(id);

				call(`/v1/companion/masters/${masterId}/map/p/${id}.json`)
					.then((r) => (r.ok ? (r.json() as Promise<Place[]>) : []))
					.then((places) => {
						for (const place of places) {
							if (!place.name) continue;
							L.marker([place.y, place.x], {
								interactive: false,
								icon: L.divIcon({ className: 'place-label', html: escape(place.name), iconSize: undefined })
							}).addTo(group);
						}
					})
					.catch(() => loaded.delete(id));
			}
		}
	};

	map.on('zoomend', () => {
		group.clearLayers();
		loaded.clear();
		refresh();
	});
	map.on('moveend', refresh);
	queueMicrotask(refresh);
	return group;
}

function key(coords: L.Coords): string {
	return `${coords.z}/${coords.x}/${coords.y}`;
}

function escape(text: string): string {
	return text.replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]!);
}
