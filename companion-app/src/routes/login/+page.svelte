<script lang="ts">
	import { startLogin } from '$lib/auth';
	import { isInstalled, isIos } from '$lib/push';

	let busy = $state(false);
	let failure = $state<string | null>(null);
	const iosBrowser = isIos() && !isInstalled();

	async function go(intent: 'LOGIN' | 'REGISTER') {
		busy = true;
		failure = null;
		try {
			await startLogin(intent);
		} catch (e) {
			failure = e instanceof Error ? e.message : String(e);
			busy = false;
		}
	}
</script>

<main class="page login">
	<img class="crest" src="/icons/icon-192.png" alt="" width="128" height="128" />
	<h1 class="wordmark">Bestia</h1>
	<p class="muted lead">Your Masters are where you left them. Look in on them, and on your Bestia, from wherever you are.</p>

	<section class="panel rise">
		<button class="btn primary wide" disabled={busy} onclick={() => go('LOGIN')}>Sign in with a passkey</button>
		<button class="btn wide" disabled={busy} onclick={() => go('REGISTER')}>Create an account</button>
		{#if failure}<p class="alert">{failure}</p>{/if}
	</section>

	{#if iosBrowser}
		<p class="muted hint">Tip: add Bestia to your Home Screen (Share, then "Add to Home Screen") to get notifications on this device.</p>
	{/if}
</main>

<style>
	.login {
		display: grid;
		justify-items: center;
		align-content: center;
		min-height: 100dvh;
		text-align: center;
	}

	.crest {
		border-radius: 1.5rem;
		box-shadow: 0 0 0 2px var(--brass-dim), 0 12px 32px rgb(0 0 0 / 50%);
	}

	.wordmark {
		margin-top: 1rem;
		font-size: 2.2rem;
		letter-spacing: 0.3em;
		text-transform: uppercase;
		color: var(--brass);
	}

	.lead {
		max-width: 22rem;
	}

	.panel {
		display: grid;
		gap: 0.6rem;
		width: 100%;
		max-width: 22rem;
	}

	.wide {
		width: 100%;
	}

	.hint {
		max-width: 22rem;
		font-size: 0.85rem;
	}
</style>
