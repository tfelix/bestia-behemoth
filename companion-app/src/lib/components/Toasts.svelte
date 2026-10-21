<script lang="ts">
	import { onMount } from 'svelte';
	import { live, type LiveEvent } from '$lib/live.svelte';
	import { describe } from '$lib/events';

	let toasts = $state<{ id: string; title: string; body: string }[]>([]);

	onMount(() =>
		live.on((event: LiveEvent) => {
			const text = describe(event);
			if (!text) return;
			toasts = [{ id: event.id, ...text }, ...toasts].slice(0, 3);
			setTimeout(() => (toasts = toasts.filter((t) => t.id !== event.id)), 6_000);
		})
	);
</script>

<div class="toasts" aria-live="polite">
	{#each toasts as toast (toast.id)}
		<div class="toast panel rise">
			<strong>{toast.title}</strong>
			<span class="muted">{toast.body}</span>
		</div>
	{/each}
</div>

<style>
	.toasts {
		position: fixed;
		top: calc(env(safe-area-inset-top) + 0.75rem);
		left: 50%;
		z-index: 2000;
		width: min(26rem, calc(100% - 2rem));
		transform: translateX(-50%);
		display: grid;
		gap: 0.5rem;
		pointer-events: none;
	}

	.toast {
		display: grid;
		gap: 0.15rem;
		padding: 0.7rem 0.9rem;
	}
</style>
