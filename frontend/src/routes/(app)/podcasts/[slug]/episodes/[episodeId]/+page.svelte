<script lang="ts">
	import { goto, invalidate } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { api } from '#lib/app.ts';
	import ConfirmButton from '#lib/components/ConfirmButton.svelte';
	import EpisodeEditForm from '#lib/components/EpisodeEditForm.svelte';
	import {
		episodeFormValues,
		toEpisodeMetadata,
		type EpisodeFormValues
	} from '#lib/forms/episode-form.ts';
	import { formatDateTime } from '#lib/format/date-time.ts';
	import { formatFileSize } from '#lib/format/file-size.ts';
	import { LoadDependency } from '#lib/load-dependencies.ts';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();

	const podcast = $derived(data.podcast.resource);
	const episode = $derived(data.episode.resource);
	const episodesHref = $derived(resolve('/(app)/podcasts/[slug]/episodes', { slug: podcast.slug }));
	let savedMessage = $state('');

	async function saveEpisode(values: EpisodeFormValues): Promise<void> {
		savedMessage = '';
		await api.updateEpisode(data.episode, toEpisodeMetadata(values));
		await invalidate(LoadDependency.episode);
		savedMessage = 'Modifications enregistrées.';
	}

	async function deleteEpisode(): Promise<void> {
		await api.deleteEpisode(episode);
		await invalidate(LoadDependency.podcast);
		await goto(episodesHref);
	}
</script>

<svelte:head>
	<title>{episode.title} — {podcast.title} — Bergacast</title>
</svelte:head>

<p><a href={episodesHref}>← Retour aux épisodes</a></p>

<section class="card" aria-labelledby="episode-title">
	<h2 id="episode-title">Modifier l’épisode</h2>
	<p class="muted">
		Ajouté le {formatDateTime(episode.addedAt)} · {episode.audio.mimeType} · {formatFileSize(
			episode.audio.sizeBytes
		)}. Le fichier audio ne peut pas être remplacé : supprimez l’épisode et publiez-le de nouveau.
	</p>
	{#if savedMessage}
		<p class="notice success" role="status">{savedMessage}</p>
	{/if}
	{#key data.episode}
		<EpisodeEditForm initialValues={episodeFormValues(episode)} onSubmit={saveEpisode} />
	{/key}
</section>

<section class="card" aria-labelledby="episode-danger-title">
	<h2 id="episode-danger-title">Suppression</h2>
	<ConfirmButton
		label="Supprimer l’épisode"
		title="Supprimer « {episode.title} » ?"
		onConfirm={deleteEpisode}
	>
		<p>L’épisode et son fichier audio seront définitivement supprimés.</p>
	</ConfirmButton>
</section>
