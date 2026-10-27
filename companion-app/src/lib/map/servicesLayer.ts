import L from 'leaflet';
import { call } from '#lib/api/client.ts';
import type { MapMeta, Service } from '#lib/api/model.ts';
import { inWorld } from './tiles';

/** The finest-detail levels are the only ones the world lists services at, as in the game. */
const SERVICES_MAX_LEVEL = 3;

/** As the game draws them (`map_view.gd`): a colour and a silhouette per kind, so neither is the only cue. */
export const SERVICE_KINDS = ['MERCHANT', 'MARKET', 'INN', 'TEMPLE', 'GUARD', 'WARD_STONE'] as const;

export function serviceName(service: Service): string {
	const what = service.business
		? service.business.replace(/_/g, ' ').replace(/^./, (c) => c.toUpperCase())
		: service.kind.charAt(0) + service.kind.slice(1).toLowerCase().replace(/_/g, ' ');
	return service.settlement ? `${what} · ${service.settlement}` : what;
}

export function serviceHours(service: Service): string {
	if (service.openFrom == null || service.openTo == null) return '';
	const hour = (h: number) => `${String(h).padStart(2, '0')}:00`;
	return `Open ${hour(service.openFrom)} - ${hour(service.openTo)}`;
}

/**
 * The shops, markets, inns, temples, guards and ward stones the master has charted, drawn at the zooms the game
 * draws them at. [hidden] says which kinds the master switched off - the same setting as the game's legend, from
 * the notebook. Call [restart] when it changes.
 */
export function servicesLayer(
	masterId: number,
	map: L.Map,
	meta: MapMeta,
	hidden: () => readonly string[],
	onService: (service: Service) => void
): { group: L.LayerGroup; restart: () => void } {
	const group = L.layerGroup();
	const loaded = new Set<string>();
	let generation = 0;

	const show = (services: Service[]) => {
		const off = hidden();
		for (const service of services) {
			if (off.includes(service.kind)) continue;
			L.marker([service.y, service.x], {
				icon: L.divIcon({ className: `service service-${service.kind.toLowerCase()}`, html: '<span></span>', iconSize: [14, 14], iconAnchor: [7, 7] })
			})
				.bindTooltip(serviceName(service), { direction: 'top', offset: [0, -6] })
				.on('click', (e: L.LeafletMouseEvent) => {
					L.DomEvent.stopPropagation(e);
					onService(service);
				})
				.addTo(group);
		}
	};

	const refresh = () => {
		const level = Math.round(-map.getZoom());
		if (level > SERVICES_MAX_LEVEL) return;

		const current = generation;
		const span = meta.tileSize * 2 ** Math.max(level, meta.minLevel);
		const bounds = map.getBounds();
		for (let x = Math.floor(bounds.getWest() / span); x <= Math.floor(bounds.getEast() / span); x++) {
			for (let y = Math.floor(bounds.getSouth() / span); y <= Math.floor(bounds.getNorth() / span); y++) {
				const tile = { level: Math.max(level, meta.minLevel), x, y };
				const id = `${tile.level}/${x}/${y}`;
				if (loaded.has(id) || !inWorld(tile, meta.tileSize, meta.worldWidthMetres, meta.worldHeightMetres)) continue;
				loaded.add(id);

				call(`/v1/companion/masters/${masterId}/map/s/${id}`)
					// A 404 is ground the master has not charted, so there is nothing to show there.
					.then((r) => (r.ok ? (r.json() as Promise<{ services: Service[] }>) : { services: [] }))
					.then((answer) => {
						if (current === generation) show(answer.services);
					})
					.catch(() => {
						if (current === generation) loaded.delete(id);
					});
			}
		}
	};

	const restart = () => {
		generation++;
		group.clearLayers();
		loaded.clear();
		refresh();
	};

	map.on('zoomend', restart);
	map.on('moveend', refresh);
	map.whenReady(refresh);
	return { group, restart };
}
