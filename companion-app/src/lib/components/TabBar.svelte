<script lang="ts">
	import { page } from '$app/state';
	import Icon from './Icon.svelte';

	let { masterId }: { masterId: number } = $props();

	const tabs = $derived([
		{ href: `/m/${masterId}`, label: 'Master', icon: 'chart-bar' },
		{ href: `/m/${masterId}/bestia`, label: 'Bestia', icon: 'shield' },
		{ href: `/m/${masterId}/bag`, label: 'Bag', icon: 'backpack' },
		{ href: `/m/${masterId}/map`, label: 'Map', icon: 'map-pin' },
		{ href: `/m/${masterId}/chat`, label: 'Chat', icon: 'message-circle' }
	]);
</script>

<nav>
	{#each tabs as tab (tab.href)}
		<a href={tab.href} class:active={page.url.pathname === tab.href} aria-current={page.url.pathname === tab.href ? 'page' : undefined}>
			<Icon name={tab.icon} size={22} />
			<span>{tab.label}</span>
		</a>
	{/each}
</nav>

<style>
	nav {
		position: fixed;
		inset: auto 0 0 0;
		z-index: 1000;
		display: grid;
		grid-template-columns: repeat(5, 1fr);
		height: calc(var(--nav-height) + env(safe-area-inset-bottom));
		padding-bottom: env(safe-area-inset-bottom);
		border-top: 1px solid var(--edge);
		background: linear-gradient(180deg, #1d1814, #15120f);
		box-shadow: 0 -6px 18px rgb(0 0 0 / 45%);
	}

	a {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 0.2rem;
		font-family: var(--label);
		font-weight: 600;
		font-size: 0.7rem;
		letter-spacing: 0.06em;
		text-transform: uppercase;
		color: var(--muted);
	}

	a.active {
		color: var(--brass);
	}
</style>
