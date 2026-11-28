<script lang="ts">
	import { getJson } from '#lib/api/client.ts';
	import type { AwayReport } from '#lib/api/model.ts';
	import { touchesMaster } from '#lib/events.ts';
	import { live } from '#lib/live.svelte.ts';
	import { eventful, reportText } from '#lib/report.ts';

	let { masterId }: { masterId: number } = $props();

	const GO_ON = 'To go on:';

	let report = $state<AwayReport | null>(null);

	/** Quiet on failure: the rest of the page does not depend on it. */
	async function load(id: number) {
		try {
			report = await getJson<AwayReport>(`/v1/companion/masters/${id}/away-report`);
		} catch {
			report = null;
		}
	}

	$effect(() => {
		const id = masterId;
		void load(id);
		return live.on((event) => touchesMaster(event, id) && void load(id));
	});

	// The game shows the report at login, so it is gone once the master plays again.
	const told = $derived(report && !report.masterInWorld ? report.bestias.filter(eventful) : []);
</script>

{#if told.length > 0}
	<section class="panel rise">
		<h2>While you were away</h2>
		{#each told as bestia (bestia.playerBestiaId)}
			{@const sentences = reportText(bestia)}
			<p class="bestia">
				{#each sentences as sentence, i (i)}
					{#if sentence.startsWith(GO_ON)}<strong class="go-on">{GO_ON}</strong>{sentence.slice(GO_ON.length)}{:else}{sentence}{/if}{' '}
				{/each}
			</p>
		{/each}
	</section>
{/if}

<style>
	.bestia {
		margin: 0.5rem 0 0;
		line-height: 1.45;
	}

	.go-on {
		color: var(--accent);
	}
</style>
