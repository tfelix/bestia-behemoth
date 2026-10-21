<script lang="ts">
	import 'leaflet/dist/leaflet.css';
	import L from 'leaflet';
	import { page } from '$app/state';
	import { getJson } from '$lib/api/client';
	import type { Bestia, MapMeta, MasterSheet } from '$lib/api/model';
	import { guarded } from '$lib/guard';
	import { live } from '$lib/live.svelte';
	import { ChartCache } from '$lib/map/chartCache';
	import { chartLayer, MAX_ZOOM, placeLabels } from '$lib/map/chartLayer';
	import { speciesName } from '$lib/names';

	const masterId = $derived(Number(page.params.id));
	let container = $state<HTMLDivElement>();
	let failure = $state<string | null>(null);

	$effect(() => {
		const id = masterId;
		const element = container;
		if (!element) return;

		let map: L.Map | null = null;
		const markers = L.layerGroup();
		// Per visit: tiles and place lists already asked for, so zooming and panning back over them costs nothing.
		const cache = new ChartCache();

		const placeMarkers = async () => {
			const [sheet, bestias] = await Promise.all([
				getJson<MasterSheet>(`/v1/companion/masters/${id}`),
				getJson<Bestia[]>(`/v1/companion/masters/${id}/bestias`)
			]);
			markers.clearLayers();
			for (const b of bestias) {
				marker([b.position.northMetres, b.position.eastMetres], 'bestia', b.name ?? speciesName(b.species)).addTo(markers);
			}
			marker([sheet.position.northMetres, sheet.position.eastMetres], 'master', sheet.name).addTo(markers);
			return sheet;
		};

		guarded(async () => {
			const meta = await getJson<MapMeta>(`/v1/companion/masters/${id}/map/meta`);
			map = L.map(element, {
				crs: L.CRS.Simple,
				minZoom: -meta.maxLevel,
				maxZoom: MAX_ZOOM,
				zoomSnap: 1,
				attributionControl: false,
				maxBounds: [
					[-meta.worldHeightMetres * 0.1, -meta.worldWidthMetres * 0.1],
					[meta.worldHeightMetres * 1.1, meta.worldWidthMetres * 1.1]
				]
			});
			chartLayer(id, meta, cache).addTo(map);
			placeLabels(id, map, meta, cache).addTo(map);
			markers.addTo(map);

			const sheet = await placeMarkers();
			map.setView([sheet.position.northMetres, sheet.position.eastMetres], -3);
		}).catch((e) => (failure = e instanceof Error ? e.message : String(e)));

		const unsubscribe = live.on((event) => {
			if (event.data.masterId !== id) return;
			if (event.type === 'ChartsChanged') cache.clear();
			void placeMarkers().catch(() => {});
		});

		return () => {
			unsubscribe();
			map?.remove();
		};
	});

	function marker(at: L.LatLngExpression, kind: 'master' | 'bestia', title: string): L.Marker {
		return L.marker(at, {
			title,
			icon: L.divIcon({ className: `pin pin-${kind}`, iconSize: [18, 18], iconAnchor: [9, 9] })
		}).bindTooltip(title, { direction: 'top', offset: [0, -8] });
	}
</script>

<main class="map-page">
	{#if failure}<p class="alert">{failure}</p>{/if}
	<div class="map" bind:this={container}></div>
	<p class="legend label"><span class="dot master"></span> your Master <span class="dot bestia"></span> your Bestia · fog is ground you have not charted</p>
</main>

<style>
	.map-page {
		position: fixed;
		inset: 3rem 0 calc(var(--nav-height) + env(safe-area-inset-bottom)) 0;
		display: flex;
		flex-direction: column;
	}

	.map {
		flex: 1;
		background: repeating-linear-gradient(45deg, #17130f 0 8px, #1b1612 8px 16px);
	}

	.map :global(.chart-tile.fog) {
		visibility: hidden;
	}

	.map :global(.leaflet-container) {
		background: transparent;
	}

	.map :global(.place-label) {
		width: auto !important;
		height: auto !important;
		white-space: nowrap;
		transform: translate(-50%, 0.6rem);
		font-family: var(--display);
		font-size: 0.75rem;
		font-weight: 600;
		color: #2a1d10;
		text-shadow: 0 0 3px #f4e6c8, 0 0 3px #f4e6c8;
	}

	.map :global(.pin) {
		border: 2px solid #1a1410;
		border-radius: 50%;
		box-shadow: 0 0 0 2px var(--brass), 0 2px 6px rgb(0 0 0 / 60%);
	}

	.map :global(.pin-master) {
		background: var(--accent);
	}

	.map :global(.pin-bestia) {
		background: var(--good);
	}

	.legend {
		margin: 0;
		padding: 0.4rem var(--gutter);
		text-align: center;
	}

	.dot {
		display: inline-block;
		width: 0.6rem;
		height: 0.6rem;
		border-radius: 50%;
		vertical-align: middle;
	}

	.dot.master {
		background: var(--accent);
	}

	.dot.bestia {
		background: var(--good);
	}
</style>
