<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { finishLogin } from '$lib/auth';
	import { landingPath } from '$lib/lastMaster';

	let failure = $state<string | null>(null);

	onMount(async () => {
		try {
			await finishLogin(page.url);
			await goto(await landingPath(), { replaceState: true });
		} catch (e) {
			failure = e instanceof Error ? e.message : String(e);
		}
	});
</script>

<main class="page">
	{#if failure}
		<p class="alert">{failure}</p>
		<a class="btn" href="/login">Back to sign-in</a>
	{:else}
		<p class="muted">Signing you in…</p>
	{/if}
</main>
