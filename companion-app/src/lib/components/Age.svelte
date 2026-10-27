<script lang="ts">
	import { onMount } from 'svelte';
	import { age } from '#lib/age.ts';

	let { asOf, live = false }: { asOf: number; live?: boolean } = $props();
	let now = $state(Date.now());

	onMount(() => {
		const timer = setInterval(() => (now = Date.now()), 30_000);
		return () => clearInterval(timer);
	});
</script>

{#if live}
	<span class="chip live">in the world</span>
{:else}
	<span class="chip" title={asOf > 0 ? new Date(asOf).toLocaleString() : undefined}>{asOf > 0 ? `as of ${age(asOf, now)}` : age(asOf, now)}</span>
{/if}
