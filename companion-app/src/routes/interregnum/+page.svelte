<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import type { LastWorld } from '#lib/api/model.ts';
	import { guarded } from '#lib/guard.ts';
	import { landingPath } from '#lib/lastMaster.ts';
	import { endingTitle, gameText, summaryLines, worldState } from '#lib/world.ts';

	let last = $state<LastWorld | null>(null);

	// Nothing tells the app when the new world is ready, so the page keeps asking.
	const CHECK_EVERY_MS = 30_000;

	async function check() {
		try {
			const world = await guarded(worldState);
			if (!world.rebirth) {
				await goto(await landingPath(), { replaceState: true });
				return;
			}
			last = world.last ?? null;
		} catch {
			// The servers may be restarting as well. The next check tries again.
		}
	}

	onMount(() => {
		void check();
		const timer = setInterval(() => void check(), CHECK_EVERY_MS);
		return () => clearInterval(timer);
	});
</script>

<main class="page interregnum">
	<div class="dawn" aria-hidden="true"></div>
	<h1>{gameText('REBIRTH_TITLE')}</h1>
	<p class="muted">{gameText('REBIRTH_TEXT')}</p>

	{#if last}
		<section class="panel rise ended" class:saved={last.ending === 'SAVED'}>
			<span class="label">The world before</span>
			<h2>{endingTitle(last)}</h2>
			{#each summaryLines(last) as line, i (i)}
				<p>{line}</p>
			{/each}
		</section>
	{/if}

	<section class="panel rise keep">
		<span class="label">What crosses over</span>
		<p>Your Masters and all their Bestia, with the gear each one wears. Bags, storage and buildings stay behind.</p>
	</section>

	<p class="muted waiting">This page checks by itself and opens your Masters once the new world is ready.</p>
</main>

<style>
	.interregnum {
		position: relative;
		display: grid;
		gap: 1rem;
		text-align: center;
	}

	.interregnum h1 {
		margin: 2.5rem 0 0;
	}

	.interregnum > p {
		margin: 0;
	}

	/* A pale glow rising behind the title, like light before a dawn. */
	.dawn {
		position: absolute;
		inset: -2rem 0 auto;
		height: 14rem;
		background: radial-gradient(ellipse at 50% 0%, rgba(201, 164, 92, 0.28), transparent 70%);
		animation: breathe 6s ease-in-out infinite;
		pointer-events: none;
	}

	.interregnum .panel + .panel {
		margin-top: 0;
	}

	.ended {
		display: grid;
		gap: 0.35rem;
		border-color: color-mix(in srgb, var(--bad) 45%, var(--edge));
	}

	.ended h2 {
		margin: 0.2rem 0 0.4rem;
		color: var(--bad);
	}

	.ended.saved {
		border-color: color-mix(in srgb, var(--ink) 40%, var(--edge));
	}

	.ended.saved h2 {
		color: var(--ink);
	}

	.ended p,
	.keep p {
		margin: 0;
	}

	.keep {
		display: grid;
		gap: 0.35rem;
	}

	.waiting {
		font-size: 0.9rem;
	}

	@keyframes breathe {
		50% {
			opacity: 0.55;
		}
	}

	@media (prefers-reduced-motion: reduce) {
		.dawn {
			animation: none;
		}
	}
</style>
