<script lang="ts">
	import { page } from '$app/state';
	import { age } from '#lib/age.ts';
	import { ApiError, getJson, sendJson } from '#lib/api/client.ts';
	import type { ChatOrder, GuildView, PartyView, WhisperOrder } from '#lib/api/model.ts';
	import { chat } from '#lib/chat.svelte.ts';
	import {
		closedReason,
		MAX_TEXT_LENGTH,
		refusalText,
		tagOf,
		unavailableText,
		whisperPartner,
		type Channel,
		type ChatLine,
		type Membership
	} from '#lib/chatLog.ts';
	import { touchesMaster } from '#lib/events.ts';
	import { guarded } from '#lib/guard.ts';
	import { live } from '#lib/live.svelte.ts';

	const CHANNELS: { value: Channel; name: string }[] = [
		{ value: 'PARTY', name: 'Party' },
		{ value: 'GUILD', name: 'Guild' },
		{ value: 'WHISPER', name: 'Whisper' }
	];

	/** The longest master name there is. */
	const MAX_NAME_LENGTH = 24;

	const PLACEHOLDERS: Record<Channel, string> = {
		PARTY: 'Say to your party',
		GUILD: 'Say to your guild',
		WHISPER: 'Whisper'
	};

	const masterId = $derived(Number(page.params.id));
	const lines = $derived(chat.lines(masterId));

	let channel = $state<Channel>('PARTY');
	// Kept after a whisper goes out, so the next line goes to the same player.
	let whisperTo = $state('');
	let text = $state('');
	// Kept until the line is surely sent or surely refused, so sending again after a timeout cannot say it twice.
	let key = crypto.randomUUID();
	let sending = $state(false);
	let note = $state<string | null>(null);
	let list = $state<HTMLElement | null>(null);
	let now = $state(Date.now());

	let membership = $state<Membership>({ party: null, guild: null });
	const closed = $derived({
		PARTY: closedReason('PARTY', membership),
		GUILD: closedReason('GUILD', membership),
		WHISPER: closedReason('WHISPER', membership)
	});
	const missingTarget = $derived(channel === 'WHISPER' && !whisperTo.trim());

	async function loadMembership(id: number) {
		try {
			const [party, guild] = await guarded(() =>
				Promise.all([getJson<PartyView>(`/v1/companion/masters/${id}/party`), getJson<GuildView>(`/v1/companion/masters/${id}/guild`)])
			);
			if (id === masterId) membership = { party: party.party !== null, guild: guild.guild !== null };
		} catch {
			// Not knowing leaves both channels open; the zone refuses a line to a group the master is not in.
		}
	}

	$effect(() => chat.open(masterId));

	$effect(() => {
		const id = masterId;
		membership = { party: null, guild: null };
		void loadMembership(id);
		return live.on((event) => touchesMaster(event, id) && void loadMembership(id));
	});

	// A group the master has left cannot stay chosen.
	$effect(() => {
		if (closed[channel] === null) return;
		const other: Channel = channel === 'PARTY' ? 'GUILD' : 'PARTY';
		channel = closed[other] === null ? other : 'WHISPER';
	});

	$effect(() => {
		const timer = setInterval(() => (now = Date.now()), 30_000);
		return () => clearInterval(timer);
	});

	// Follow the newest line.
	$effect(() => {
		void lines.length;
		if (list) list.scrollTop = list.scrollHeight;
	});

	/** Answers a whisper: whisper mode, to the other player of [line]. */
	function answer(line: ChatLine) {
		const partner = whisperPartner(line);
		if (!partner) return;
		channel = 'WHISPER';
		whisperTo = partner;
	}

	/** The line as its endpoint takes it: whispers name a master, group lines a channel. */
	function post(line: string): Promise<void> {
		const headers = { 'Idempotency-Key': key };
		if (channel === 'WHISPER') {
			const order: WhisperOrder = { targetName: whisperTo.trim(), text: line };
			return sendJson<void>('POST', `/v1/companion/masters/${masterId}/chat/whisper`, order, headers);
		}
		const order: ChatOrder = { channel, text: line };
		return sendJson<void>('POST', `/v1/companion/masters/${masterId}/chat`, order, headers);
	}

	async function send(e: SubmitEvent) {
		e.preventDefault();
		const line = text.trim();
		if (!line || sending || missingTarget) return;

		sending = true;
		note = null;
		try {
			await guarded(() => post(line));
			text = '';
			key = crypto.randomUUID();
		} catch (e) {
			if (e instanceof ApiError && e.status === 504) {
				note = 'No answer yet - send again to make sure.';
			} else if (e instanceof ApiError && e.status === 409 && channel === 'WHISPER') {
				key = crypto.randomUUID();
				note = unavailableText(whisperTo.trim());
			} else if (e instanceof ApiError && e.status === 409) {
				// Left the group meanwhile: the channel closes itself once membership is read again.
				key = crypto.randomUUID();
				void loadMembership(masterId);
			} else {
				key = crypto.randomUUID();
				note = (e instanceof ApiError && refusalText(e.status)) || String((e as Error).message);
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
				Nothing heard yet. What your party and guild say and whisper to you from now on arrives here{live.connected
					? ''
					: ' once the app is listening'}.
			</p>
		{:else}
			{#each lines as line (line.id)}
				<p class="line {line.channel.toLowerCase()}">
					{#if line.channel === 'WHISPER'}
						<button type="button" class="tag answer" title="Whisper back" onclick={() => answer(line)}>{tagOf(line)}</button>
					{:else}
						<span class="tag">{tagOf(line)}</span>
						<strong>{line.senderName}:</strong>
					{/if}
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
					class:closed={closed[c.value] !== null}
					role="radio"
					aria-checked={channel === c.value}
					aria-disabled={closed[c.value] !== null}
					title={closed[c.value] ?? undefined}
					onclick={() => closed[c.value] === null && (channel = c.value)}>{c.name}</button
				>
			{/each}
		</div>
		{#if channel === 'WHISPER'}
			<input
				class="field to"
				bind:value={whisperTo}
				maxlength={MAX_NAME_LENGTH}
				placeholder="To (name of a master)"
				aria-label="Whisper to"
				autocapitalize="words"
				autocomplete="off"
				spellcheck="false"
			/>
		{/if}
		<div class="say">
			<input
				class="field"
				bind:value={text}
				maxlength={MAX_TEXT_LENGTH}
				placeholder={PLACEHOLDERS[channel]}
				aria-label="Message"
				enterkeyhint="send"
			/>
			<button class="btn primary" disabled={sending || !text.trim() || missingTarget || closed[channel] !== null}
				>{sending ? 'Sending…' : 'Send'}</button
			>
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

	.whisper .tag {
		color: rgb(255 115 217);
	}

	/* A whisper's tag answers it, without looking like a button among the lines. */
	.answer {
		padding: 0;
		border: 0;
		background: none;
		font: inherit;
		font-weight: 700;
		cursor: pointer;
	}

	.when {
		margin-left: 0.35rem;
		font-size: 0.75rem;
		color: var(--muted);
	}

	.channels {
		display: grid;
		grid-template-columns: repeat(3, 1fr);
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

	/* aria-disabled rather than disabled: a disabled button shows no tooltip on every browser. */
	.channel.closed {
		opacity: 0.45;
		cursor: not-allowed;
	}

	.say {
		display: flex;
		gap: 0.5rem;
	}

	.field {
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

	.to {
		width: 100%;
		margin-bottom: 0.5rem;
	}

	.small {
		margin: 0.5rem 0 0;
		font-size: 0.85rem;
	}
</style>
