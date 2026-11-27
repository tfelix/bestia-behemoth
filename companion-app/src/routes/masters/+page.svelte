<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { getJson } from '#lib/api/client.ts';
	import type { CurrentWorld, MasterSummary } from '#lib/api/model.ts';
	import Icon from '#lib/components/Icon.svelte';
	import TabBar from '#lib/components/TabBar.svelte';
	import { guarded } from '#lib/guard.ts';
	import { live } from '#lib/live.svelte.ts';
	import { doomOf, worldState } from '#lib/world.ts';

	let masters = $state<MasterSummary[] | null>(null);
	let world = $state<CurrentWorld | null>(null);
	const doom = $derived(world ? doomOf(world) : null);
	let failure = $state<string | null>(null);

	// Masters are only created in the game, so a new account starts there.
	const GAME_CLIENT_URL = 'https://github.com/tfelix/bestia-behemoth/releases';

	async function load() {
		try {
			masters = await guarded(() => getJson<MasterSummary[]>('/v1/companion/masters'));
		} catch (e) {
			failure = e instanceof Error ? e.message : String(e);
		}
	}

	// The list works without it, so a failure only hides the card.
	async function loadWorld() {
		try {
			const state = await worldState();
			if (state.rebirth) await goto('/interregnum');
			world = state.current ?? null;
		} catch {
			world = null;
		}
	}

	onMount(() => {
		live.start();
		void loadWorld();
		void load();
		return live.on((event) => event.type === 'Presence' && void load());
	});
</script>

<main class="page">
	<header class="top">
		<h1>Your Masters</h1>
		<a class="settings" href="/settings" aria-label="Settings"><Icon name="settings" size={22} /></a>
	</header>

	{#if world}
		<section class="panel rise world">
			<span class="label">The world</span>
			<h2>{world.name} <span class="muted day">Day {world.ageDays + 1}</span></h2>
			{#if doom}
				<p><span class="chip doom">{doom.stage}</span> {doom.threat}</p>
				<p class="muted">{doom.line}</p>
			{:else}
				<p class="muted">No shadow lies on the world yet.</p>
			{/if}
		</section>
	{/if}

	{#if failure}
		<p class="alert">{failure}</p>
	{:else if masters === null}
		<p class="muted">Asking the world…</p>
	{:else if masters.length === 0}
		<section class="panel rise welcome">
			<h2>Welcome to Bestia</h2>
			<p>
				This app looks in on your Masters. You have none yet, and a Master can only be created in the game. Before you
				continue:
			</p>
			<ol>
				<li>Get the game client for your computer.</li>
				<li>Sign in there with this same account.</li>
				<li>Create your first Master.</li>
			</ol>
			<a class="btn primary" href={GAME_CLIENT_URL} target="_blank" rel="noopener">Get the game client</a>
			<p class="muted">Your Master appears here as soon as it enters the world. The tabs below open then.</p>
		</section>
		<TabBar masterId={null} />
	{:else}
		{#each masters as master, i (master.id)}
			<a class="panel master rise" href="/m/{master.id}" style:animation-delay="{i * 60}ms">
				<div class="slot">{master.slot + 1}</div>
				<div class="who">
					<h2>{master.name}</h2>
					<span class="muted">Level {master.level} · {master.home || 'no home'}</span>
				</div>
				{#if master.inWorld}<span class="chip live">in the world</span>{/if}
			</a>
		{/each}
	{/if}
</main>

<style>
	.top {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 1rem;
	}

	.settings {
		color: var(--muted);
	}

	.world {
		display: grid;
		gap: 0.3rem;
		margin-bottom: 0.85rem;
	}

	.world h2,
	.world p {
		margin: 0;
	}

	.day {
		font-family: var(--label);
		font-size: 0.8rem;
		font-weight: 600;
	}

	.chip.doom {
		border-color: color-mix(in srgb, var(--bad) 60%, transparent);
		color: var(--bad);
	}

	.master {
		display: flex;
		align-items: center;
		gap: 0.9rem;
	}

	.slot {
		display: grid;
		place-items: center;
		width: 2.6rem;
		height: 2.6rem;
		border: 2px solid var(--brass-dim);
		border-radius: 50%;
		background: radial-gradient(circle at 35% 30%, #3a2f27, #1a1512);
		font-family: var(--display);
		font-weight: 700;
		color: var(--brass);
	}

	.welcome {
		display: grid;
		gap: 0.75rem;
	}

	.welcome h2,
	.welcome p,
	.welcome ol {
		margin: 0;
	}

	.welcome ol {
		display: grid;
		gap: 0.35rem;
		padding-left: 1.4rem;
	}

	.who {
		flex: 1;
		display: grid;
	}
</style>
