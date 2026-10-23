<script lang="ts">
	import 'leaflet/dist/leaflet.css';
	import L from 'leaflet';
	import { page } from '$app/state';
	import { ApiError, getJson } from '$lib/api/client';
	import type { Bestia, Catalog, MapMeta, MasterSheet, Position, Route } from '$lib/api/model';
	import TravelSheet from '$lib/components/TravelSheet.svelte';
	import { guarded } from '$lib/guard';
	import { live } from '$lib/live.svelte';
	import { ChartCache } from '$lib/map/chartCache';
	import { chartLayer, habitatLayer, MAX_ZOOM, placeLabels } from '$lib/map/chartLayer';
	import { bestiaColor, MASTER_COLOR } from '$lib/map/slotColors';
	import { speciesName } from '$lib/names';
	import { deleteRoute, renameRoute, saveRoute, sendJourney, stopJourney, stopsAhead, wayAhead } from '$lib/travel';

	const masterId = $derived(Number(page.params.id));
	/** The species whose habitat is laid over the map, picked in the compendium; 0 for none. */
	const habitat = $derived(Number(page.url.searchParams.get('habitat')) || 0);
	let habitatName = $state<string | null>(null);
	let container = $state<HTMLDivElement>();
	let failure = $state<string | null>(null);

	let bestias = $state<Bestia[]>([]);
	let routes = $state<Route[]>([]);
	let selectedId = $state<number | null>(null);
	let draft = $state<Position[]>([]);
	let busy = $state(false);
	let note = $state<string | null>(null);
	/** Where a journey gave up, by bestia, from the live event; shown until that bestia sets off again. */
	let gaveUpAt = $state<Record<number, Position>>({});
	let travelLayers: { journeys: L.LayerGroup; draft: L.LayerGroup; failures: L.LayerGroup } | null = null;

	$effect(() => {
		const id = habitat;
		habitatName = null;
		if (id === 0) return;
		void getJson<Catalog>('/v1/companion/catalog')
			.then((catalog) => {
				const species = catalog.bestias.find((s) => s.id === id);
				if (species && id === habitat) habitatName = speciesName(species.identifier);
			})
			.catch(() => {});
	});

	$effect(() => {
		const id = masterId;
		const element = container;
		const species = habitat;
		if (!element) return;

		let map: L.Map | null = null;
		const markers = L.layerGroup();
		// Per visit: tiles and place lists already asked for, so zooming and panning back over them costs nothing.
		const cache = new ChartCache();

		const placeMarkers = async () => {
			const [sheet, roster] = await Promise.all([
				getJson<MasterSheet>(`/v1/companion/masters/${id}`),
				getJson<Bestia[]>(`/v1/companion/masters/${id}/bestias`)
			]);
			bestias = roster;
			if (selectedId === null || !roster.some((b) => b.id === selectedId)) {
				selectedId = roster.find((b) => b.inWorld)?.id ?? null;
			}
			markers.clearLayers();
			roster.forEach((b, i) => {
				marker(latLng(b.position), bestiaColor(i), b.name ?? speciesName(b.species)).addTo(markers);
			});
			marker(latLng(sheet.position), MASTER_COLOR, sheet.name).addTo(markers);
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
			if (species !== 0) habitatLayer(id, species, meta, cache).addTo(map);
			placeLabels(id, map, meta, cache).addTo(map);
			travelLayers = { journeys: L.layerGroup().addTo(map), draft: L.layerGroup().addTo(map), failures: L.layerGroup().addTo(map) };
			markers.addTo(map);
			map.on('click', (e: L.LeafletMouseEvent) => addStop({ eastMetres: e.latlng.lng, northMetres: e.latlng.lat }));
			routes = await getJson<Route[]>(`/v1/companion/masters/${id}/routes`);

			const sheet = await placeMarkers();
			map.setView([sheet.position.northMetres, sheet.position.eastMetres], -3);
		}).catch((e) => (failure = e instanceof Error ? e.message : String(e)));

		const unsubscribe = live.on((event) => {
			if (event.data.masterId !== id) return;
			if (event.type === 'ChartsChanged' || event.type === 'CompendiumChanged') cache.clear();
			if (event.type === 'JourneyEnded') noteJourneyEnded(event.data);
			void placeMarkers().catch(() => {});
		});

		return () => {
			unsubscribe();
			travelLayers = null;
			map?.remove();
		};
	});

	// Every journey in its bestia's colour, and where any gave up. Redrawn whenever the roster or the draft moves.
	$effect(() => {
		const layers = travelLayers;
		if (!layers) return;

		layers.journeys.clearLayers();
		bestias.forEach((b, i) => {
			const colour = bestiaColor(i);
			const ahead = wayAhead(b);
			if (ahead.length >= 2) L.polyline(ahead.map(latLng), { color: colour, weight: 4, opacity: 0.9 }).addTo(layers.journeys);
			if (b.journey) stopsAhead(b.journey).forEach((stop, n) => stopMarker(stop, n + 1, colour).addTo(layers.journeys));
		});

		layers.failures.clearLayers();
		for (const [bestiaId, at] of Object.entries(gaveUpAt)) {
			const b = bestias.find((x) => x.id === Number(bestiaId));
			L.marker(latLng(at), { icon: L.divIcon({ className: 'gave-up', html: '✕', iconSize: [22, 22], iconAnchor: [11, 11] }) })
				.bindTooltip(`${b?.name ?? 'A Bestia'} gave up here`, { direction: 'top' })
				.addTo(layers.failures);
		}

		layers.draft.clearLayers();
		const selected = bestias.find((b) => b.id === selectedId);
		if (selected && draft.length > 0) {
			const colour = bestiaColor(bestias.indexOf(selected));
			L.polyline([selected.position, ...draft].map(latLng), { color: colour, weight: 3, dashArray: '6 6' }).addTo(layers.draft);
			draft.forEach((stop, n) => stopMarker(stop, n + 1, colour).addTo(layers.draft));
		}
	});

	function addStop(at: Position) {
		const selected = bestias.find((b) => b.id === selectedId);
		if (!selected?.inWorld || draft.length >= MAX_STOPS) return;
		draft = [...draft, at];
	}

	function noteJourneyEnded(data: Record<string, unknown>) {
		const bestiaId = Number(data.playerBestiaId);
		const position = data.position as { east: number; north: number } | undefined;
		const { [bestiaId]: _, ...others } = gaveUpAt;
		gaveUpAt = data.failure && position ? { ...others, [bestiaId]: { eastMetres: position.east, northMetres: position.north } } : others;
	}

	/** Runs one order, with a fresh key, and words what went wrong the way the app does elsewhere. */
	async function order(work: (key: string) => Promise<void>) {
		busy = true;
		note = null;
		try {
			await guarded(() => work(crypto.randomUUID()));
			await placeMarkersFor(masterId);
		} catch (e) {
			note = e instanceof ApiError && e.status === 404 ? 'It rests until you are back in the world.' : (e as Error).message;
		} finally {
			busy = false;
		}
	}

	function send() {
		const id = selectedId;
		const stops = draft;
		if (id === null) return;
		void order(async (key) => {
			await sendJourney(masterId, id, { waypoints: stops, reverse: false }, key);
			forgetGaveUp(id);
			draft = [];
		});
	}

	function sendAlong(route: Route, back: boolean) {
		const id = selectedId;
		if (id === null) return;
		void order(async (key) => {
			await sendJourney(masterId, id, { waypoints: [], savedRouteId: route.id, reverse: back }, key);
			forgetGaveUp(id);
		});
	}

	function stop(b: Bestia) {
		void order((key) => stopJourney(masterId, b.id, key));
	}

	/** A route starts where the bestia it was drafted for stands, so it can be walked back the other way too. */
	function keepRoute() {
		const selected = bestias.find((b) => b.id === selectedId);
		if (!selected) return;
		const waypoints = [selected.position, ...draft];
		void order(async (key) => {
			try {
				routes = await saveRoute(masterId, waypoints, '', key);
				draft = [];
			} catch (e) {
				if (e instanceof ApiError && e.status === 400) throw new Error('You keep as many routes as you may. Forget one first.');
				throw e;
			}
		});
	}

	function rename(route: Route, name: string) {
		void order(async (key) => {
			routes = await renameRoute(masterId, route.id, name, key);
		});
	}

	function forget(route: Route) {
		void order(async (key) => {
			routes = await deleteRoute(masterId, route.id, key);
		});
	}

	function forgetGaveUp(bestiaId: number) {
		const { [bestiaId]: _, ...others } = gaveUpAt;
		gaveUpAt = others;
	}

	/** The roster again after an order, so the way just ordered is drawn without waiting for an event. */
	async function placeMarkersFor(id: number) {
		bestias = await getJson<Bestia[]>(`/v1/companion/masters/${id}/bestias`);
	}

	function latLng(at: Position): L.LatLngTuple {
		return [at.northMetres, at.eastMetres];
	}

	function marker(at: L.LatLngExpression, colour: string, title: string): L.Marker {
		return L.marker(at, {
			title,
			icon: L.divIcon({ className: 'pin', html: `<span style="background:${colour}"></span>`, iconSize: [18, 18], iconAnchor: [9, 9] })
		}).bindTooltip(title, { direction: 'top', offset: [0, -8] });
	}

	function stopMarker(at: Position, number: number, colour: string): L.Marker {
		return L.marker(latLng(at), {
			interactive: false,
			icon: L.divIcon({ className: 'stop', html: `<span style="background:${colour}">${number}</span>`, iconSize: [20, 20], iconAnchor: [10, 10] })
		});
	}

	/** As many as one journey may have. */
	const MAX_STOPS = 15;
</script>

<main class="map-page">
	{#if failure}<p class="alert">{failure}</p>{/if}
	{#if habitat !== 0}
		<a class="chip habitat" href={`/m/${masterId}/map`} title="Stop showing this habitat">Habitat: {habitatName ?? '…'} ✕</a>
	{/if}
	<div class="map-area">
		<div class="map" bind:this={container}></div>
		{#if bestias.length > 0}
			<TravelSheet
				{bestias}
				{routes}
				bind:selectedId
				{draft}
				{busy}
				{note}
				onsend={send}
				onclear={() => (draft = [])}
				onsave={keepRoute}
				onstop={stop}
				onroute={sendAlong}
				onrename={rename}
				ondelete={forget}
			/>
		{/if}
	</div>
	<p class="legend label">Each Bestia's way is drawn in its colour · fog is ground you have not charted</p>
</main>

<style>
	.map-page {
		position: fixed;
		inset: 3rem 0 calc(var(--nav-height) + env(safe-area-inset-bottom)) 0;
		display: flex;
		flex-direction: column;
	}

	.map-area {
		position: relative;
		flex: 1;
		display: flex;
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

	.map :global(.pin span) {
		display: block;
		width: 100%;
		height: 100%;
		border: 2px solid #1a1410;
		border-radius: 50%;
		box-shadow: 0 0 0 2px var(--brass), 0 2px 6px rgb(0 0 0 / 60%);
	}

	.map :global(.stop span) {
		display: grid;
		place-items: center;
		width: 100%;
		height: 100%;
		border: 2px solid #1a1410;
		border-radius: 50%;
		color: #1a1410;
		font: 700 0.7rem var(--label);
	}

	.map :global(.gave-up) {
		display: grid;
		place-items: center;
		color: var(--bad);
		font-size: 1.3rem;
		font-weight: 700;
		text-shadow: 0 0 3px #000, 0 0 3px #000;
	}

	.habitat {
		position: absolute;
		top: 0.5rem;
		left: 50%;
		z-index: 500;
		transform: translateX(-50%);
		background: var(--card);
		color: var(--brass);
	}

	.legend {
		margin: 0;
		padding: 0.4rem var(--gutter);
		text-align: center;
	}

</style>
