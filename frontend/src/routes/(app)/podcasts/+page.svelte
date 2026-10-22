<script lang="ts">
	import { resolve } from '$app/paths';
	import { page } from '$app/state';
	import Pagination from '#lib/components/Pagination.svelte';
	import { formatDateTime } from '#lib/format/date-time.ts';
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
							<a href={resolve('/(app)/podcasts/[slug]', { slug: podcast.slug })}>{podcast.title}</a
							>
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
