<script lang="ts">
	import { onMount } from 'svelte';
	import { getJson } from '$lib/api/client';
	import type { MasterSummary } from '$lib/api/model';
	import Icon from '$lib/components/Icon.svelte';
	import { guarded } from '$lib/guard';
	import { live } from '$lib/live.svelte';

	let masters = $state<MasterSummary[] | null>(null);
	let failure = $state<string | null>(null);

	async function load() {
		try {
			masters = await guarded(() => getJson<MasterSummary[]>('/v1/companion/masters'));
		} catch (e) {
			failure = e instanceof Error ? e.message : String(e);
		}
	}

	onMount(() => {
		live.start();
		void load();
		return live.on((event) => event.type === 'Presence' && void load());
	});
</script>

<main class="page">
	<header class="top">
		<h1>Your Masters</h1>
		<a class="settings" href="/settings" aria-label="Settings"><Icon name="settings" size={22} /></a>
	</header>

	{#if failure}
		<p class="alert">{failure}</p>
	{:else if masters === null}
		<p class="muted">Asking the world…</p>
	{:else if masters.length === 0}
		<section class="panel">
			<p>You have no Masters yet. Create one in the game, and it will appear here.</p>
		</section>
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

	.who {
		flex: 1;
		display: grid;
	}
</style>
