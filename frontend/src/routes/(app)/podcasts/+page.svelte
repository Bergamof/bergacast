<script lang="ts">
	import { resolve } from '$app/paths';
	import { page } from '$app/state';
	import Pagination from '#lib/components/Pagination.svelte';
	import { formatDateTime } from '#lib/format/date-time.ts';
	import { imagePreviewUrl } from '#lib/forms/podcast-image.ts';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();
</script>

<svelte:head>
	<title>Podcasts — Bergacast</title>
</svelte:head>

<div class="toolbar">
	<h1>Podcasts</h1>
	<a class="button" href={resolve('/(app)/podcasts/new')}>Nouveau podcast</a>
</div>

{#if data.podcasts.items.length === 0}
	<p class="card">Aucun podcast pour l’instant. Créez le premier !</p>
{:else}
	<div class="table-wrapper">
		<table>
			<thead>
				<tr>
					<th scope="col">Titre</th>
					<th scope="col">Identifiant</th>
					<th scope="col">Langue</th>
					<th scope="col">Épisodes</th>
					<th scope="col">Modifié le</th>
				</tr>
			</thead>
			<tbody>
				{#each data.podcasts.items as podcast (podcast.id)}
					<tr>
						<td>
							<div class="title">
								{#if podcast.image}
									<img
										class="thumbnail"
										src={imagePreviewUrl(podcast.image)}
										alt="Couverture de « {podcast.title} »"
										width="40"
										height="40"
										loading="lazy"
										decoding="async"
									/>
								{:else}
									<span class="thumbnail placeholder" aria-hidden="true"></span>
								{/if}
								<a href={resolve('/(app)/podcasts/[slug]', { slug: podcast.slug })}>
									{podcast.title}
								</a>
							</div>
						</td>
						<td><code>{podcast.slug}</code></td>
						<td>{podcast.language}</td>
						<td>
							<a href={resolve('/(app)/podcasts/[slug]/episodes', { slug: podcast.slug })}>
								{podcast.episodeCount}
							</a>
						</td>
						<td>{formatDateTime(podcast.updatedAt)}</td>
					</tr>
				{/each}
			</tbody>
		</table>
	</div>
	<Pagination page={data.podcasts.page} url={page.url} />
{/if}

<style>
	.title {
		display: flex;
		gap: var(--space-s);
		align-items: center;
	}

	.thumbnail {
		flex: none;
		width: 2.5rem;
		height: 2.5rem;
		object-fit: cover;
		border: 1px solid var(--color-border);
		border-radius: var(--radius);
	}

	.placeholder {
		display: block;
		background: var(--color-background);
	}
</style>
