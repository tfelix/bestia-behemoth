<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { call, getJson, sendJson } from '$lib/api/client';
	import type { Preferences } from '$lib/api/model';
	import { logout } from '$lib/auth';
	import { guarded } from '$lib/guard';
	import { live } from '$lib/live.svelte';
	import { currentSubscription, disablePush, enablePush, isInstalled, pushUnavailableReason } from '$lib/push';

	let prefs = $state<Preferences | null>(null);
	let pushOn = $state(false);
	let unavailable = $state<string | null>(null);
	let busy = $state(false);
	let note = $state<string | null>(null);

	onMount(async () => {
		unavailable = pushUnavailableReason();
		if (!unavailable) pushOn = (await currentSubscription()) !== null;
		prefs = await guarded(() => getJson<Preferences>('/v1/companion/push/preferences'));
	});

	async function togglePush() {
		busy = true;
		note = null;
		try {
			if (pushOn) {
				await guarded(disablePush);
				pushOn = false;
			} else {
				await guarded(enablePush);
				pushOn = true;
			}
		} catch (e) {
			note = e instanceof Error ? e.message : String(e);
		} finally {
			busy = false;
		}
	}

	async function save() {
		if (!prefs) return;
		const quiet = prefs.quietFrom && prefs.quietTo;
		prefs = await guarded(() =>
			sendJson<Preferences>('PUT', '/v1/companion/push/preferences', {
				...prefs,
				quietFrom: quiet ? prefs!.quietFrom : null,
				quietTo: quiet ? prefs!.quietTo : null,
				timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone
			})
		);
		note = 'Saved.';
	}

	async function test() {
		await guarded(() => call('/v1/companion/push/test', { method: 'POST' }));
		note = 'Sent - it should arrive in a moment.';
	}

	async function signOut() {
		live.stop();
		await logout();
		await goto('/login', { replaceState: true });
	}
</script>

<main class="page">
	<header class="top">
		<a href="/masters" class="back">‹ Masters</a>
		<h1>Settings</h1>
	</header>

	<section class="panel rise">
		<h2>Notifications</h2>
		{#if unavailable}
			<p class="muted">{unavailable}</p>
		{:else}
			<p class="muted">Sent only while you are not in the game, and at most a few an hour.</p>
			<div class="row">
				<button class="btn" class:primary={!pushOn} disabled={busy} onclick={togglePush}>
					{pushOn ? 'Turn off on this device' : 'Turn on for this device'}
				</button>
				{#if pushOn}<button class="btn" onclick={test}>Send a test</button>{/if}
			</div>
		{/if}

		{#if prefs}
			<fieldset>
				<legend class="label">Tell me when</legend>
				<label><input type="checkbox" bind:checked={prefs.homeFell} onchange={save} /> my home town falls</label>
				<label><input type="checkbox" bind:checked={prefs.bestiaDied} onchange={save} /> a Bestia is killed while I am away</label>
				<label><input type="checkbox" bind:checked={prefs.constructionDone} onchange={save} /> a construction is finished</label>
				<label><input type="checkbox" bind:checked={prefs.journeyEnded} onchange={save} /> a Bestia I sent off arrives, or gets stuck</label>
			</fieldset>
			<fieldset>
				<legend class="label">Quiet hours</legend>
				<div class="row">
					<input type="time" bind:value={prefs.quietFrom} onchange={save} aria-label="Quiet from" />
					<span class="muted">to</span>
					<input type="time" bind:value={prefs.quietTo} onchange={save} aria-label="Quiet until" />
				</div>
			</fieldset>
		{/if}
		{#if note}<p class="muted">{note}</p>{/if}
	</section>

	{#if !isInstalled()}
		<section class="panel rise">
			<h2>Install</h2>
			<p class="muted">
				Add Bestia to your Home Screen to open it like an app. On Android, use the browser menu's "Install app"; on
				iPhone, Share and then "Add to Home Screen".
			</p>
		</section>
	{/if}

	<section class="panel rise">
		<button class="btn" onclick={signOut}>Sign out on this device</button>
	</section>
</main>

<style>
	.top {
		display: grid;
		gap: 0.3rem;
		margin-bottom: 1rem;
	}

	.back {
		font-family: var(--label);
		font-weight: 600;
		color: var(--muted);
	}

	.row {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: 0.6rem;
		margin: 0.6rem 0;
	}

	fieldset {
		display: grid;
		gap: 0.45rem;
		margin: 1rem 0 0;
		padding: 0;
		border: 0;
	}

	input[type='checkbox'] {
		accent-color: var(--accent);
	}

	input[type='time'] {
		padding: 0.4rem 0.5rem;
		border: 1px solid var(--edge);
		border-radius: 0.4rem;
		background: #171310;
		color: var(--ink);
		font: inherit;
	}
</style>
