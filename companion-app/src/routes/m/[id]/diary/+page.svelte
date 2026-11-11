<script lang="ts">
	import { page } from '$app/state';
	import { getJson } from '#lib/api/client.ts';
	import type { Bestia, DiaryEntry } from '#lib/api/model.ts';
	import { diaryText } from '#lib/diary.ts';
	import { touchesMaster } from '#lib/events.ts';
	import { guarded } from '#lib/guard.ts';
	import { live } from '#lib/live.svelte.ts';
	import { speciesName } from '#lib/names.ts';

	const masterId = $derived(Number(page.params.id));
	let lines = $state<DiaryEntry[] | null>(null);
	let names = $state<Record<number, string>>({});
	let only = $state<number | null>(null);
	let failure = $state<string | null>(null);

	async function load(id: number) {
		try {
			const [diary, bestias] = await guarded(() =>
				Promise.all([getJson<DiaryEntry[]>(`/v1/companion/masters/${id}/diary`), getJson<Bestia[]>(`/v1/companion/masters/${id}/bestias`)])
			);
			lines = diary;
			names = Object.fromEntries(bestias.map((b) => [b.id, b.name ?? speciesName(b.species)]));
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

	const shown = $derived((lines ?? []).filter((line) => only == null || line.playerBestiaId === only));

	/** Lines grouped under the day they happened, newest day first. */
	const days = $derived(
		shown.reduce<{ day: string; lines: DiaryEntry[] }[]>((groups, line) => {
			const day = new Date(line.atEpochMs).toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long' });
			const last = groups.at(-1);
			if (last?.day === day) last.lines.push(line);
			else groups.push({ day, lines: [line] });
			return groups;
		}, [])
	);

	function time(atEpochMs: number): string {
		return new Date(atEpochMs).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
	}
</script>

<main class="page">
	<h1 class="title">Diary</h1>

	{#if failure}
		<p class="alert">{failure}</p>
	{:else if lines === null}
		<p class="muted">Asking the world…</p>
	{:else}
		<label class="field">
			<span class="label">Bestia</span>
			<select bind:value={only}>
				<option value={null}>All</option>
				{#each Object.entries(names) as [id, name] (id)}<option value={Number(id)}>{name}</option>{/each}
			</select>
		</label>

		{#if days.length === 0}
			<section class="panel"><p class="muted">Nothing written down yet.</p></section>
		{/if}
		{#each days as group (group.day)}
			<section class="panel rise">
				<h2>{group.day}</h2>
				{#each group.lines as line, i (i)}
					<p class="line" class:alarm={line.kind === 'DIED' || line.kind === 'WORK_PAUSED'}>
						<span class="muted small">{time(line.atEpochMs)}</span>
						<span><strong>{names[line.playerBestiaId] ?? 'A Bestia'}</strong> {diaryText(line).charAt(0).toLowerCase() + diaryText(line).slice(1)}</span>
					</p>
				{/each}
			</section>
		{/each}
	{/if}
</main>

<style>
	.title {
		margin-bottom: 0.8rem;
	}

	select {
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
		margin-bottom: 0.8rem;
	}

	.line {
		display: grid;
		grid-template-columns: 3.2rem 1fr;
		gap: 0.5rem;
		margin: 0.3rem 0;
	}

	.alarm strong {
		color: var(--accent);
	}

	.small {
		font-size: 0.85rem;
	}
</style>
