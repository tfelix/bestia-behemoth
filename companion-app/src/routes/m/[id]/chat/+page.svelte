<script lang="ts">
	import { page } from '$app/state';
	import { age } from '$lib/age';
	import { ApiError, sendJson } from '$lib/api/client';
	import type { ChatOrder } from '$lib/api/model';
	import { chat } from '$lib/chat.svelte';
	import { MAX_TEXT_LENGTH, refusalText, type Channel } from '$lib/chatLog';
	import { guarded } from '$lib/guard';
	import { live } from '$lib/live.svelte';

	const CHANNELS: { value: Channel; name: string }[] = [
		{ value: 'PARTY', name: 'Party' },
		{ value: 'GUILD', name: 'Guild' }
	];

	const masterId = $derived(Number(page.params.id));
	const lines = $derived(chat.lines(masterId));

	let channel = $state<Channel>('PARTY');
	let text = $state('');
	// Kept until the line is surely sent or surely refused, so sending again after a timeout cannot say it twice.
	let key = crypto.randomUUID();
	let sending = $state(false);
	let note = $state<string | null>(null);
	let list = $state<HTMLElement | null>(null);
	let now = $state(Date.now());

	$effect(() => chat.open(masterId));

	$effect(() => {
		const timer = setInterval(() => (now = Date.now()), 30_000);
		return () => clearInterval(timer);
	});

	// Follow the newest line.
	$effect(() => {
		void lines.length;
		if (list) list.scrollTop = list.scrollHeight;
	});

	async function send(e: SubmitEvent) {
		e.preventDefault();
		const line = text.trim();
		if (!line || sending) return;

		sending = true;
		note = null;
		try {
			const order: ChatOrder = { channel, text: line };
			await guarded(() => sendJson<void>('POST', `/v1/companion/masters/${masterId}/chat`, order, { 'Idempotency-Key': key }));
			text = '';
			key = crypto.randomUUID();
		} catch (e) {
			if (e instanceof ApiError && e.status === 504) {
				note = 'No answer yet - send again to make sure.';
			} else {
				key = crypto.randomUUID();
				note = (e instanceof ApiError && refusalText(e.status, channel)) || String((e as Error).message);
			}
		} finally {
			sending = false;
		}
	}
</script>

<main class="page">
	<h1 class="title">Chat</h1>

	<section class="panel log" bind:this={list} aria-live="polite">
		{#if lines.length === 0}
			<p class="muted">
				Nothing heard yet. What your party and guild say from now on arrives here{live.connected ? '' : ' once the app is listening'}.
			</p>
		{:else}
			{#each lines as line (line.id)}
				<p class="line {line.channel.toLowerCase()}">
					<span class="tag">[{line.channel === 'PARTY' ? 'Party' : 'Guild'}]</span>
					<strong>{line.senderName}:</strong>
					{line.text}
					<span class="when" title={new Date(line.sentAt).toLocaleString()}>{age(line.sentAt, now)}</span>
				</p>
			{/each}
		{/if}
	</section>

	<form class="panel composer" onsubmit={send}>
		<div class="channels" role="radiogroup" aria-label="Say to">
			{#each CHANNELS as c (c.value)}
				<button
					type="button"
					class="channel {c.value.toLowerCase()}"
					class:chosen={channel === c.value}
					role="radio"
					aria-checked={channel === c.value}
					onclick={() => (channel = c.value)}>{c.name}</button
				>
			{/each}
		</div>
		<div class="say">
			<input
				bind:value={text}
				maxlength={MAX_TEXT_LENGTH}
				placeholder={channel === 'PARTY' ? 'Say to your party' : 'Say to your guild'}
				aria-label="Message"
				enterkeyhint="send"
			/>
			<button class="btn primary" disabled={sending || !text.trim()}>{sending ? 'Sending…' : 'Send'}</button>
		</div>
		{#if note}<p class="muted small">{note}</p>{/if}
	</form>
</main>

<style>
	.title {
		margin-bottom: 0.8rem;
	}

	.log {
		max-height: 55dvh;
		overflow-y: auto;
	}

	.line {
		margin: 0 0 0.45rem;
		line-height: 1.4;
		overflow-wrap: anywhere;
	}

	/* The game client's colours for the same lines. */
	.party .tag,
	.party strong {
		color: rgb(140 224 140);
	}

	.guild .tag,
	.guild strong {
		color: rgb(204 168 255);
	}

	.when {
		margin-left: 0.35rem;
		font-size: 0.75rem;
		color: var(--muted);
	}

	.channels {
		display: grid;
		grid-template-columns: repeat(2, 1fr);
		gap: 0.35rem;
		margin-bottom: 0.6rem;
	}

	.channel {
		min-height: 2.5rem;
		border: 1px solid var(--edge);
		border-radius: 0.45rem;
		background: #171310;
		font-family: var(--label);
		font-weight: 600;
		font-size: 0.8rem;
		color: var(--muted);
		cursor: pointer;
	}

	.channel.chosen {
		border-color: var(--brass);
		background: linear-gradient(180deg, #3a2f22, #241d16);
		color: var(--brass);
		box-shadow: inset 0 0 0 1px rgb(201 164 92 / 30%);
	}

	.say {
		display: flex;
		gap: 0.5rem;
	}

	.say input {
		flex: 1;
		min-width: 0;
		min-height: 2.75rem;
		padding: 0 0.75rem;
		border: 1px solid var(--edge);
		border-radius: 0.5rem;
		background: #171310;
		color: var(--ink);
		font: inherit;
	}

	.small {
		margin: 0.5rem 0 0;
		font-size: 0.85rem;
	}
</style>
