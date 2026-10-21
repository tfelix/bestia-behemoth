<script lang="ts">
	import { page } from '$app/state';
	import { getJson } from '$lib/api/client';
	import type { MasterSheet, PartyView } from '$lib/api/model';
	import Age from '$lib/components/Age.svelte';
	import { touchesMaster } from '$lib/events';
	import { guarded } from '$lib/guard';
	import { live } from '$lib/live.svelte';

	const masterId = $derived(Number(page.params.id));
	let sheet = $state<MasterSheet | null>(null);
	let party = $state<PartyView | null>(null);
	let failure = $state<string | null>(null);

	async function load(id: number) {
		try {
			[sheet, party] = await guarded(() =>
				Promise.all([getJson<MasterSheet>(`/v1/companion/masters/${id}`), getJson<PartyView>(`/v1/companion/masters/${id}/party`)])
			);
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

	const stats = $derived(
		sheet
			? [
					['Strength', sheet.stats.strength],
					['Vitality', sheet.stats.vitality],
					['Intelligence', sheet.stats.intelligence],
					['Dexterity', sheet.stats.dexterity],
					['Willpower', sheet.stats.willpower],
					['Agility', sheet.stats.agility]
				]
			: []
	);
</script>

<main class="page">
	{#if failure}
		<p class="alert">{failure}</p>
	{:else if !sheet}
		<p class="muted">Asking the world…</p>
	{:else}
		<section class="panel rise hero">
			<div>
				<h1>{sheet.name}</h1>
				<span class="muted">Level {sheet.level} · lives in {sheet.home || 'no town'}</span>
			</div>
			<Age asOf={sheet.asOf} live={sheet.inWorld} />
		</section>

		<section class="panel rise">
			<div class="points">
				<div><span class="label">Health</span><strong>{sheet.currentHealth ?? '–'}</strong></div>
				<div><span class="label">Experience</span><strong>{sheet.exp}</strong></div>
				<div><span class="label">Skill points</span><strong>{sheet.skillPoints}</strong></div>
				<div><span class="label">Status points</span><strong>{sheet.statusPoints}</strong></div>
			</div>
		</section>

		<section class="panel rise">
			<h2>Attributes</h2>
			<div class="grid-2 stats">
				{#each stats as [name, value] (name)}
					<div class="stat"><span class="label">{name}</span><strong>{value}</strong></div>
				{/each}
			</div>
		</section>

		<section class="panel rise">
			<h2>Party</h2>
			{#if party?.party}
				<p class="muted">{party.party.name}</p>
				<ul class="members">
					{#each party.party.members as member (member.masterId)}
						<li>
							<span>{member.name}{member.masterId === party.party.ownerMasterId ? ' ★' : ''}</span>
							<span class="muted">Lv {member.level}</span>
							{#if member.online}<span class="chip live">online</span>{/if}
						</li>
					{/each}
				</ul>
			{:else}
				<p class="muted">Travelling alone.</p>
			{/if}
		</section>
	{/if}
</main>

<style>
	.hero {
		display: flex;
		justify-content: space-between;
		align-items: flex-start;
		gap: 1rem;
	}

	.points {
		display: grid;
		grid-template-columns: repeat(2, 1fr);
		gap: 0.5rem;
		margin-top: 0.75rem;
	}

	.points div,
	.stat {
		display: grid;
	}

	.points strong,
	.stat strong {
		font-family: var(--display);
		font-size: 1.25rem;
	}

	.stats {
		margin-top: 0.6rem;
	}

	.members {
		list-style: none;
		margin: 0.4rem 0 0;
		padding: 0;
		display: grid;
		gap: 0.35rem;
	}

	.members li {
		display: flex;
		gap: 0.6rem;
		align-items: center;
	}

	.members li span:first-child {
		flex: 1;
	}
</style>
