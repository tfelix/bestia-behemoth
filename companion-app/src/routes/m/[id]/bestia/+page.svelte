<script lang="ts">
	import { page } from '$app/state';
	import { ApiError, getJson, sendJson } from '#lib/api/client.ts';
	import { COURAGE, JOBS, type Bestia, type BestiaOrders, type OrderPreset, type PresetWrite } from '#lib/api/model.ts';
	import Age from '#lib/components/Age.svelte';
	import { touchesMaster } from '#lib/events.ts';
	import { guarded } from '#lib/guard.ts';
	import { live } from '#lib/live.svelte.ts';
	import { COURAGE_NAME, JOB_TEXT, speciesName } from '#lib/names.ts';
	import {
		ACTION_NAME,
		CONDITIONS,
		MAX_RULES,
		NUMBERS,
		changed,
		draftOf,
		fallbacksFor,
		newRule,
		ordersOf,
		ruleOf,
		sheetOf,
		type OrderDraft
	} from '#lib/orders.ts';
	import { stopJourney, travelStatus } from '#lib/travel.ts';

	type Draft = OrderDraft & { key: string; sending: boolean; note: string | null; presetName: string };

	const masterId = $derived(Number(page.params.id));
	let bestias = $state<Bestia[] | null>(null);
	let presets = $state<OrderPreset[]>([]);
	let drafts = $state<Record<number, Draft>>({});
	let failure = $state<string | null>(null);

	function freshDraft(b: Bestia, note: string | null): Draft {
		return { ...draftOf(b), key: crypto.randomUUID(), sending: false, note, presetName: '' };
	}

	async function load(id: number) {
		try {
			[bestias, presets] = await guarded(() =>
				Promise.all([
					getJson<Bestia[]>(`/v1/companion/masters/${id}/bestias`),
					getJson<OrderPreset[]>(`/v1/companion/masters/${id}/order-presets`)
				])
			);
			for (const b of bestias) {
				if (!drafts[b.id]?.sending) drafts[b.id] = freshDraft(b, drafts[b.id]?.note ?? null);
			}
			failure = null;
		} catch (e) {
			failure = e instanceof Error ? e.message : String(e);
		}
	}

	$effect(() => {
		const id = masterId;
		void load(id);
		return live.on((event) => touchesMaster(event, id) && void load(id));
	});

	let stopping = $state<number | null>(null);

	async function stop(b: Bestia) {
		stopping = b.id;
		try {
			await guarded(() => stopJourney(masterId, b.id, crypto.randomUUID()));
			await load(masterId);
		} catch (e) {
			failure = e instanceof Error ? e.message : String(e);
		} finally {
			stopping = null;
		}
	}

	function presetOf(id: number | null): OrderPreset | undefined {
		return presets.find((preset) => preset.id === id);
	}

	/** Following a preset takes its job and courage; its own numbers start empty. */
	function follow(draft: Draft, presetId: number | null) {
		draft.presetId = presetId;
		const preset = presetOf(presetId);
		if (!preset) return;
		draft.job = preset.sheet.job;
		draft.courage = preset.sheet.courage;
		draft.numbers = {};
	}

	function noteOf(e: unknown): string {
		return e instanceof ApiError && e.status === 504 ? 'No answer yet - send again to make sure.' : String((e as Error).message);
	}

	async function give(b: Bestia) {
		const draft = drafts[b.id];
		draft.sending = true;
		draft.note = null;
		try {
			const stored = await guarded(() =>
				sendJson<BestiaOrders>('PUT', `/v1/companion/masters/${masterId}/bestias/${b.id}/orders`, ordersOf(draft), {
					'Idempotency-Key': draft.key
				})
			);
			Object.assign(b, stored);
			drafts[b.id] = freshDraft(b, 'Orders given.');
		} catch (e) {
			// On a timeout the key stays: sending again cannot give the order twice.
			draft.sending = false;
			draft.note = noteOf(e);
		}
	}

	/** Under a name already in use, it changes that preset, and so every Bestia that follows it. */
	async function saveAsPreset(draft: Draft) {
		const write: PresetWrite = { name: draft.presetName.trim(), sheet: sheetOf(draft) };
		const existing = presets.find((preset) => preset.name === write.name);
		const [method, path] = existing
			? ['PUT', `/v1/companion/masters/${masterId}/order-presets/${existing.id}`]
			: ['POST', `/v1/companion/masters/${masterId}/order-presets`];
		try {
			presets = await guarded(() => sendJson<OrderPreset[]>(method, path, write, { 'Idempotency-Key': crypto.randomUUID() }));
			if (existing) await load(masterId);
			draft.note = `Saved as "${draft.presetName.trim()}".`;
			draft.presetName = '';
		} catch (e) {
			draft.note = noteOf(e);
		}
	}

	async function deletePreset(preset: OrderPreset) {
		try {
			await guarded(() =>
				sendJson<OrderPreset[]>('DELETE', `/v1/companion/masters/${masterId}/order-presets/${preset.id}`, undefined, {
					'Idempotency-Key': crypto.randomUUID()
				})
			);
			await load(masterId);
		} catch (e) {
			failure = e instanceof Error ? e.message : String(e);
		}
	}
</script>

<main class="page">
	<h1 class="title">Bestia</h1>

	{#if failure}
		<p class="alert">{failure}</p>
	{:else if bestias === null}
		<p class="muted">Asking the world…</p>
	{:else if bestias.length === 0}
		<section class="panel"><p class="muted">This Master has no Bestia yet.</p></section>
	{:else}
		{#each bestias as b (b.id)}
			{@const draft = drafts[b.id]}
			<section class="panel rise">
				<div class="head">
					<div>
						<h2>{b.name ?? speciesName(b.species)}</h2>
						<span class="muted">{speciesName(b.species)} · Level {b.level}</span>
					</div>
					<Age asOf={b.asOf} live={b.inWorld} />
				</div>

				<p class="travel">
					<span class="muted small">{travelStatus(b)}</span>
					{#if b.journey}
						<button class="btn" disabled={stopping === b.id} onclick={() => stop(b)}>Stop</button>
					{:else if b.inWorld}
						<a class="btn" href={`/m/${masterId}/map`}>Send…</a>
					{/if}
				</p>

				{#if draft}
					{@const following = presetOf(draft.presetId)}
					<label class="field">
						<span class="label">Orders</span>
						<select value={draft.presetId ?? ''} onchange={(e) => follow(draft, e.currentTarget.value ? Number(e.currentTarget.value) : null)}>
							<option value="">Its own</option>
							{#each presets as preset (preset.id)}<option value={preset.id}>Preset: {preset.name}</option>{/each}
						</select>
					</label>

					<div class="label">Job</div>
					<div class="jobs" role="radiogroup" aria-label="Job">
						{#each JOBS as job (job)}
							<button
								class="job"
								class:chosen={draft.job === job}
								role="radio"
								aria-checked={draft.job === job}
								disabled={!!following}
								onclick={() => (draft.job = job)}>{JOB_TEXT[job].name}</button
							>
						{/each}
					</div>
					<p class="muted small">{JOB_TEXT[draft.job]?.text}</p>

					<label class="courage">
						<span class="label">Courage <strong>{COURAGE_NAME[draft.courage] ?? draft.courage}</strong></span>
						<input
							type="range"
							min="0"
							max={COURAGE.length - 1}
							step="1"
							disabled={!!following}
							value={COURAGE.indexOf(draft.courage as (typeof COURAGE)[number])}
							oninput={(e) => (draft.courage = COURAGE[Number(e.currentTarget.value)])}
						/>
						<span class="ends muted small"><span>Careful</span><span>Brave</span></span>
					</label>
					{#if following}
						<p class="muted small">Job, courage, fallback and rules come from the preset. Numbers set below stay this Bestia's own.</p>
					{/if}

					<details class="tune">
						<summary>Fine-tune</summary>

						{#if !following && fallbacksFor(draft.job).length > 0}
							<label class="field">
								<span class="label">When there is nothing to do</span>
								<select bind:value={draft.fallbackJob}>
									<option value={null}>Rest</option>
									{#each fallbacksFor(draft.job) as job (job)}<option value={job}>{JOB_TEXT[job].name}</option>{/each}
								</select>
							</label>
						{/if}

						<div class="label">Numbers <span class="muted small">(empty: chosen by courage and job)</span></div>
						<div class="numbers">
							{#each NUMBERS as number (number.key)}
								<label class="number">
									<span>{number.name}</span>
									<input
										type="number"
										min={number.min}
										max={number.max}
										placeholder="auto"
										bind:value={draft.numbers[number.key]}
									/>
									<span class="muted small">{number.unit}</span>
								</label>
							{/each}
						</div>

						{#if !following}
							<div class="label">Rules <span class="muted small">(read from the top, the first that holds decides)</span></div>
							{#each draft.rules as rule, i (i)}
								{@const condition = CONDITIONS[rule.condition]}
								<div class="rule">
									<span class="muted small">When</span>
									<select bind:value={rule.condition} onchange={() => (draft.rules[i] = ruleOf(rule))}>
										{#each Object.entries(CONDITIONS) as [key, c] (key)}<option value={key}>{c.name}</option>{/each}
									</select>
									{#if condition.takesThreshold}
										<input class="threshold" type="number" min="1" max="99" bind:value={rule.threshold} /><span>%</span>
									{/if}
									<span class="muted small">then</span>
									<select bind:value={rule.action}>
										{#each condition.actions as action (action)}<option value={action}>{ACTION_NAME[action]}</option>{/each}
									</select>
									<button class="btn small" aria-label="Remove rule" onclick={() => draft.rules.splice(i, 1)}>×</button>
								</div>
							{/each}
							{#if draft.rules.length < MAX_RULES}
								<button class="btn small" onclick={() => draft.rules.push(newRule())}>Add rule</button>
							{/if}

							<div class="save-preset">
								<input placeholder="Preset name" maxlength="32" bind:value={draft.presetName} />
								<button class="btn small" disabled={!draft.presetName.trim()} onclick={() => saveAsPreset(draft)}>
									Save as preset
								</button>
							</div>
						{/if}
					</details>

					<div class="actions">
						<button class="btn primary" disabled={!changed(b, draft) || draft.sending} onclick={() => give(b)}>
							{draft.sending ? 'Sending…' : 'Give the order'}
						</button>
						{#if draft.note}<span class="muted small">{draft.note}</span>{/if}
					</div>
				{/if}
			</section>
		{/each}

		{#if presets.length > 0}
			<section class="panel rise">
				<h2>Presets</h2>
				<p class="muted small">Change a preset by saving a Bestia's orders under its name again. Deleting one leaves its Bestias their orders.</p>
				{#each presets as preset (preset.id)}
					<div class="preset">
						<span>{preset.name}</span>
						<span class="muted small">{JOB_TEXT[preset.sheet.job]?.name}, {COURAGE_NAME[preset.sheet.courage]} · {preset.bestias} following</span>
						<button class="btn small" onclick={() => deletePreset(preset)}>Delete</button>
					</div>
				{/each}
			</section>
		{/if}
	{/if}
</main>

<style>
	.title {
		margin-bottom: 0.8rem;
	}

	.head {
		display: flex;
		justify-content: space-between;
		align-items: flex-start;
		gap: 0.75rem;
		margin-bottom: 0.8rem;
	}

	.travel {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 0.5rem;
		margin: 0 0 0.8rem;
	}

	.jobs {
		display: grid;
		grid-template-columns: repeat(2, 1fr);
		gap: 0.35rem;
		margin-top: 0.35rem;
	}

	.job {
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

	.job.chosen {
		border-color: var(--brass);
		background: linear-gradient(180deg, #3a2f22, #241d16);
		color: var(--brass);
		box-shadow: inset 0 0 0 1px rgb(201 164 92 / 30%);
	}

	.small {
		font-size: 0.85rem;
	}

	.courage {
		display: grid;
		gap: 0.3rem;
		margin: 0.6rem 0 0.9rem;
	}

	.courage input {
		accent-color: var(--accent);
	}

	.ends {
		display: flex;
		justify-content: space-between;
	}

	.actions {
		display: flex;
		align-items: center;
		gap: 0.8rem;
	}

	/* As the chat line: a dark field in the panel's own edge. */
	select,
	input:not([type='range']) {
		min-height: 2.25rem;
		padding: 0 0.6rem;
		border: 1px solid var(--edge);
		border-radius: 0.45rem;
		background: #171310;
		color: var(--ink);
		font: inherit;
	}

	.field {
		display: grid;
		gap: 0.3rem;
		margin-bottom: 0.7rem;
	}

	.tune {
		margin: 0 0 0.9rem;
	}

	.tune summary {
		cursor: pointer;
		font-family: var(--label);
		color: var(--brass);
		margin-bottom: 0.6rem;
	}

	.numbers {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(9rem, 1fr));
		gap: 0.5rem;
		margin: 0.35rem 0 0.8rem;
	}

	.number {
		display: grid;
		gap: 0.2rem;
	}

	.rule,
	.save-preset,
	.preset {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: 0.4rem;
		margin: 0.35rem 0;
	}

	.threshold {
		width: 4rem;
	}

	.preset span:first-child {
		font-weight: 600;
	}
</style>
