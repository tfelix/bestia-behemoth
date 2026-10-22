<script lang="ts">
	import { emblemUrl } from '$lib/emblems';

	/** [scale] whole times the emblem's 32 pixels: only whole numbers keep its pixels square. */
	let { hash, scale = 2 }: { hash: string; scale?: 1 | 2 | 3 } = $props();

	let url = $state<string | null>(null);

	$effect(() => {
		url = null;
		if (!hash) return;
		let current = true;
		void emblemUrl(hash).then((loaded) => {
			if (current) url = loaded;
		});
		return () => {
			current = false;
		};
	});
</script>

{#if url}
	<img class="emblem" src={url} alt="" width={32 * scale} height={32 * scale} />
{/if}

<style>
	.emblem {
		image-rendering: pixelated;
		flex: none;
	}
</style>
