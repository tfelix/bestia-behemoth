<script lang="ts">
	import '@fontsource/cinzel/600.css';
	import '@fontsource/cinzel/700.css';
	import '../app.css';
	import Toasts from '#lib/components/Toasts.svelte';

	let { children } = $props();
</script>

<Toasts />
{@render children()}
