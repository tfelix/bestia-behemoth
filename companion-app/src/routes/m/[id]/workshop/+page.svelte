<script lang="ts">
	import { page } from '$app/state';
	import { PAUSE_TEXT, stationName } from '#lib/activity.ts';
	import { ApiError, getJson, sendJson } from '#lib/api/client.ts';
	import type { Bestia, QueueOrder, StationView, WorkerOrder, Workshop } from '#lib/api/model.ts';
	import { touchesMaster } from '#lib/events.ts';
	import { guarded } from '#lib/guard.ts';
	import { live } from '#lib/live.svelte.ts';
	import { itemName, speciesName } from '#lib/names.ts';

	/** A recipe picked to queue at a station, and how many. */
	type Pick = { recipeId: number | null; amount: number };

	const masterId = $derived(Number(page.params.id));
	const path = $derived(`/v1/companion/masters/${masterId}/workshop`);
	let workshop = $state<Workshop | null>(null);
	let bestias = $state<Bestia[]>([]);
	let picks = $state<Record<number, Pick>>({});
	let failure = $state<string | null>(null);
	let note = $state<Record<number, string>>({});
	let busy = $state<number | null>(null);

	async function load(id: number) {
		try {
			[workshop, bestias] = await guarded(() =>
				Promise.all([
					getJson<Workshop>(`/v1/companion/masters/${id}/workshop`),
					getJson<Bestia[]>(`/v1/companion/masters/${id}/bestias`)
				])
			);
			for (const station of workshop.stations) picks[station.id] ??= { recipeId: null, amount: 1 };
			failure = null;
		} catch (e) {
			failure = e instanceof Error ? e.message : String(e);
		}
	}

	$effect(() => {
		const id = masterId;
		void load(id);
		return live.on((event) => touchesMaster(event, id) && void load(id));
	});

	/** Every write answers with the whole workshop. */
	async function write(station: StationView, method: string, suffix: string, body: unknown) {
		busy = station.id;
		note[station.id] = '';
		try {
			workshop = await guarded(() =>
				sendJson<Workshop>(method, `${path}/${station.id}${suffix}`, body, { 'Idempotency-Key': crypto.randomUUID() })
			);
		} catch (e) {
			note[station.id] = e instanceof ApiError && e.status === 400 ? 'The station refused that.' : String((e as Error).message);
		} finally {
			busy = null;
		}
	}

	function queue(station: StationView) {
		const pick = picks[station.id];
		if (pick.recipeId == null) return;
		const order: QueueOrder = { recipeId: pick.recipeId, amount: pick.amount };
		void write(station, 'POST', '/queue', order);
	}

	function workerValue(station: StationView): string {
		if (station.worker === 'BESTIA') return String(station.workerBestiaId);
		return station.worker === 'ANY_FREE_CRAFTER' ? 'any' : 'owner';
	}

	function setWorker(station: StationView, value: string) {
		const order: WorkerOrder =
			value === 'any' ? { anyFreeCrafter: true } : value === 'owner' ? { anyFreeCrafter: false } : { playerBestiaId: Number(value), anyFreeCrafter: false };
		void write(station, 'PUT', '/worker', order);
	}

	function bestiaName(b: Bestia): string {
		return b.name ?? speciesName(b.species);
	}
</script>

<main class="page">
	<h1 class="title">Workshop</h1>

	{#if failure}
		<p class="alert">{failure}</p>
	{:else if workshop === null}
		<p class="muted">Asking the world…</p>
	{:else if workshop.stations.length === 0}
		<section class="panel"><p class="muted">This Master has built no crafting station yet.</p></section>
	{:else}
		{#each workshop.stations as station (station.id)}
			{@const pick = picks[station.id]}
			<section class="panel rise">
				<h2>{stationName(station.kind)}</h2>
				{#if station.pause}<p class="muted small">Paused: {PAUSE_TEXT[station.pause] ?? station.pause.toLowerCase()}</p>{/if}

				<label class="field">
					<span class="label">Worked by</span>
					<select value={workerValue(station)} disabled={busy === station.id} onchange={(e) => setWorker(station, e.currentTarget.value)}>
						<option value="owner">You, when you are there</option>
						<option value="any">Any free crafter</option>
						{#each bestias as b (b.id)}<option value={String(b.id)}>{bestiaName(b)}</option>{/each}
					</select>
				</label>

				<div class="label">Queue</div>
				{#if station.queue.length === 0}
					<p class="muted small">Nothing queued.</p>
				{/if}
				{#each station.queue as line, i (i)}
					<div class="line">
						<span>{line.item ? itemName(line.item) : `Recipe ${line.recipeId}`}</span>
						<span class="muted small">{line.done}/{line.amount}</span>
						<span class="moves">
							<button class="btn small" aria-label="Move up" disabled={i === 0 || busy === station.id} onclick={() => write(station, 'PUT', `/queue/${i}/position`, { to: i - 1 })}>↑</button>
							<button
								class="btn small"
								aria-label="Move down"
								disabled={i === station.queue.length - 1 || busy === station.id}
								onclick={() => write(station, 'PUT', `/queue/${i}/position`, { to: i + 1 })}>↓</button
							>
							<button class="btn small" aria-label="Cancel" disabled={busy === station.id} onclick={() => write(station, 'DELETE', `/queue/${i}`, undefined)}>×</button>
						</span>
					</div>
				{/each}

				{#if !station.hasChest}
					<p class="muted small">No chest beside it. From afar a station can only be paid from a chest next to it.</p>
				{:else if station.affordable.length === 0}
					<p class="muted small">The chest beside it holds nothing it can make.</p>
				{:else if pick}
					<div class="add">
						<select bind:value={pick.recipeId}>
							<option value={null}>Add to queue…</option>
							{#each station.affordable as recipe (recipe.recipeId)}
								<option value={recipe.recipeId}>{itemName(recipe.item)} (up to {recipe.most})</option>
							{/each}
						</select>
						<input class="amount" type="number" min="1" max="99" bind:value={pick.amount} aria-label="How many" />
						<button class="btn primary" disabled={pick.recipeId == null || busy === station.id} onclick={() => queue(station)}>Queue</button>
					</div>
				{/if}
				{#if note[station.id]}<p class="muted small">{note[station.id]}</p>{/if}
			</section>
		{/each}
	{/if}
</main>

<style>
	.title {
		margin-bottom: 0.8rem;
	}

	select,
	input {
		min-height: 2.25rem;
		padding: 0 0.6rem;
		border: 1px solid var(--edge);
		border-radius: 0.45rem;
		background: #171310;
		color: var(--ink);
		font: inherit;
	}

	.field {
		display: grid;
		gap: 0.3rem;
		margin: 0.6rem 0 0.8rem;
	}

	.line,
	.add {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: 0.5rem;
		margin: 0.35rem 0;
	}

	.line > span:first-child {
		flex: 1;
	}

	.moves {
		display: flex;
		gap: 0.25rem;
	}

	.amount {
		width: 4.5rem;
	}

	.small {
		font-size: 0.85rem;
	}
</style>
