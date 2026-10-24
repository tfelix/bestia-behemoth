<script lang="ts">
	import { page } from '$app/state';
	import { getJson } from '$lib/api/client';
	import type { Bestia, Holder } from '$lib/api/model';
	import Icon from '$lib/components/Icon.svelte';
	import { guarded } from '$lib/guard';
	import { itemDescription, itemName, speciesName } from '$lib/names';

	const masterId = $derived(Number(page.params.id));
	let holders = $state<Holder[] | null>(null);
	/** The species of each Bestia by id, for the one that has no name of its own. */
	let species = $state<Record<number, string>>({});
	let failure = $state<string | null>(null);
	let open = $state<string | null>(null);

	$effect(() => {
		const id = masterId;
		guarded(() => Promise.all([getJson<Holder[]>(`/v1/companion/masters/${id}/inventory`), getJson<Bestia[]>(`/v1/companion/masters/${id}/bestias`)]))
			.then(([h, roster]) => {
				species = Object.fromEntries(roster.map((b) => [b.id, speciesName(b.species)]));
				holders = h;
			})
			.catch((e) => (failure = e instanceof Error ? e.message : String(e)));
	});

	function gold(holder: Holder): number {
		return holder.slots.filter((s) => s.item === 'gold_coin').reduce((sum, s) => sum + s.amount, 0);
	}
</script>

<main class="page">
	<h1 class="title">Bags</h1>

	{#if failure}
		<p class="alert">{failure}</p>
	{:else if holders === null}
		<p class="muted">Asking the world…</p>
	{:else}
		{#each holders as holder (holder.kind + holder.id)}
			<section class="panel rise">
				<div class="head">
					<h2>{holder.kind === 'MASTER' ? 'Carried' : (holder.name ?? species[holder.id] ?? 'Bestia')}</h2>
					<span class="coin"><Icon name="coin" size={18} /> {gold(holder)}</span>
				</div>
				{#if holder.slots.length === 0}
					<p class="muted">Empty.</p>
				{:else}
					<ul>
						{#each holder.slots.filter((s) => s.item !== 'gold_coin') as slot, i (i)}
							{@const id = `${holder.id}-${i}`}
							<li>
								<button class="row" onclick={() => (open = open === id ? null : id)} aria-expanded={open === id}>
									<span class="name">
										{itemName(slot.item)}{slot.upgradeLevel > 0 ? ` +${slot.upgradeLevel}` : ''}
										{#if slot.equippedIn}<span class="chip">worn</span>{/if}
									</span>
									<span class="amount">{slot.amount > 1 ? `×${slot.amount}` : ''}</span>
								</button>
								{#if open === id}
									<div class="detail muted">
										{#if slot.maxDurability > 0}<p>Durability {slot.durability} / {slot.maxDurability}</p>{/if}
										<p>{itemDescription(slot.item) ?? ''}</p>
									</div>
								{/if}
							</li>
						{/each}
					</ul>
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
		align-items: center;
		margin-bottom: 0.4rem;
	}

	.coin {
		display: inline-flex;
		align-items: center;
		gap: 0.3rem;
		font-family: var(--label);
		font-weight: 600;
		color: var(--warn);
	}

	ul {
		list-style: none;
		margin: 0;
		padding: 0;
	}

	li + li {
		border-top: 1px solid rgb(58 50 44 / 60%);
	}

	.row {
		display: flex;
		width: 100%;
		justify-content: space-between;
		align-items: center;
		padding: 0.55rem 0;
		border: 0;
		background: none;
		text-align: left;
		cursor: pointer;
	}

	.name {
		display: inline-flex;
		align-items: center;
		gap: 0.5rem;
	}

	.amount {
		font-family: var(--label);
		font-weight: 600;
		color: var(--muted);
	}

	.detail {
		padding: 0 0 0.6rem;
		white-space: pre-line;
		font-size: 0.9rem;
	}

	.detail p {
		margin: 0.2rem 0;
	}

</style>
