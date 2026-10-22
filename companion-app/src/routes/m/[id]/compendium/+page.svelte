<script lang="ts">
	import { page } from '$app/state';
	import { getJson } from '$lib/api/client';
	import type { Catalog, CompendiumEntry } from '$lib/api/model';
	import { matches, numberLabel, type Tab } from '$lib/compendium';
	import { touchesMaster } from '$lib/events';
	import { guarded } from '$lib/guard';
	import { live } from '$lib/live.svelte';
	import { biomeName, speciesDescription, speciesName } from '$lib/names';
	import { portraitOf } from '$lib/portraits';

	type Species = Catalog['bestias'][number];

	const TABS: { tab: Tab; label: string }[] = [
		{ tab: 'all', label: 'All' },
		{ tab: 'seen', label: 'Seen' },
		{ tab: 'caught', label: 'Caught' }
	];

	const masterId = $derived(Number(page.params.id));
	let species = $state<Species[] | null>(null);
	let known = $state(new Map<number, CompendiumEntry>());
	let failure = $state<string | null>(null);
	let query = $state('');
	let tab = $state<Tab>('all');
	let opened = $state<number | null>(null);

	const listed = $derived(
		(species ?? []).filter((s) => {
			const entry = known.get(s.id);
			return matches({ id: s.id, name: entry ? speciesName(s.identifier) : null, caught: !!entry?.caught }, tab, query);
		})
	);
	const caughtCount = $derived([...known.values()].filter((e) => e.caught).length);

	async function load(id: number) {
		try {
			const [catalog, entries] = await guarded(() =>
				Promise.all([getJson<Catalog>('/v1/companion/catalog'), getJson<CompendiumEntry[]>(`/v1/companion/masters/${id}/compendium`)])
			);
			species = catalog.bestias.filter((s) => s.inCompendium);
			known = new Map(entries.map((e) => [e.speciesId, e]));
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

	function habitatText(s: Species): string {
		return s.habitat.length === 0 ? 'anywhere on land' : s.habitat.map(biomeName).join(', ');
	}
</script>

<main class="page">
	<h1 class="title">Bestia Compendium</h1>

	{#if failure}
		<p class="alert">{failure}</p>
	{:else if species === null}
		<p class="muted">Asking the world…</p>
	{:else}
		<div class="filters">
			<input class="search" type="search" placeholder="Search name or number" bind:value={query} />
			<div class="tabs" role="radiogroup" aria-label="Show">
				{#each TABS as t (t.tab)}
					<button class="tab" class:chosen={tab === t.tab} role="radio" aria-checked={tab === t.tab} onclick={() => (tab = t.tab)}
						>{t.label}</button
					>
				{/each}
			</div>
		</div>
		<p class="label">Seen {known.size} · Caught {caughtCount} · {species.length} species</p>

		{#if listed.length === 0}
			<section class="panel"><p class="muted">No species match.</p></section>
		{/if}

		{#each listed as s (s.id)}
			{@const entry = known.get(s.id)}
			<section class="panel entry rise">
				<button class="row" onclick={() => (opened = opened === s.id ? null : s.id)} aria-expanded={opened === s.id}>
					<span class="number">{numberLabel(s.id)}</span>
					<span class="frame small"><img class:unseen={!entry} src={portraitOf(s.id)} alt="" /></span>
					<span class="name" class:muted={!entry}>{entry ? speciesName(s.identifier) : '???'}</span>
					{#if entry?.caught}<span class="caught" title="Caught">◆</span>{/if}
				</button>

				{#if opened === s.id}
					<div class="detail">
						<span class="frame large"><img class:unseen={!entry} src={portraitOf(s.id)} alt="" /></span>
						{#if entry}
							<p class="muted">{s.kind.charAt(0) + s.kind.slice(1).toLowerCase()} · {entry.caught ? 'Caught' : 'Not caught yet'}</p>
							<p><span class="label">Habitat</span><br />{habitatText(s)}</p>
							{#if speciesDescription(s.identifier)}<p>{speciesDescription(s.identifier)}</p>{/if}
							<p class="muted small">First seen {new Date(entry.firstSeen).toLocaleDateString()}</p>
							<a class="btn" href={`/m/${masterId}/map?habitat=${s.id}`}>Show habitat on the map</a>
						{:else}
							<p class="muted">Not seen yet. Bring your Master or one of your Bestia close to one.</p>
						{/if}
					</div>
				{/if}
			</section>
		{/each}
	{/if}
</main>

<style>
	.title {
		margin-bottom: 0.8rem;
	}

	.filters {
		display: grid;
		gap: 0.5rem;
		margin-bottom: 0.4rem;
	}

	.search {
		min-height: 2.5rem;
		padding: 0 0.75rem;
		border: 1px solid var(--edge);
		border-radius: 0.45rem;
		background: #171310;
		color: var(--ink);
		font: inherit;
	}

	.tabs {
		display: grid;
		grid-template-columns: repeat(3, 1fr);
		gap: 0.35rem;
	}

	.tab {
		min-height: 2.25rem;
		border: 1px solid var(--edge);
		border-radius: 0.45rem;
		background: #171310;
		font-family: var(--label);
		font-weight: 600;
		font-size: 0.8rem;
		color: var(--muted);
		cursor: pointer;
	}

	.tab.chosen {
		border-color: var(--brass);
		color: var(--brass);
	}

	.entry {
		padding: 0.5rem 0.75rem;
	}

	.row {
		display: flex;
		align-items: center;
		gap: 0.75rem;
		width: 100%;
		padding: 0;
		border: none;
		background: none;
		color: inherit;
		font: inherit;
		text-align: left;
		cursor: pointer;
	}

	.number {
		min-width: 2.6rem;
		font-family: var(--label);
		font-size: 0.8rem;
		color: var(--muted);
	}

	.name {
		flex: 1;
		font-family: var(--display);
		font-weight: 600;
	}

	.caught {
		color: var(--brass);
	}

	/* The parchment the game draws its portraits on. */
	.frame {
		display: block;
		flex: none;
		border-radius: 0.35rem;
		background: #e8dbbd;
	}

	.frame img {
		display: block;
		width: 100%;
		height: 100%;
		object-fit: contain;
	}

	.frame.small {
		width: 2.6rem;
		height: 2.6rem;
	}

	.frame.large {
		width: 9rem;
		height: 9rem;
		margin: 0.4rem auto 0.6rem;
	}

	/* An unseen species keeps its outline and nothing else. */
	.frame img.unseen {
		filter: brightness(0);
		opacity: 0.85;
	}

	.detail p {
		margin: 0.4rem 0;
	}

	.small {
		font-size: 0.85rem;
	}
</style>
