<script lang="ts">
	import { page } from '$app/state';
	import { getJson } from '#lib/api/client.ts';
	import type { GuildView, MasterSheet, PartyView } from '#lib/api/model.ts';
	import Age from '#lib/components/Age.svelte';
	import Emblem from '#lib/components/Emblem.svelte';
	import { touchesMaster } from '#lib/events.ts';
	import { guarded } from '#lib/guard.ts';
	import { live } from '#lib/live.svelte.ts';

	const masterId = $derived(Number(page.params.id));
	let sheet = $state<MasterSheet | null>(null);
	let party = $state<PartyView | null>(null);
	let guild = $state<GuildView | null>(null);
	let failure = $state<string | null>(null);

	async function load(id: number) {
		try {
			[sheet, party, guild] = await guarded(() =>
				Promise.all([
					getJson<MasterSheet>(`/v1/companion/masters/${id}`),
					getJson<PartyView>(`/v1/companion/masters/${id}/party`),
					getJson<GuildView>(`/v1/companion/masters/${id}/guild`)
				])
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

	/** As the game's guild window words it: minutes, then hours, then days. */
	function seenAgo(epochMillis: number): string {
		if (epochMillis <= 0) return '';
		const minutes = Math.max(0, Math.floor((Date.now() - epochMillis) / 60_000));
		if (minutes < 1) return 'just now';
		if (minutes < 60) return `${minutes} min ago`;
		if (minutes < 60 * 24) return `${Math.floor(minutes / 60)} h ago`;
		return `${Math.floor(minutes / (60 * 24))} d ago`;
	}

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

		<section class="panel rise">
			<h2>Guild</h2>
			{#if guild?.guild}
				<div class="guild-head">
					<Emblem hash={guild.guild.emblemHash} />
					<div>
						<strong>{guild.guild.name}</strong>
						<span class="muted">Level {guild.guild.level}</span>
					</div>
				</div>
				{#if guild.guild.motdTitle || guild.guild.motdBody}
					<blockquote class="motd">
						{#if guild.guild.motdTitle}<strong>{guild.guild.motdTitle}</strong>{/if}
						{#if guild.guild.motdBody}<p>{guild.guild.motdBody}</p>{/if}
					</blockquote>
				{/if}
				<ul class="members">
					{#each guild.guild.members as member (member.masterId)}
						<li>
							<span>{member.name}{member.isLeader ? ' ★' : ''}</span>
							<span class="muted">{member.rankTitle} · Lv {member.level}</span>
							{#if member.online}
								<span class="chip live">online</span>
							{:else}
								<span class="muted">{[member.lastAreaName, seenAgo(member.lastSeen)].filter(Boolean).join(' · ')}</span>
							{/if}
						</li>
					{/each}
				</ul>
			{:else}
				<p class="muted">In no guild.</p>
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

	.guild-head {
		display: flex;
		gap: 0.75rem;
		align-items: center;
		margin-top: 0.4rem;
	}

	.guild-head div {
		display: grid;
	}

	.motd {
		margin: 0.6rem 0;
		padding: 0.5rem 0.75rem;
		border-left: 3px solid var(--muted);
	}

	.motd p {
		margin: 0.25rem 0 0;
		white-space: pre-line;
	}
</style>
