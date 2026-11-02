<script lang="ts">
	import { invalidateAll } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { page } from '$app/state';
	import { errorPageTitle, isRetryable } from '../error-page';
	import PendingButton from './PendingButton.svelte';

	const title = $derived(errorPageTitle(page.status));
	let retrying = $state(false);

	async function retry(): Promise<void> {
		retrying = true;
		try {
			await invalidateAll();
		} finally {
			retrying = false;
		}
	}
</script>

<svelte:head>
	<title>{title} — Bergacast</title>
</svelte:head>

<h1>{title}</h1>
<p>{page.error?.message}</p>
<div class="actions">
	{#if isRetryable(page.status)}
		<PendingButton type="button" pending={retrying} pendingLabel="Nouvel essai…" onclick={retry}>
			Réessayer
		</PendingButton>
	{/if}
	<a href={resolve('/(app)/podcasts')}>Retour à la liste des podcasts</a>
</div>
