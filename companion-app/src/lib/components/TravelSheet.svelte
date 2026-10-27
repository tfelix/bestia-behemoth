<script lang="ts">
	import type { Bestia, Position } from '#lib/api/model.ts';
	import { bestiaColor } from '#lib/map/slotColors.ts';
	import { speciesName } from '#lib/names.ts';
	import { distance, distanceText, routeLength, travelStatus, travelTimeText, type Route } from '#lib/travel.ts';

	/**
	 * Where the bestias are going, and the order being drafted for one: the half of the map page a journey is
	 * planned from. With [routeMode] on each tap on the map is one more stop; Go sends the bestia along them.
	 * The page does the asking; this only shows and collects.
	 */
	let {
		bestias,
		routes,
		selectedId,
		routeMode = $bindable(),
		draft,
		busy,
		note,
		onselect,
		onsend,
		onclear,
		onsave,
		onstop,
		onroute,
		onrename,
		ondelete
	}: {
		bestias: Bestia[];
		routes: Route[];
		selectedId: number | null;
		routeMode: boolean;
		draft: Position[];
		busy: boolean;
		note: string | null;
		onselect: (bestia: Bestia) => void;
		onsend: () => void;
		onclear: () => void;
		onsave: () => void;
		onstop: (bestia: Bestia) => void;
		onroute: (route: Route, back: boolean) => void;
		onrename: (route: Route, name: string) => void;
		ondelete: (route: Route) => void;
	} = $props();

	let open = $state(true);
	let renaming = $state<string | null>(null);
	let newName = $state('');

	const selected = $derived(bestias.find((b) => b.id === selectedId) ?? null);
	const canOrder = $derived(selected !== null && selected.inWorld && !busy);
	const length = $derived(selected ? routeLength(selected.position, draft) : 0);

	// A new draft is what the player is looking for: open the sheet to it.
	$effect(() => {
		if (draft.length > 0) open = true;
	});

	function legs(stops: Position[]): string[] {
		return stops.map((stop, i) => {
			const from = i === 0 ? selected?.position : stops[i - 1];
			return from ? distanceText(distance(from, stop)) : '';
		});
	}

	function startRename(route: Route) {
		renaming = route.key;
		newName = route.customName ? route.name : '';
	}

	function finishRename(route: Route) {
		renaming = null;
		onrename(route, newName.trim());
	}
</script>

<section class="sheet panel" class:closed={!open} aria-label="Travel">
	<button class="handle" onclick={() => (open = !open)} aria-expanded={open}>
		<span class="label">Travel</span>
		<span class="muted">{open ? 'Hide' : selected ? (selected.name ?? speciesName(selected.species)) : 'Send a Bestia'}</span>
	</button>

	{#if note}<p class="note">{note}</p>{/if}

	{#if open}
		<div class="chips" role="radiogroup" aria-label="Bestia to send">
			{#each bestias as b, i (b.id)}
				<button
					class="chip who"
					class:chosen={b.id === selectedId}
					class:away={!b.inWorld}
					role="radio"
					aria-checked={b.id === selectedId}
					onclick={() => onselect(b)}
				>
					<span class="dot" style:background={bestiaColor(i)}></span>
					{b.name ?? speciesName(b.species)}
				</button>
			{/each}
		</div>

		{#if selected}
			<p class="status">
				{travelStatus(selected)}
				{#if selected.journey}
					<button class="btn small" disabled={busy} onclick={() => onstop(selected)}>Stop</button>
				{/if}
			</p>

			{#if !selected.inWorld}
				<p class="muted hint">It cannot be sent anywhere until you are back in the world.</p>
			{:else}
				{#if draft.length === 0}
					<p class="muted hint">
						{routeMode ? 'Tap the map to add the first stop.' : 'Tap the map for what is there, or press Route and tap its stops.'}
					</p>
				{:else}
					<div class="plan">
						<p class="summary" title="As the crow flies; the way round obstacles is longer.">
							<strong>About {distanceText(length)}</strong>
							<span>{travelTimeText(length, selected.metresPerSecond)}</span>
						</p>
						{#if draft.length > 1}
							<ol class="stops">
								{#each legs(draft) as leg, i (i)}
									<li>{i === 0 ? leg : `+${leg}`}</li>
								{/each}
							</ol>
						{/if}
					</div>
				{/if}
				<div class="actions">
					<button class="btn primary" disabled={!canOrder || draft.length === 0} onclick={onsend}>Go</button>
					<button class="btn" disabled={draft.length === 0 && !routeMode} onclick={onclear}>Cancel</button>
					<button class="btn" class:on={routeMode} aria-pressed={routeMode} onclick={() => (routeMode = !routeMode)}>
						{routeMode ? 'Planning a route' : 'Route'}
					</button>
					<button class="btn" disabled={!canOrder || draft.length === 0} onclick={onsave}>Save route</button>
				</div>
			{/if}
		{:else}
			<p class="muted hint">Pick a Bestia to send it somewhere.</p>
		{/if}

		<h2 class="label">Routes</h2>
		{#if routes.length === 0}
			<p class="muted hint">None kept yet. Draft stops and keep them as a route.</p>
		{/if}
		<ul class="routes">
			{#each routes as route (route.key)}
				<li>
					{#if renaming === route.key}
						<form class="rename" onsubmit={(e) => (e.preventDefault(), finishRename(route))}>
							<!-- svelte-ignore a11y_autofocus -->
							<input bind:value={newName} placeholder={route.name} maxlength="40" autofocus aria-label="Route name" />
							<button class="btn small" type="submit">Keep</button>
						</form>
					{:else}
						<span class="name" title={route.name}>{route.name}</span>
						<button class="icon" disabled={!canOrder} title="Send along it" onclick={() => onroute(route, false)}>→</button>
						<button class="icon" disabled={!canOrder} title="Send back the other way" onclick={() => onroute(route, true)}>←</button>
						<button class="icon" title="Rename" onclick={() => startRename(route)}>✎</button>
						<button class="icon" title="Forget this route" onclick={() => ondelete(route)}>✕</button>
					{/if}
				</li>
			{/each}
		</ul>
	{/if}
</section>

<style>
	.sheet {
		position: absolute;
		left: 0.5rem;
		right: 0.5rem;
		bottom: 0.5rem;
		z-index: 600;
		max-height: 48%;
		overflow-y: auto;
		padding: 0.5rem 0.75rem 0.75rem;
	}

	.sheet.closed {
		max-height: none;
	}

	.handle {
		display: flex;
		width: 100%;
		justify-content: space-between;
		align-items: center;
		background: none;
		border: none;
		color: inherit;
		padding: 0.25rem 0;
	}

	.chips {
		display: flex;
		gap: 0.4rem;
		overflow-x: auto;
		padding: 0.4rem 0;
	}

	.who {
		display: inline-flex;
		align-items: center;
		gap: 0.35rem;
		white-space: nowrap;
	}

	.who.chosen {
		outline: 2px solid var(--brass);
	}

	.who.away {
		opacity: 0.55;
	}

	.dot {
		width: 0.65rem;
		height: 0.65rem;
		border-radius: 50%;
		border: 1px solid #1a1410;
	}

	.status {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 0.5rem;
		margin: 0.25rem 0;
	}

	.hint {
		margin: 0.25rem 0;
		font-size: 0.85rem;
	}

	.plan {
		margin: 0.25rem 0 0.5rem;
	}

	.summary {
		display: flex;
		justify-content: space-between;
		gap: 0.5rem;
		margin: 0;
	}

	.stops {
		margin: 0.25rem 0;
		padding-left: 1.4rem;
		font-size: 0.9rem;
	}

	.actions {
		display: flex;
		flex-wrap: wrap;
		gap: 0.4rem;
	}

	.btn.on {
		border-color: var(--brass);
		color: var(--brass);
		box-shadow: inset 0 0 0 1px rgb(201 164 92 / 30%);
	}

	.note {
		margin: 0.4rem 0 0.2rem;
		color: var(--warn);
		font-size: 0.85rem;
	}

	h2.label {
		margin: 0.75rem 0 0.25rem;
	}

	.routes {
		list-style: none;
		margin: 0;
		padding: 0;
	}

	.routes li {
		display: flex;
		align-items: center;
		gap: 0.25rem;
		padding: 0.2rem 0;
		border-top: 1px solid var(--edge);
	}

	.name {
		flex: 1;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.icon {
		min-width: 2.25rem;
		min-height: 2.25rem;
		background: none;
		border: 1px solid var(--edge);
		border-radius: var(--radius);
		color: var(--brass);
	}

	.icon:disabled {
		opacity: 0.4;
	}

	.rename {
		display: flex;
		flex: 1;
		gap: 0.4rem;
	}

	.rename input {
		flex: 1;
		min-width: 0;
	}

	.btn.small {
		padding: 0.2rem 0.6rem;
		font-size: 0.85rem;
	}
</style>
