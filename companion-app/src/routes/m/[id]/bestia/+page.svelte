<script lang="ts">
	import { page } from '$app/state';
	import { ApiError, getJson, sendJson } from '$lib/api/client';
	import { STANCES, type Bestia, type StanceOrder } from '$lib/api/model';
	import Age from '$lib/components/Age.svelte';
	import { touchesMaster } from '$lib/events';
	import { guarded } from '$lib/guard';
	import { live } from '$lib/live.svelte';
	import { speciesName, STANCE_TEXT } from '$lib/names';
	import { stopJourney, travelStatus } from '$lib/travel';

	type Draft = { stance: string; aggression: number; key: string; sending: boolean; note: string | null };

	const masterId = $derived(Number(page.params.id));
	let bestias = $state<Bestia[] | null>(null);
	let drafts = $state<Record<number, Draft>>({});
	let failure = $state<string | null>(null);

	async function load(id: number) {
		try {
			bestias = await guarded(() => getJson<Bestia[]>(`/v1/companion/masters/${id}/bestias`));
			for (const b of bestias) {
				if (!drafts[b.id]?.sending) {
					const note = drafts[b.id]?.note ?? null;
					drafts[b.id] = { stance: b.stance, aggression: b.aggression, key: crypto.randomUUID(), sending: false, note };
				}
			}
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

	function changed(b: Bestia): boolean {
		const d = drafts[b.id];
		return !!d && (d.stance !== b.stance || d.aggression !== b.aggression);
	}

	let stopping = $state<number | null>(null);

	async function stop(b: Bestia) {
		stopping = b.id;
		try {
			await guarded(() => stopJourney(masterId, b.id, crypto.randomUUID()));
			await load(masterId);
		} catch (e) {
			failure = e instanceof Error ? e.message : String(e);
		} finally {
			stopping = null;
		}
	}

	async function give(b: Bestia) {
		const draft = drafts[b.id];
		draft.sending = true;
		draft.note = null;
		try {
			const stored = await guarded(() =>
				sendJson<StanceOrder>(
					'PUT',
					`/v1/companion/masters/${masterId}/bestias/${b.id}/stance`,
					{ stance: draft.stance, aggression: draft.aggression },
					{ 'Idempotency-Key': draft.key }
				)
			);
			b.stance = stored.stance;
			b.aggression = stored.aggression;
			drafts[b.id] = { ...stored, key: crypto.randomUUID(), sending: false, note: 'Order given.' };
		} catch (e) {
			// On a timeout the key stays: sending again cannot give the order twice.
			draft.sending = false;
			draft.note = e instanceof ApiError && e.status === 504 ? 'No answer yet - send again to make sure.' : String((e as Error).message);
		}
	}
</script>

<main class="page">
	<h1 class="title">Bestia</h1>

	{#if failure}
		<p class="alert">{failure}</p>
	{:else if bestias === null}
		<p class="muted">Asking the world…</p>
	{:else if bestias.length === 0}
		<section class="panel"><p class="muted">This Master has no Bestia yet.</p></section>
	{:else}
		{#each bestias as b (b.id)}
			{@const draft = drafts[b.id]}
			<section class="panel rise">
				<div class="head">
					<div>
						<h2>{b.name ?? speciesName(b.species)}</h2>
						<span class="muted">{speciesName(b.species)} · Level {b.level}</span>
					</div>
					<Age asOf={b.asOf} live={b.inWorld} />
				</div>

				<p class="travel">
					<span class="muted small">{travelStatus(b)}</span>
					{#if b.journey}
						<button class="btn" disabled={stopping === b.id} onclick={() => stop(b)}>Stop</button>
					{:else if b.inWorld}
						<a class="btn" href={`/m/${masterId}/map`}>Send…</a>
					{/if}
				</p>

				{#if draft}
					<div class="label">Standing order</div>
					<div class="stances" role="radiogroup" aria-label="Stance">
						{#each STANCES as stance (stance)}
							<button
								class="stance"
								class:chosen={draft.stance === stance}
								role="radio"
								aria-checked={draft.stance === stance}
								onclick={() => (draft.stance = stance)}>{STANCE_TEXT[stance].name}</button
							>
						{/each}
					</div>
					<p class="muted small">{STANCE_TEXT[draft.stance]?.text}</p>

					<label class="aggression">
						<span class="label">Aggression <strong>{draft.aggression}</strong></span>
						<input type="range" min="0" max="100" step="5" bind:value={draft.aggression} />
					</label>

					<div class="actions">
						<button class="btn primary" disabled={!changed(b) || draft.sending} onclick={() => give(b)}>
							{draft.sending ? 'Sending…' : 'Give the order'}
						</button>
						{#if draft.note}<span class="muted small">{draft.note}</span>{/if}
					</div>
				{/if}
			</section>
		{/each}
	{/if}
</main>

<style>
	.title {
		margin-bottom: 0.8rem;
	}

	.head {
		display: flex;
		justify-content: space-between;
		align-items: flex-start;
		gap: 0.75rem;
		margin-bottom: 0.8rem;
	}

	.travel {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 0.5rem;
		margin: 0 0 0.8rem;
	}

	.stances {
		display: grid;
		grid-template-columns: repeat(4, 1fr);
		gap: 0.35rem;
		margin-top: 0.35rem;
	}

	.stance {
		min-height: 2.5rem;
		border: 1px solid var(--edge);
		border-radius: 0.45rem;
		background: #171310;
		font-family: var(--label);
		font-weight: 600;
		font-size: 0.8rem;
		color: var(--muted);
		cursor: pointer;
	}

	.stance.chosen {
		border-color: var(--brass);
		background: linear-gradient(180deg, #3a2f22, #241d16);
		color: var(--brass);
		box-shadow: inset 0 0 0 1px rgb(201 164 92 / 30%);
	}

	.small {
		font-size: 0.85rem;
	}

	.aggression {
		display: grid;
		gap: 0.3rem;
		margin: 0.6rem 0 0.9rem;
	}

	.aggression input {
		accent-color: var(--accent);
	}

	.actions {
		display: flex;
		align-items: center;
		gap: 0.8rem;
	}
</style>
