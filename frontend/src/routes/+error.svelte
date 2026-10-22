<script lang="ts">
	import { page } from '$app/state';
	import { resolve } from '$app/paths';
</script>

<main class="error-page">
	<h1>Erreur {page.status}</h1>
	<p>{page.error?.message}</p>
	<p><a href={resolve('/(app)/podcasts')}>Retour à la liste des podcasts</a></p>
</main>

<style>
	.error-page {
		max-width: var(--content-width);
		margin: 0 auto;
		padding: var(--space-xl) var(--space-m);
	}
</style>
