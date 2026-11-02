<script lang="ts">
	import ErrorPageContent from '#lib/components/ErrorPageContent.svelte';
</script>

<main class="error-page">
	<ErrorPageContent />
</main>

<style>
	.error-page {
		max-width: var(--content-width);
		margin: 0 auto;
		padding: var(--space-xl) var(--space-m);
	}
</style>
