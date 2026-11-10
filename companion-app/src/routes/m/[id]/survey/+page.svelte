<script lang="ts">
	import { page } from '$app/state';
	import { ApiError, getJson, sendJson } from '#lib/api/client.ts';
	import type { SurveyGame, SurveyOrder, SurveyResult, SurveyStops, Theodolite } from '#lib/api/model.ts';
	import { guarded } from '#lib/guard.ts';
	import { arcPath, dialPoint, isInArc, needleAt, SURVEY_SIZES, surveyRefusal, WORK_MINUTES, workLeft } from '#lib/survey.ts';

	const DIAL = { cx: 150, cy: 150, r: 130 };
	const RIM = `M ${DIAL.cx - DIAL.r} ${DIAL.cy} A ${DIAL.r} ${DIAL.r} 0 0 1 ${DIAL.cx + DIAL.r} ${DIAL.cy}`;

	const masterId = $derived(Number(page.params.id));
	const path = $derived(`/v1/companion/masters/${masterId}/theodolites`);

	let theodolites = $state<Theodolite[] | null>(null);
	let failure = $state<string | null>(null);
	let note = $state<string | null>(null);
	let sizes = $state<Record<number, number>>({});
	let busy = $state(false);
	// Kept until a request is surely answered, so sending again after a timeout cannot pay twice.
	let startKey = crypto.randomUUID();
	let stopsKey = crypto.randomUUID();

	/** The game being played, and where in it the player is. */
	let game = $state<{ structureId: number; rules: SurveyGame } | null>(null);
	let round = $state(0);
	let roundStartedAt = 0;
	let stops = $state<number[]>([]);
	let needle = $state(0);
	let outcome = $state<string | null>(null);

	async function load(id: number) {
		try {
			const found = await guarded(() => getJson<Theodolite[]>(`/v1/companion/masters/${id}/theodolites`));
			if (id === masterId) theodolites = found;
			failure = null;
		} catch (e) {
			failure = String((e as Error).message);
		}
	}

	$effect(() => {
		const id = masterId;
		theodolites = null;
		void load(id);
	});

	// Swings the needle while a game is open.
	$effect(() => {
		const rules = game?.rules;
		if (!rules) return;

		let frame = requestAnimationFrame(function swing(now) {
			needle = needleAt(now - roundStartedAt, rules.swingPeriodMs);
			frame = requestAnimationFrame(swing);
		});
		return () => cancelAnimationFrame(frame);
	});

	async function start(theodolite: Theodolite) {
		if (busy) return;
		busy = true;
		note = null;
		outcome = null;
		try {
			const order: SurveyOrder = { km: sizes[theodolite.structureId] ?? 1 };
			const rules = await guarded(() =>
				sendJson<SurveyGame>('POST', `${path}/${theodolite.structureId}/survey`, order, { 'Idempotency-Key': startKey })
			);
			startKey = crypto.randomUUID();
			stopsKey = crypto.randomUUID();
			game = { structureId: theodolite.structureId, rules };
			stops = [];
			startRound(0);
		} catch (e) {
			if (e instanceof ApiError && e.status === 504) {
				note = 'No answer yet - start again to make sure. You will not pay twice.';
			} else {
				startKey = crypto.randomUUID();
				note = (e instanceof ApiError && surveyRefusal(e.status)) || String((e as Error).message);
			}
		} finally {
			busy = false;
		}
	}

	function startRound(index: number) {
		round = index;
		roundStartedAt = performance.now();
	}

	function stop() {
		if (!game || busy) return;
		stops = [...stops, Math.round(performance.now() - roundStartedAt)];
		if (stops.length < game.rules.arcCentresDegrees.length) {
			startRound(round + 1);
		} else {
			void submit();
		}
	}

	async function submit() {
		if (!game) return;
		busy = true;
		const structureId = game.structureId;
		try {
			const body: SurveyStops = { stopsMs: stops };
			const result = await guarded(() =>
				sendJson<SurveyResult>('POST', `${path}/${structureId}/survey/stops`, body, { 'Idempotency-Key': stopsKey })
			);
			outcome = result.outcome;
			game = null;
			void load(masterId);
		} catch (e) {
			const unanswered = e instanceof ApiError && e.status === 504;
			note = unanswered ? 'No answer yet - send the stops again.' : String((e as Error).message);
			if (!unanswered) game = null;
		} finally {
			busy = false;
		}
	}

	function onKey(e: KeyboardEvent) {
		// A focused button already turns Space into its own click.
		if (e.target instanceof HTMLButtonElement) return;
		if (game && e.key === ' ' && !e.repeat) {
			e.preventDefault();
			stop();
		}
	}

	/** The arc of the round being played, and how the last stop landed against its own round's arc. */
	const target = $derived(game ? game.rules.arcCentresDegrees[Math.min(round, game.rules.arcCentresDegrees.length - 1)] : 0);
	const lastStopHit = $derived.by(() => {
		if (!game || stops.length === 0) return null;
		const index = stops.length - 1;
		const at = needleAt(stops[index], game.rules.swingPeriodMs);
		return isInArc(at, game.rules.arcCentresDegrees[index], game.rules.arcWidthDegrees);
	});
	const needleTip = $derived(dialPoint(DIAL.cx, DIAL.cy, DIAL.r - 4, needle));
	const allStopped = $derived(game !== null && stops.length >= game.rules.arcCentresDegrees.length);

	function stateText(t: Theodolite): string {
		switch (t.state) {
			case 'SURVEYING':
				return `Surveying ${t.radiusKm} km · ${workLeft(t.remainingSeconds ?? 0)} of work left`;
			case 'READY':
				return `Chart of ${t.radiusKm} km ready - collect it at the theodolite`;
			default:
				return 'Idle';
		}
	}
</script>

<svelte:window onkeydown={onKey} />

<main class="page">
	<h1 class="title">Theodolites</h1>

	{#if game}
		<section class="panel game">
			<span class="label">Round {Math.min(round + 1, game.rules.arcCentresDegrees.length)} of {game.rules.arcCentresDegrees.length}</span>
			<svg viewBox="0 0 300 160" role="img" aria-label="Survey dial">
				<path d={RIM} class="rim" />
				<path d={arcPath(DIAL.cx, DIAL.cy, DIAL.r - 8, target, game.rules.arcWidthDegrees)} class="arc" />
				{#if !allStopped}
					<line x1={DIAL.cx} y1={DIAL.cy} x2={needleTip.x} y2={needleTip.y} class="needle" />
				{/if}
				<circle cx={DIAL.cx} cy={DIAL.cy} r="5" class="pivot" />
			</svg>
			{#if lastStopHit !== null}
				<p class="muted small">Last stop: {lastStopHit ? 'inside the arc' : 'outside the arc'}</p>
			{/if}
			<p class="muted small">Stop the needle inside the green arc. Space works too.</p>
			{#if allStopped}
				<button class="btn primary" onclick={submit} disabled={busy}>{busy ? 'Sending…' : 'Send the stops again'}</button>
			{:else}
				<button class="btn primary" onclick={stop} disabled={busy}>Stop</button>
			{/if}
		</section>
	{/if}

	{#if outcome === 'WON'}
		<p class="panel">
			The survey has begun. It takes {WORK_MINUTES} minutes of work while you or one of your bestias stands at the theodolite.
		</p>
	{:else if outcome === 'MISSED'}
		<p class="panel">The needle missed. The paper and ink are spent.</p>
	{/if}
	{#if note}<p class="muted small">{note}</p>{/if}

	{#if failure}
		<p class="muted">{failure}</p>
	{:else if theodolites === null}
		<p class="muted">Looking for your theodolites…</p>
	{:else if theodolites.length === 0}
		<p class="muted">You have no theodolite yet. Build one from a theodolite kit in the game.</p>
	{:else}
		<section class="panel">
			{#each theodolites as theodolite (theodolite.structureId)}
				<article class="theodolite">
					<div class="info">
						<strong>Theodolite at {Math.round(theodolite.position.eastMetres)} E, {Math.round(theodolite.position.northMetres)} N</strong>
						<span class="muted small">{stateText(theodolite)}</span>
					</div>
					{#if theodolite.state === 'IDLE' && !game}
						<div class="actions">
							<select bind:value={sizes[theodolite.structureId]} aria-label="Survey size">
								{#each SURVEY_SIZES as size (size.km)}
									<option value={size.km}>{size.km} km (Lv {size.level})</option>
								{/each}
							</select>
							<button class="btn" disabled={busy} onclick={() => start(theodolite)}>Survey</button>
						</div>
					{/if}
				</article>
			{/each}
			<p class="muted small">A survey spends one empty paper and one ink. Collect the chart in the game.</p>
		</section>
	{/if}
</main>

<style>
	.title {
		margin: 0 0 0.8rem;
	}

	.small {
		font-size: 0.85rem;
	}

	.game {
		display: grid;
		justify-items: center;
		gap: 0.4rem;
		margin-bottom: 0.85rem;
	}

	.game svg {
		width: 100%;
		max-width: 22rem;
	}

	.rim {
		fill: none;
		stroke: var(--muted);
		stroke-width: 2;
	}

	.arc {
		fill: none;
		stroke: var(--good);
		stroke-width: 10;
	}

	.needle {
		stroke: var(--brass);
		stroke-width: 3;
		stroke-linecap: round;
	}

	.pivot {
		fill: var(--brass);
	}

	.game .btn {
		width: 100%;
		max-width: 22rem;
	}

	.theodolite {
		display: grid;
		gap: 0.4rem;
		padding: 0.5rem 0;
	}

	.theodolite + .theodolite {
		border-top: 1px solid var(--edge);
	}

	.info {
		display: grid;
	}

	.actions {
		display: flex;
		gap: 0.5rem;
	}

	select {
		flex: 1;
		min-height: 2.75rem;
		padding: 0.5rem 0.75rem;
		border: 1px solid var(--edge);
		border-radius: 0.5rem;
		background: #171310;
		color: var(--ink);
		font: inherit;
	}
</style>
