<script lang="ts">
	import ErrorMessage from '#lib/components/ErrorMessage.svelte';
</script>

<main class="error-page">
	<ErrorMessage />
</main>

<style>
	.error-page {
		max-width: var(--content-width);
		margin: 0 auto;
		padding: var(--space-xl) var(--space-m);
	}
</style>
