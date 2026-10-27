<script lang="ts">
	import 'leaflet/dist/leaflet.css';
	import L from 'leaflet';
	import { untrack } from 'svelte';
	import { page } from '$app/state';
	import { ApiError, getJson } from '#lib/api/client.ts';
	import type { Bestia, Catalog, MapMeta, MasterSheet, Place, PlaceDetail, Position, Service } from '#lib/api/model.ts';
	import MapCard, { type Card } from '#lib/components/MapCard.svelte';
	import TravelSheet from '#lib/components/TravelSheet.svelte';
	import { guarded } from '#lib/guard.ts';
	import { live } from '#lib/live.svelte.ts';
	import { ChartCache } from '#lib/map/chartCache.ts';
	import { chartLayer, habitatLayer, MAX_ZOOM, placeLabels } from '#lib/map/chartLayer.ts';
	import { gridLayer } from '#lib/map/gridLayer.ts';
	import { serviceHours, serviceName, servicesLayer } from '#lib/map/servicesLayer.ts';
	import { formatGridRef, MAX_DIGITS, parseGridRef } from '#lib/map/gridRef.ts';
	import { bestiaColor, MASTER_COLOR } from '#lib/map/slotColors.ts';
	import { speciesName } from '#lib/names.ts';
	import { Notebook, SINGLE_KEY } from '#lib/notebook/notebook.svelte.ts';
	import type { NotebookEntry } from '#lib/notebook/rules.ts';
	import { distance, distanceText, ROUTES, routeBody, routeOf, sendJourney, stopJourney, stopsAhead, wayAhead, type Route, type RouteValue } from '#lib/travel.ts';

	/** The notebook collection of the master's own map marks, shared with the game. */
	const MARKS = 'marks';
	type MarkValue = { name: string; east: number; north: number; icon: string; note?: string | null };
	/** How the master likes their map, kept with the game's: the service kinds its legend hides, and the grid. */
	const MAP_PREFS = 'map-prefs';
	type MapPrefs = { hiddenServiceKinds: string[]; gridOverlay: boolean };

	const masterId = $derived(Number(page.params.id));
	/** The species whose habitat is laid over the map, picked in the compendium; 0 for none. */
	const habitat = $derived(Number(page.url.searchParams.get('habitat')) || 0);
	let habitatName = $state<string | null>(null);
	let container = $state<HTMLDivElement>();
	let failure = $state<string | null>(null);

	let bestias = $state<Bestia[]>([]);
	/** The master's notebook, shared with the game: its routes, and later its marks and map preferences. */
	let notebook = $state.raw<Notebook | null>(null);
	const routes = $derived(notebook ? notebook.entries<RouteValue>(ROUTES).map(routeOf) : []);
	const marks = $derived(notebook ? notebook.entries<MarkValue>(MARKS) : []);
	const prefs = $derived(notebook?.document<MapPrefs>(MAP_PREFS) ?? null);
	let services: { restart: () => void } | null = null;
	let selectedId = $state<number | null>(null);
	/** The master as the map draws it; its sheet comes apart from the roster, so its marker is kept here. */
	let master = $state<{ name: string; position: Position } | null>(null);
	let draft = $state<Position[]>([]);
	/** Whether a tap on the map adds a stop to the draft, rather than asking what is there. */
	let routeMode = $state(false);
	/** What the last tap asked about, and what can be done there. */
	let card = $state<Card | null>(null);
	let busy = $state(false);
	let note = $state<string | null>(null);
	/** Where a journey gave up, by bestia, from the live event; shown until that bestia sets off again. */
	let gaveUpAt = $state<Record<number, Position>>({});
	let travelLayers = $state.raw<{ markers: L.LayerGroup; journeys: L.LayerGroup; draft: L.LayerGroup; failures: L.LayerGroup; marks: L.LayerGroup } | null>(null);
	let map: L.Map | null = null;
	let meta = $state.raw<MapMeta | null>(null);
	let grid: L.GridLayer | null = null;
	let pinMarker: L.Marker | null = null;
	let showGrid = $state(false);
	/** The grid reference under the pointer, or of the middle of the map on a touch screen. */
	let readout = $state('');
	let query = $state('');
	let found = $state<Place[]>([]);
	let searchNote = $state<string | null>(null);

	const selected = $derived(bestias.find((b) => b.id === selectedId) ?? null);

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

		let leaflet: L.Map | null = null;
		let gone = false;
		// Per visit: tiles and place lists already asked for, so zooming and panning back over them costs nothing.
		const cache = new ChartCache();

		const placeMarkers = async () => {
			const [sheet, roster] = await Promise.all([
				getJson<MasterSheet>(`/v1/companion/masters/${id}`),
				getJson<Bestia[]>(`/v1/companion/masters/${id}/bestias`)
			]);
			bestias = roster;
			master = { name: sheet.name, position: sheet.position };
			if (selectedId === null || !roster.some((b) => b.id === selectedId)) {
				// One that rests is picked too when none stands in the world, so the sheet can say why it cannot be sent.
				selectedId = (roster.find((b) => b.inWorld) ?? roster[0])?.id ?? null;
			}
			return sheet;
		};

		guarded(async () => {
			const meta = await getJson<MapMeta>(`/v1/companion/masters/${id}/map/meta`);
			if (gone) return;
			setMeta(meta);
			leaflet = L.map(element, {
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
			map = leaflet;
			chartLayer(id, meta, cache).addTo(leaflet);
			if (species !== 0) habitatLayer(id, species, meta, cache).addTo(leaflet);
			placeLabels(id, leaflet, meta, cache, openPlace).addTo(leaflet);
			const shown = servicesLayer(id, leaflet, meta, () => prefs?.hiddenServiceKinds ?? [], openService);
			shown.group.addTo(leaflet);
			services = shown;
			leaflet.on('click', (e: L.LeafletMouseEvent) => tapMap({ eastMetres: e.latlng.lng, northMetres: e.latlng.lat }));
			// A right click, where there is one, goes at once: the shortcut for what the card's Go does.
			leaflet.on('contextmenu', (e: L.LeafletMouseEvent) => goNow({ eastMetres: e.latlng.lng, northMetres: e.latlng.lat }));
			leaflet.on('mousemove', (e: L.LeafletMouseEvent) => (readout = refOf(e.latlng)));
			leaflet.on('moveend', () => leaflet && (readout = refOf(leaflet.getCenter())));
			grid = gridLayer(meta);
			if (showGrid) grid.addTo(leaflet);
			const kept = new Notebook(id);
			await Promise.all([kept.load(ROUTES), kept.load(MARKS), kept.load(MAP_PREFS)]);
			if (gone) return;
			notebook = kept;

			const sheet = await placeMarkers();
			if (gone) return;
			leaflet.setView([sheet.position.northMetres, sheet.position.eastMetres], -3);
			// Last, so what the layers draw first already has the roster.
			travelLayers = {
				markers: L.layerGroup().addTo(leaflet),
				journeys: L.layerGroup().addTo(leaflet),
				draft: L.layerGroup().addTo(leaflet),
				failures: L.layerGroup().addTo(leaflet),
				marks: L.layerGroup().addTo(leaflet)
			};
		}).catch((e) => (failure = e instanceof Error ? e.message : String(e)));

		const unsubscribe = live.on((event) => {
			if (event.data.masterId !== id) return;
			// A notebook write changes what the notebook holds and nothing on the map, so the roster is not asked again.
			if (notebook?.receive(event)) return;
			if (event.type === 'ChartsChanged' || event.type === 'CompendiumChanged') cache.clear();
			if (event.type === 'JourneyEnded') noteJourneyEnded(event.data);
			void placeMarkers().catch(() => {});
		});

		return () => {
			gone = true;
			unsubscribe();
			travelLayers = null;
			notebook = null;
			services = null;
			map = null;
			grid = null;
			pinMarker = null;
			leaflet?.remove();
		};
	});

	// Every pin: the master, and each bestia in its colour, ringed when it is the one being sent. A tap on one picks it.
	$effect(() => {
		const layers = travelLayers;
		if (!layers) return;

		layers.markers.clearLayers();
		bestias.forEach((b, i) => {
			marker(latLng(b.position), bestiaColor(i), nameOf(b), { chosen: b.id === selectedId, away: !b.inWorld })
				.on('click', () => select(b))
				.addTo(layers.markers);
		});
		if (master) marker(latLng(master.position), MASTER_COLOR, master.name).addTo(layers.markers);
	});

	// The legend is the game's, so a kind switched off there is gone here as well, and the grid follows it too.
	$effect(() => {
		void prefs?.hiddenServiceKinds;
		services?.restart();
	});

	$effect(() => {
		const wanted = prefs?.gridOverlay ?? false;
		if (wanted !== untrack(() => showGrid)) setGrid(wanted);
	});

	// The master's own marks, from the notebook the game keeps too: a tap on one asks about it.
	$effect(() => {
		const layers = travelLayers;
		if (!layers) return;

		layers.marks.clearLayers();
		for (const mark of marks) {
			L.marker([mark.value.north, mark.value.east], {
				icon: L.divIcon({ className: 'mark', html: '<span></span>', iconSize: [14, 14], iconAnchor: [7, 7] })
			})
				.bindTooltip(mark.value.name, { direction: 'top', offset: [0, -6] })
				.on('click', (e: L.LeafletMouseEvent) => {
					L.DomEvent.stopPropagation(e);
					openMark(mark);
				})
				.addTo(layers.marks);
		}
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
		if (selected && draft.length > 0) {
			const colour = bestiaColor(bestias.indexOf(selected));
			L.polyline([selected.position, ...draft].map(latLng), { color: colour, weight: 3, dashArray: '6 6' }).addTo(layers.draft);
			draft.forEach((stop, n) => draftMarker(stop, n, colour).addTo(layers.draft));
		}
	});

	function setMeta(value: MapMeta) {
		meta = value;
	}

	function setGrid(on: boolean) {
		showGrid = on;
		if (!map || !grid) return;
		if (on) grid.addTo(map);
		else grid.remove();
	}

	/** The button keeps the choice in the notebook, so the game's map shows the grid too. */
	function toggleGrid() {
		setGrid(!showGrid);
		const keep = { hiddenServiceKinds: prefs?.hiddenServiceKinds ?? [], gridOverlay: showGrid };
		void notebook?.put(MAP_PREFS, SINGLE_KEY, keep).catch(() => {});
	}

	/** A reference with a cell no finer than a pixel at the map's zoom. */
	function refOf(at: L.LatLng): string {
		if (!meta || !map) return '';
		const metresPerPixel = 2 ** -map.getZoom();
		const digits = Math.min(MAX_DIGITS, Math.max(1, MAX_DIGITS - Math.ceil(Math.log10(metresPerPixel))));
		return formatGridRef(at.lng, at.lat, meta.worldWidthMetres, meta.worldHeightMetres, digits);
	}

	/** A grid reference is answered at once; anything else is looked up among the places this master has charted. */
	async function search() {
		const text = query.trim();
		found = [];
		searchNote = null;
		if (!text || !meta) return;

		const point = parseGridRef(text, meta.worldWidthMetres, meta.worldHeightMetres);
		if (point) {
			showPin({ eastMetres: point.eastMetres, northMetres: point.northMetres });
			return;
		}

		try {
			const places = await getJson<Place[]>(`/v1/companion/masters/${masterId}/map/find?q=${encodeURIComponent(text)}`);
			if (places.length === 0) searchNote = `No charted place matches ${text}.`;
			else if (places.length === 1) goToPlace(places[0]);
			else found = places;
		} catch (e) {
			searchNote = (e as Error).message;
		}
	}

	function goToPlace(place: Place) {
		found = [];
		showPin({ eastMetres: place.x, northMetres: place.y });
	}

	function showPin(at: Position) {
		if (!map) return;
		pinMarker?.remove();
		pinMarker = L.marker(latLng(at), {
			interactive: false,
			icon: L.divIcon({ className: 'search-pin', html: '<span></span>', iconSize: [22, 22], iconAnchor: [11, 11] })
		}).addTo(map);
		map.setView(latLng(at), Math.max(map.getZoom(), -4));
	}

	function nameOf(b: Bestia): string {
		return b.name ?? speciesName(b.species);
	}

	/** Picks the bestia to send; a draft made for another is dropped. */
	function select(b: Bestia) {
		if (b.id !== selectedId) {
			draft = [];
			routeMode = false;
		}
		selectedId = b.id;
		note = null;
	}

	/** Picked from the list rather than the map: the map goes to where it stands. */
	function pick(b: Bestia) {
		select(b);
		map?.panTo(latLng(b.position));
	}

	/** Whether the selected bestia may be ordered, and if not, why not on the note. */
	function canSend(): boolean {
		if (!selected) {
			note = 'Pick a Bestia first: tap it on the map or in the list.';
			return false;
		}
		if (!selected.inWorld) {
			note = `${nameOf(selected)} rests until you are back in the world.`;
			return false;
		}
		note = null;
		return true;
	}

	/** In route mode a tap is one more stop; otherwise it asks what is there. */
	function tapMap(at: Position) {
		if (routeMode) {
			addStop(at);
			return;
		}
		openCard('Open ground', at, [{ label: 'Mark here', run: () => askMarkName(at) }]);
	}

	function addStop(at: Position) {
		if (!canSend()) return;
		if (draft.length >= MAX_STOPS) {
			note = `A journey has at most ${MAX_STOPS} stops.`;
			return;
		}
		routeMode = true;
		draft = [...draft, at];
	}

	/** Sends the selected bestia straight to [at], dropping any draft. */
	function goNow(at: Position) {
		if (!canSend()) return;
		routeMode = false;
		draft = [at];
		send();
	}

	/** The card for [at]: its grid reference and distance, Go and Add to route, then [extra]. */
	function openCard(title: string, at: Position, extra: Card['actions'], lines: string[] = []) {
		const away = selected ? `${distanceText(distance(selected.position, at))} away` : '';
		const ref = meta ? formatGridRef(at.eastMetres, at.northMetres, meta.worldWidthMetres, meta.worldHeightMetres, 3) : '';
		card = {
			title,
			lines: [...lines, ref, away],
			actions: [{ label: 'Go here', run: () => goNow(at) }, { label: 'Add to route', run: () => addStop(at) }, ...extra]
		};
	}

	/** The card for a town at once, then every service it has as the world tells it - none are drawn at coarse zooms. */
	function openPlace(place: Place) {
		const tier = place.tier ? place.tier.charAt(0) + place.tier.slice(1).toLowerCase() : '';
		const at = { eastMetres: place.x, northMetres: place.y };
		openCard(place.name ?? 'A place', at, [], [tier]);
		const opened = card;

		void getJson<PlaceDetail>(`/v1/companion/masters/${masterId}/map/places/${place.id}`)
			.then((detail) => {
				if (card !== opened) return;
				const goTo = detail.services.map((service) => ({
					label: `Go: ${serviceName(service)}`,
					run: () => goNow({ eastMetres: service.x, northMetres: service.y })
				}));
				openCard(place.name ?? 'A place', at, goTo, [tier, detail.region ?? '']);
			})
			.catch(() => {});
	}

	function openService(service: Service) {
		openCard(serviceName(service), { eastMetres: service.x, northMetres: service.y }, [], [serviceHours(service)]);
	}

	function openMark(mark: NotebookEntry<MarkValue>) {
		const at = { eastMetres: mark.value.east, northMetres: mark.value.north };
		openCard(mark.value.name, at, [
			{ label: 'Rename', run: () => askMarkName(at, mark) },
			{ label: 'Delete', run: () => void order(async () => notebook?.remove(MARKS, mark.key, mark.revision)) }
		], ['Your mark', mark.value.note ?? '']);
	}

	/** Asks what to call a mark at [at] - a new one, or [mark] renamed - and keeps it in the notebook. */
	function askMarkName(at: Position, mark?: NotebookEntry<MarkValue>) {
		card = {
			title: mark ? 'Rename mark' : 'Mark this place',
			lines: [],
			actions: [],
			ask: {
				placeholder: 'Name',
				value: mark?.value.name ?? '',
				submit: (name) => {
					const value = { ...(mark?.value ?? { icon: 'pin' }), name, east: at.eastMetres, north: at.northMetres };
					void order(async () => (mark ? notebook?.put(MARKS, mark.key, value, mark.revision) : notebook?.add(MARKS, value)));
				}
			}
		};
	}

	function cancel() {
		draft = [];
		routeMode = false;
		note = null;
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
			routeMode = false;
		});
	}

	function sendAlong(route: Route, back: boolean) {
		const id = selectedId;
		if (id === null) return;
		void order(async (key) => {
			await sendJourney(masterId, id, { waypoints: [], routeKey: route.key, reverse: back }, key);
			forgetGaveUp(id);
		});
	}

	function stop(b: Bestia) {
		void order((key) => stopJourney(masterId, b.id, key));
	}

	/** A route starts where the bestia it was drafted for stands, so it can be walked back the other way too. */
	function keepRoute() {
		const kept = notebook;
		if (!selected || !kept) return;
		const waypoints = [selected.position, ...draft];
		void order(async () => {
			try {
				await kept.add(ROUTES, routeBody(waypoints, ''));
				draft = [];
				routeMode = false;
			} catch (e) {
				if (e instanceof ApiError && e.status === 400) throw new Error('You keep as many routes as you may. Forget one first.');
				throw e;
			}
		});
	}

	function rename(route: Route, name: string) {
		void order(async () => notebook?.put(ROUTES, route.key, routeBody(route.waypoints, name), route.revision));
	}

	function forget(route: Route) {
		void order(async () => notebook?.remove(ROUTES, route.key, route.revision));
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

	function marker(at: L.LatLngExpression, colour: string, title: string, state: { chosen?: boolean; away?: boolean } = {}): L.Marker {
		const classes = ['pin', state.chosen ? 'chosen' : '', state.away ? 'away' : ''].join(' ').trim();
		return L.marker(at, {
			title,
			icon: L.divIcon({ className: classes, html: `<span style="background:${colour}"></span>`, iconSize: [18, 18], iconAnchor: [9, 9] })
		}).bindTooltip(title, { direction: 'top', offset: [0, -8] });
	}

	/** A drafted stop: in route mode it can be dragged to move it, and a tap takes it away. */
	function draftMarker(at: Position, index: number, colour: string): L.Marker {
		const pin = L.marker(latLng(at), {
			draggable: routeMode,
			icon: L.divIcon({ className: 'stop', html: `<span style="background:${colour}">${index + 1}</span>`, iconSize: [22, 22], iconAnchor: [11, 11] })
		});
		pin.on('dragend', () => {
			const moved = pin.getLatLng();
			draft = draft.map((stop, i) => (i === index ? { eastMetres: moved.lng, northMetres: moved.lat } : stop));
		});
		pin.on('click', (e: L.LeafletMouseEvent) => {
			L.DomEvent.stopPropagation(e);
			if (routeMode) draft = draft.filter((_, i) => i !== index);
		});
		return pin;
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
		<div class="map-tools">
			<form class="search" onsubmit={(e) => { e.preventDefault(); void search(); }}>
				<input type="search" placeholder="Place or grid reference (H12 41 37)" bind:value={query} aria-label="Find a place or grid reference" />
			</form>
			<button type="button" class="chip" class:on={showGrid} onclick={toggleGrid} aria-pressed={showGrid}>Grid</button>
			{#if searchNote}<p class="search-note">{searchNote}</p>{/if}
			{#if found.length > 0}
				<ul class="found">
					{#each found as place (place.id)}
						<li><button type="button" onclick={() => goToPlace(place)}>{place.name} <span class="label">{meta ? formatGridRef(place.x, place.y, meta.worldWidthMetres, meta.worldHeightMetres) : ''}</span></button></li>
					{/each}
				</ul>
			{/if}
		</div>
		{#if readout}<span class="readout label">{readout}</span>{/if}
		{#if card}<MapCard {card} onclose={() => (card = null)} />{/if}
		{#if bestias.length > 0}
			<TravelSheet
				{bestias}
				{routes}
				{selectedId}
				bind:routeMode
				{draft}
				{busy}
				{note}
				onselect={pick}
				onsend={send}
				onclear={cancel}
				onsave={keepRoute}
				onstop={stop}
				onroute={sendAlong}
				onrename={rename}
				ondelete={forget}
			/>
		{/if}
	</div>
	<p class="legend label">Tap the map for what is there · Route: each tap a stop · each way is drawn in its Bestia's colour · fog is ground you have not charted</p>
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

	/* The one a tap on the map sends. */
	.map :global(.pin.chosen span) {
		box-shadow: 0 0 0 2px #fff, 0 0 0 5px var(--brass), 0 2px 8px rgb(0 0 0 / 70%);
	}

	/* One that rests until its master is back: seen where it was left, but cannot be sent. */
	.map :global(.pin.away span) {
		opacity: 0.55;
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

	.map :global(.service span) {
		display: block;
		width: 100%;
		height: 100%;
		border: 2px solid #261c14;
		border-radius: 50%;
		background: #f5c74d;
	}

	.map :global(.service-market span) {
		background: #f08c3d;
		border-radius: 0;
		transform: rotate(45deg) scale(0.8);
	}

	.map :global(.service-inn span) {
		background: #94cc66;
		border-radius: 0;
		clip-path: polygon(50% 0, 100% 40%, 100% 100%, 0 100%, 0 40%);
	}

	.map :global(.service-temple span) {
		background: #f5f2e0;
		border-radius: 0;
		clip-path: polygon(50% 0, 95% 100%, 5% 100%);
	}

	.map :global(.service-guard span) {
		background: #db4d47;
		border-radius: 0 0 50% 50%;
	}

	.map :global(.service-ward_stone span) {
		background: #b894f5;
		border-radius: 30% 30% 10% 10%;
	}

	.map :global(.mark span) {
		display: block;
		width: 100%;
		height: 100%;
		border: 2px solid #1a1410;
		border-radius: 50% 50% 50% 0;
		background: #9fd8ff;
		transform: rotate(-45deg);
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

	.map-tools {
		position: absolute;
		top: 0.5rem;
		left: 3.2rem;
		right: 0.5rem;
		z-index: 600;
		display: flex;
		flex-wrap: wrap;
		gap: 0.4rem;
		align-items: flex-start;
		pointer-events: none;
	}

	.map-tools > * {
		pointer-events: auto;
	}

	.search {
		flex: 1;
		max-width: 22rem;
	}

	.search input {
		width: 100%;
	}

	.map-tools .chip.on {
		color: var(--brass);
		box-shadow: 0 0 0 2px var(--brass);
	}

	.found,
	.search-note {
		flex-basis: 100%;
		max-width: 22rem;
		margin: 0;
		padding: 0.3rem;
		list-style: none;
		background: var(--card);
		border-radius: 0.4rem;
	}

	.found button {
		width: 100%;
		text-align: left;
	}

	.readout {
		position: absolute;
		bottom: 0.4rem;
		left: 0.5rem;
		z-index: 600;
		padding: 0.1rem 0.4rem;
		background: var(--card);
		border-radius: 0.3rem;
		pointer-events: none;
	}

	.map :global(.search-pin span) {
		display: block;
		width: 100%;
		height: 100%;
		border: 3px solid var(--brass);
		border-radius: 50%;
		box-shadow: 0 0 0 2px #1a1410;
	}

	.legend {
		margin: 0;
		padding: 0.4rem var(--gutter);
		text-align: center;
	}

</style>
