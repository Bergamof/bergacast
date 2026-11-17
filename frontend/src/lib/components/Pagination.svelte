<script lang="ts">
	import type { PageMetadata } from '../api/hal';
	import { pageHref } from '../pagination';

	interface Props {
		page: PageMetadata;
		url: { readonly href: string };
	}

	let { page, url }: Props = $props();

	const hasPrevious = $derived(page.number > 0);
	const hasNext = $derived(page.number + 1 < page.totalPages);
	const beyondLastPage = $derived(page.totalPages > 0 && page.number >= page.totalPages);
</script>

{#if beyondLastPage}
	<nav class="pagination" aria-label="Pagination">
		<span>Cette page n’existe plus.</span>
		<!-- eslint-disable-next-line svelte/no-navigation-without-resolve -- href derives from the already resolved current URL -->
		<a class="button secondary" href={pageHref(url, page.totalPages - 1)}
			>Aller à la dernière page</a
		>
	</nav>
{:else if page.totalPages > 1}
	<nav class="pagination" aria-label="Pagination">
		{#if hasPrevious}
			<!-- eslint-disable-next-line svelte/no-navigation-without-resolve -- href derives from the already resolved current URL -->
			<a class="button secondary" href={pageHref(url, page.number - 1)}>← Précédente</a>
		{/if}
		<span>Page {page.number + 1} sur {page.totalPages} ({page.totalElements} au total)</span>
		{#if hasNext}
			<!-- eslint-disable-next-line svelte/no-navigation-without-resolve -- href derives from the already resolved current URL -->
			<a class="button secondary" href={pageHref(url, page.number + 1)}>Suivante →</a>
		{/if}
	</nav>
{/if}

<style>
	.pagination {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-m);
		align-items: center;
		justify-content: center;
		margin-top: var(--space-l);
	}
</style>
