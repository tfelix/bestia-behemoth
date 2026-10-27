<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { refresh } from '#lib/auth.ts';
	import { landingPath } from '#lib/lastMaster.ts';

	// A start from the home screen: resume the session if the refresh cookie still holds one, on the last Master.
	onMount(async () => {
		await goto((await refresh()) ? await landingPath() : '/login', { replaceState: true });
	});
</script>

<main class="page"><p class="muted">Opening the ledger…</p></main>
