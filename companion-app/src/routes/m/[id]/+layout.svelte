<script lang="ts">
	import { onMount } from 'svelte';
	import { page } from '$app/state';
	import TabBar from '#lib/components/TabBar.svelte';
	import { rememberMaster } from '#lib/lastMaster.ts';
	import { live } from '#lib/live.svelte.ts';
	// Imported for its listener: the chat log has to be listening before the socket replays what it missed.
	import '#lib/chat.svelte.ts';

	let { children } = $props();
	const masterId = $derived(Number(page.params.id));

	onMount(() => live.start());

	// The next start of the app, or sign-in, opens this Master again.
	$effect(() => rememberMaster(masterId));
</script>

<header class="bar">
	<a href="/masters" class="back" aria-label="All masters">‹ Masters</a>
	<span class="chip" class:live={live.connected}>{live.connected ? 'listening' : 'offline'}</span>
</header>
{@render children()}
<TabBar {masterId} />

<style>
	.bar {
		display: flex;
		justify-content: space-between;
		align-items: center;
		max-width: 40rem;
		margin: 0 auto;
		padding: 0.75rem var(--gutter) 0;
	}

	.back {
		font-family: var(--label);
		font-weight: 600;
		color: var(--muted);
	}
</style>
