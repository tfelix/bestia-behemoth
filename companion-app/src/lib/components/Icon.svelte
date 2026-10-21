<script lang="ts">
	// The client's Tabler icons, recoloured to follow the surrounding text.
	const sources = import.meta.glob('$lib/icons/*.svg', { query: '?raw', import: 'default', eager: true }) as Record<string, string>;
	const icons = Object.fromEntries(
		Object.entries(sources).map(([path, svg]) => [
			path.split('/').pop()!.replace('.svg', ''),
			svg.replace(/stroke="#ffffff"/g, 'stroke="currentColor"').replace(/width="24"|height="24"/g, '')
		])
	);

	let { name, size = 20 }: { name: string; size?: number } = $props();
</script>

<span class="icon" style:width="{size}px" style:height="{size}px" aria-hidden="true">{@html icons[name] ?? ''}</span>

<style>
	.icon {
		display: inline-flex;
		flex: none;
	}

	.icon :global(svg) {
		width: 100%;
		height: 100%;
	}
</style>
