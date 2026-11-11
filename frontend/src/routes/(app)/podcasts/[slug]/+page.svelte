<script lang="ts">
	import { goto, invalidate } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { api } from '#lib/app.ts';
	import { changeThenRefresh } from '#lib/api/change-then-refresh.ts';
	import ConfirmButton from '#lib/components/ConfirmButton.svelte';
	import PodcastForm from '#lib/components/PodcastForm.svelte';
	import CoverImageForm from '#lib/components/CoverImageForm.svelte';
	import PublishingInfo from '#lib/components/PublishingInfo.svelte';
	import {
		podcastFormValues,
		toPodcastPatch,
		type PodcastFormValues
	} from '#lib/forms/podcast-form.ts';
	import { LoadDependency } from '#lib/load-dependencies.ts';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();

	const podcast = $derived(data.podcast.resource);

	function refreshPodcast(): Promise<void> {
		return invalidate(LoadDependency.podcast);
	}

	function savePodcast(values: PodcastFormValues): Promise<void> {
		return changeThenRefresh(
			() => api.updatePodcast(data.podcast, toPodcastPatch(values)),
			refreshPodcast
		);
	}

	// The image changes the podcast version (ETag): reload it before any further update.
	function uploadImage(file: File): Promise<void> {
		return changeThenRefresh(() => api.uploadPodcastImage(podcast, file), refreshPodcast);
	}

	function deleteImage(): Promise<void> {
		return changeThenRefresh(() => api.deletePodcastImage(podcast), refreshPodcast);
	}

	async function deletePodcast(): Promise<void> {
		await api.deletePodcast(podcast);
		await goto(resolve('/(app)/podcasts'));
	}
</script>

<svelte:head>
	<title>{podcast.title} — Bergacast</title>
</svelte:head>

<PublishingInfo
	{podcast}
	apiTokensHref={resolve('/(app)/podcasts/[slug]/tokens', { slug: podcast.slug })}
	onPublishFeed={() => api.publishFeed(podcast)}
/>

<section class="card" aria-labelledby="settings-title">
	<h2 id="settings-title">Réglages du podcast</h2>
	<!-- Keyed by identity only: a save keeps the form, and with it the focus. -->
	{#key podcast.slug}
		<PodcastForm
			initialValues={podcastFormValues(podcast)}
			slugEditable={false}
			submitLabel="Enregistrer"
			successMessage="Modifications enregistrées."
			onSubmit={savePodcast}
			onReload={refreshPodcast}
			onPublishFeed={() => api.publishFeed(podcast)}
		/>
	{/key}
</section>

<section class="card" aria-labelledby="image-title">
	<h2 id="image-title">Image de couverture</h2>
	<CoverImageForm
		image={podcast.image}
		subjectTitle={podcast.title}
		inputId="podcast-image"
		onUpload={uploadImage}
		onDelete={deleteImage}
	>
		{#snippet intro()}
			Une image envoyée ici remplace, dans le flux RSS, l’URL d’image externe des réglages.
		{/snippet}
		{#snippet absent()}
			Aucune image envoyée : le flux utilise l’URL d’image externe, si elle est renseignée.
		{/snippet}
		{#snippet deletionNotice()}
			Le flux RSS utilisera de nouveau l’URL d’image externe, si elle est renseignée.
		{/snippet}
	</CoverImageForm>
</section>

<section class="card" aria-labelledby="danger-title">
	<h2 id="danger-title">Suppression</h2>
	<p>
		Supprimer le podcast efface définitivement ses {podcast.episodeCount} épisodes, leurs fichiers audio
		et ses jetons d’API. Le flux RSS cessera d’exister.
	</p>
	<ConfirmButton
		label="Supprimer le podcast"
		title="Supprimer « {podcast.title} » ?"
		onConfirm={deletePodcast}
	>
		<p>Cette action est irréversible.</p>
	</ConfirmButton>
</section>
