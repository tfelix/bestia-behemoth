<script lang="ts">
	import { page } from '$app/state';
	import { age } from '#lib/age.ts';
	import { ApiError, getJson, sendJson } from '#lib/api/client.ts';
	import type { Letter, LetterOrder, Parcel } from '#lib/api/model.ts';
	import { guarded } from '#lib/guard.ts';
	import { live } from '#lib/live.svelte.ts';
	import { keptFor, letterRefusal, MAX_BODY_LENGTH, MAX_SUBJECT_LENGTH, POSTAGE, replySubject } from '#lib/mail.ts';
	import { itemName } from '#lib/names.ts';

	const masterId = $derived(Number(page.params.id));

	let letters = $state<Letter[] | null>(null);
	let parcels = $state<Parcel[]>([]);
	let failure = $state<string | null>(null);
	let openId = $state<number | null>(null);
	let now = $state(Date.now());

	let writing = $state(false);
	let recipient = $state('');
	let subject = $state('');
	let body = $state('');
	// Kept until the letter is surely sent or surely refused, so sending again after a timeout cannot pay twice.
	let key = crypto.randomUUID();
	let sending = $state(false);
	let note = $state<string | null>(null);

	const path = $derived(`/v1/companion/masters/${masterId}/letters`);

	async function load(id: number) {
		try {
			const [mailbox, waiting] = await guarded(() =>
				Promise.all([getJson<Letter[]>(`/v1/companion/masters/${id}/letters`), getJson<Parcel[]>(`/v1/companion/masters/${id}/parcels`)])
			);
			if (id === masterId) {
				letters = mailbox;
				parcels = waiting;
			}
			failure = null;
		} catch (e) {
			failure = String((e as Error).message);
		}
	}

	$effect(() => {
		const id = masterId;
		letters = null;
		void load(id);
		return live.on((event) => (event.type === 'LetterArrived' || event.type === 'ParcelArrived') && event.data.masterId === id && void load(id));
	});

	$effect(() => {
		const timer = setInterval(() => (now = Date.now()), 60_000);
		return () => clearInterval(timer);
	});

	function open(letter: Letter) {
		openId = openId === letter.letterId ? null : letter.letterId;
		if (letter.read) return;

		letter.read = true;
		void guarded(() => sendJson<void>('PUT', `${path}/${letter.letterId}/read`, {})).catch(() => {
			// Read again next time: the mark is only a convenience.
		});
	}

	async function throwAway(letter: Letter) {
		try {
			await guarded(() => sendJson<void>('DELETE', `${path}/${letter.letterId}`, undefined));
			letters = letters?.filter((it) => it.letterId !== letter.letterId) ?? null;
		} catch (e) {
			failure = String((e as Error).message);
		}
	}

	function write(to: string = '', about: string = '') {
		recipient = to;
		subject = about;
		body = '';
		note = null;
		writing = true;
	}

	async function send(e: SubmitEvent) {
		e.preventDefault();
		if (sending) return;

		sending = true;
		note = null;
		try {
			const order: LetterOrder = { recipientName: recipient.trim(), subject: subject.trim(), body: body.trim() };
			await guarded(() => sendJson<void>('POST', path, order, { 'Idempotency-Key': key }));
			key = crypto.randomUUID();
			writing = false;
			note = `The pigeon flies off to ${order.recipientName}.`;
		} catch (e) {
			if (e instanceof ApiError && e.status === 504) {
				note = 'No answer yet - send again to make sure.';
			} else {
				key = crypto.randomUUID();
				note = (e instanceof ApiError && letterRefusal(e.status)) || String((e as Error).message);
			}
		} finally {
			sending = false;
		}
	}
</script>

<main class="page">
	<div class="head">
		<h1 class="title">Letters</h1>
		{#if !writing}<button class="btn" onclick={() => write()}>Write</button>{/if}
	</div>

	{#if writing}
		<form class="panel composer" onsubmit={send}>
			<input bind:value={recipient} placeholder="To (a master's name)" aria-label="To" autocomplete="off" />
			<input bind:value={subject} maxlength={MAX_SUBJECT_LENGTH} placeholder="Subject" aria-label="Subject" />
			<textarea bind:value={body} maxlength={MAX_BODY_LENGTH} rows="6" aria-label="Letter"></textarea>
			<div class="actions">
				<button type="button" class="btn" onclick={() => (writing = false)}>Cancel</button>
				<button class="btn primary" disabled={sending || !recipient.trim() || (!subject.trim() && !body.trim())}>
					{sending ? 'Sending…' : `Send (${POSTAGE} gold)`}
				</button>
			</div>
		</form>
	{/if}
	{#if note}<p class="muted small">{note}</p>{/if}

	{#if parcels.length > 0}
		<section class="panel">
			<h2 class="label">Parcels</h2>
			{#each parcels as parcel (parcel.parcelId)}
				<article class="parcel">
					<strong>{parcel.returning ? `Returned from ${parcel.senderName}` : `From ${parcel.senderName}`}</strong>
					<span class="muted small">
						{parcel.goods.map((g) => `${g.amount} × ${itemName(g.item)}`).join(', ')}
					</span>
					<span class="muted small">
						{parcel.arrived
							? `Waits at ${parcel.destinationName}`
							: `On the road to ${parcel.destinationName}, arrives by ${new Date(parcel.latestArrivalEpochMs).toLocaleString()}`}
					</span>
				</article>
			{/each}
		</section>
	{/if}

	{#if failure}
		<p class="muted">{failure}</p>
	{:else if letters === null}
		<p class="muted">Looking in the mailbox…</p>
	{:else if letters.length === 0}
		<p class="muted">No letters. A pigeon brings them to you, wherever you are.</p>
	{:else}
		<section class="panel">
			{#each letters as letter (letter.letterId)}
				<article class="letter" class:unread={!letter.read}>
					<button class="summary" onclick={() => open(letter)} aria-expanded={openId === letter.letterId}>
						<strong>{letter.subject || '(no subject)'}</strong>
						<span class="muted small">{letter.senderName} · {age(letter.sentAtEpochMs, now)}</span>
					</button>
					{#if openId === letter.letterId}
						<p class="body">{letter.body}</p>
						<div class="actions">
							<span class="muted small">Kept for {keptFor(letter.expiresAtEpochMs, now)}</span>
							<button class="btn" onclick={() => throwAway(letter)}>Throw away</button>
							<button class="btn" onclick={() => write(letter.senderName, replySubject(letter.subject))}>Reply</button>
						</div>
					{/if}
				</article>
			{/each}
		</section>
	{/if}
</main>

<style>
	.head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin-bottom: 0.8rem;
	}

	.title {
		margin: 0;
	}

	.composer {
		display: grid;
		gap: 0.5rem;
	}

	.composer input,
	.composer textarea {
		min-height: 2.75rem;
		padding: 0.5rem 0.75rem;
		border: 1px solid var(--edge);
		border-radius: 0.5rem;
		background: #171310;
		color: var(--ink);
		font: inherit;
	}

	.actions {
		display: flex;
		align-items: center;
		justify-content: flex-end;
		gap: 0.5rem;
	}

	.actions .muted {
		margin-right: auto;
	}

	.parcel {
		display: grid;
		padding: 0.4rem 0;
	}

	.parcel + .parcel {
		border-top: 1px solid var(--edge);
	}

	.letter + .letter {
		border-top: 1px solid var(--edge);
	}

	.summary {
		display: grid;
		width: 100%;
		padding: 0.6rem 0;
		border: 0;
		background: none;
		color: var(--ink);
		font: inherit;
		text-align: left;
		cursor: pointer;
	}

	.unread strong {
		color: var(--brass);
	}

	.body {
		margin: 0 0 0.6rem;
		white-space: pre-wrap;
		overflow-wrap: anywhere;
		line-height: 1.45;
	}

	.small {
		font-size: 0.85rem;
	}
</style>
