<script lang="ts">
	let { label, value, max, tone = 'accent' }: { label: string; value: number; max: number; tone?: 'accent' | 'good' } = $props();
	const fill = $derived(max > 0 ? Math.min(100, Math.max(0, (value / max) * 100)) : 0);
</script>

<div class="bar">
	<div class="head">
		<span class="label">{label}</span>
		<span class="value">{value}{#if max > 0}<span class="muted"> / {max}</span>{/if}</span>
	</div>
	<div class="track"><div class="fill {tone}" style:width="{fill}%"></div></div>
</div>

<style>
	.head {
		display: flex;
		justify-content: space-between;
		align-items: baseline;
		margin-bottom: 0.3rem;
	}

	.value {
		font-family: var(--label);
		font-weight: 600;
	}

	.track {
		height: 0.55rem;
		border: 1px solid var(--edge);
		border-radius: 999px;
		background: #120f0d;
		overflow: hidden;
	}

	.fill {
		height: 100%;
		border-radius: 999px;
		transition: width 400ms ease-out;
	}

	.fill.accent {
		background: linear-gradient(90deg, #8e4a22, var(--accent));
	}

	.fill.good {
		background: linear-gradient(90deg, #4f7a35, var(--good));
	}
</style>
