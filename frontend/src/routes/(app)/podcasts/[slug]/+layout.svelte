<script lang="ts">
	import { resolve } from '$app/paths';
	import { page } from '$app/state';
	import type { LayoutProps } from './$types';

	let { data, children }: LayoutProps = $props();

	const podcast = $derived(data.podcast.resource);
	const tabs = $derived([
		{ href: resolve('/(app)/podcasts/[slug]', { slug: podcast.slug }), label: 'Réglages' },
		{
			href: resolve('/(app)/podcasts/[slug]/episodes', { slug: podcast.slug }),
			label: `Épisodes (${podcast.episodeCount})`
		},
		{
			href: resolve('/(app)/podcasts/[slug]/tokens', { slug: podcast.slug }),
			label: 'Jetons d’API'
		}
	]);

	function isCurrentTab(href: string, index: number): boolean {
		const isSettingsTab = index === 0;
		return isSettingsTab
			? page.url.pathname === href
			: page.url.pathname === href || page.url.pathname.startsWith(`${href}/`);
	}
</script>

<p class="breadcrumb"><a href={resolve('/(app)/podcasts')}>Podcasts</a> /</p>
<h1>{podcast.title}</h1>

<nav class="tabs" aria-label="Sections du podcast">
	{#each tabs as tab, index (tab.href)}
		<a href={tab.href} aria-current={isCurrentTab(tab.href, index) ? 'page' : undefined}>
			{tab.label}
		</a>
	{/each}
</nav>

{@render children()}

<style>
	.breadcrumb {
		margin: 0;
	}

	.tabs {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-s);
		margin-bottom: var(--space-l);
		border-bottom: 1px solid var(--color-border);
	}

	.tabs a {
		padding: var(--space-s) var(--space-m);
		text-decoration: none;
		border-bottom: 3px solid transparent;
	}

	.tabs a[aria-current='page'] {
		border-bottom-color: var(--color-primary);
		font-weight: 700;
	}
</style>
