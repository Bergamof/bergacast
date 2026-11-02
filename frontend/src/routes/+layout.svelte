<script lang="ts">
	import { onMount } from 'svelte';
	import '../app.css';
	import favicon from '#lib/assets/favicon.svg';
	import type { LayoutProps } from './$types';

	let { children }: LayoutProps = $props();

	// The root layout mounts once the first page has its data: the static indicator of app.html goes.
	onMount(() => document.getElementById('app-loading')?.remove());
</script>

<svelte:head>
	<link rel="icon" href={favicon} />
	<title>Bergacast — administration</title>
</svelte:head>

{@render children()}
