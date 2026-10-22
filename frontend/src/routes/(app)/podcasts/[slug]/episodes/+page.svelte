<script lang="ts">
	import { invalidate } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { page } from '$app/state';
	import { api } from '#lib/app.ts';
	import type { Episode } from '#lib/api/resources.ts';
	import ConfirmButton from '#lib/components/ConfirmButton.svelte';
	import EpisodeUploadForm from '#lib/components/EpisodeUploadForm.svelte';
	import Pagination from '#lib/components/Pagination.svelte';
	import { toUploadMetadata, type EpisodeFormValues } from '#lib/forms/episode-form.ts';
	import { formatDateTime, isInFuture } from '#lib/format/date-time.ts';
	import { formatDuration } from '#lib/format/duration.ts';
	import { formatFileSize } from '#lib/format/file-size.ts';
	import { LoadDependency } from '#lib/load-dependencies.ts';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();

	const podcast = $derived(data.podcast.resource);

	async function refresh(): Promise<void> {
		await Promise.all([invalidate(LoadDependency.episodes), invalidate(LoadDependency.podcast)]);
	}

	async function uploadEpisode(
		values: EpisodeFormValues,
		audio: File,
		idempotencyKey: string
	): Promise<void> {
		await api.uploadEpisode(podcast, {
			metadata: toUploadMetadata(values),
			audio,
			idempotencyKey
		});
		await refresh();
	}

	async function deleteEpisode(episode: Episode): Promise<void> {
		await api.deleteEpisode(episode);
		await refresh();
	}
</script>

<svelte:head>
	<title>Épisodes — {podcast.title} — Bergacast</title>
</svelte:head>

<details class="card">
	<summary>Publier un épisode depuis l’interface</summary>
	<EpisodeUploadForm onSubmit={uploadEpisode} />
</details>

{#if data.episodes.items.length === 0}
	<p class="card">Aucun épisode pour l’instant.</p>
{:else}
	<ul class="episodes">
		{#each data.episodes.items as episode (episode.id)}
			<li class="card">
				<article aria-labelledby="episode-{episode.id}">
					<div class="toolbar">
						<h2 id="episode-{episode.id}">
							{episode.title}
							{#if isInFuture(episode.publicationDate)}
								<span class="badge">programmé</span>
							{/if}
						</h2>
						<div class="actions">
							<a
								class="button secondary"
								href={resolve('/(app)/podcasts/[slug]/episodes/[episodeId]', {
									slug: podcast.slug,
									episodeId: episode.id
								})}
								aria-label="Modifier « {episode.title} »"
							>
								Modifier
							</a>
							<ConfirmButton
								label="Supprimer"
								accessibleLabel="Supprimer « {episode.title} »"
								title="Supprimer « {episode.title} » ?"
								onConfirm={() => deleteEpisode(episode)}
							>
								<p>L’épisode et son fichier audio seront définitivement supprimés.</p>
							</ConfirmButton>
						</div>
					</div>
					<dl>
						<div>
							<dt>Publication</dt>
							<dd>{formatDateTime(episode.publicationDate)}</dd>
						</div>
						<div>
							<dt>Ajout</dt>
							<dd>{formatDateTime(episode.addedAt)}</dd>
						</div>
						<div>
							<dt>Durée</dt>
							<dd>{formatDuration(episode.durationSeconds)}</dd>
						</div>
						<div>
							<dt>Taille</dt>
							<dd>{formatFileSize(episode.audio.sizeBytes)}</dd>
						</div>
					</dl>
					<div class="player">
						<audio controls preload="none" src={episode.audio.url}>
							<!-- eslint-disable-next-line svelte/no-navigation-without-resolve -- public audio file served by the backend -->
							<a href={episode.audio.url}>Télécharger le fichier audio</a>
						</audio>
						<!-- eslint-disable-next-line svelte/no-navigation-without-resolve -- public audio file served by the backend -->
						<a href={episode.audio.url} target="_blank" rel="noopener">Fichier audio</a>
					</div>
				</article>
			</li>
		{/each}
	</ul>
	<Pagination page={data.episodes.page} url={page.url} />
{/if}

<style>
	summary {
		cursor: pointer;
		font-weight: 600;
	}

	details[open] summary {
		margin-bottom: var(--space-m);
	}

	.episodes {
		margin: 0;
		padding: 0;
		list-style: none;
	}

	h2 {
		font-size: 1.15rem;
	}

	dl {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(10rem, 1fr));
		gap: var(--space-s);
		margin: 0 0 var(--space-m);
	}

	dt {
		font-size: 0.875rem;
		color: var(--color-muted);
	}

	dd {
		margin: 0;
	}

	.player {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-m);
		align-items: center;
	}

	audio {
		max-width: 100%;
	}
</style>
