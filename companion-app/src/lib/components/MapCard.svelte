<script lang="ts" module>
	/** What a tap on the map opens: what is there, and what can be done about it. [ask] turns it into a name prompt. */
	export type CardAction = { label: string; run: () => void };
	export type Card = {
		title: string;
		lines: string[];
		actions: CardAction[];
		ask?: { placeholder: string; value: string; submit: (text: string) => void };
	};
</script>

<script lang="ts">
	let { card, onclose }: { card: Card; onclose: () => void } = $props();

	let text = $state('');

	// Each new card starts from what it asks with, not from what the last one was typed into.
	$effect(() => {
		text = card.ask?.value ?? '';
	});

	function run(action: CardAction) {
		onclose();
		action.run();
	}

	function submit(event: SubmitEvent) {
		event.preventDefault();
		const entered = text.trim();
		if (!card.ask || entered === '') return;
		const ask = card.ask;
		onclose();
		ask.submit(entered);
	}
</script>

<div class="card panel" role="dialog" aria-label={card.title}>
	<button class="close icon" onclick={onclose} aria-label="Close">✕</button>
	<h2>{card.title}</h2>
	{#each card.lines.filter((line) => line !== '') as line, i (i)}
		<p class="muted">{line}</p>
	{/each}
	{#if card.ask}
		<form onsubmit={submit}>
			<!-- svelte-ignore a11y_autofocus -->
			<input bind:value={text} placeholder={card.ask.placeholder} maxlength="32" autofocus />
			<button class="btn primary" type="submit">Keep</button>
		</form>
	{:else}
		<div class="actions">
			{#each card.actions as action, i (i)}
				<button class="btn" class:primary={i === 0} onclick={() => run(action)}>{action.label}</button>
			{/each}
		</div>
	{/if}
</div>

<style>
	.card {
		position: absolute;
		left: 50%;
		top: 3.4rem;
		z-index: 700;
		transform: translateX(-50%);
		width: min(22rem, calc(100% - 1rem));
		padding: 0.6rem 0.8rem;
	}

	h2 {
		margin: 0 1.5rem 0.2rem 0;
		font-size: 1.05rem;
		color: var(--brass);
	}

	p {
		margin: 0.1rem 0;
	}

	.close {
		position: absolute;
		top: 0.3rem;
		right: 0.3rem;
	}

	.actions,
	form {
		display: flex;
		flex-wrap: wrap;
		gap: 0.4rem;
		margin-top: 0.5rem;
	}

	form input {
		flex: 1;
	}
</style>
