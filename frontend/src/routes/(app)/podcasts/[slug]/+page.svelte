<script lang="ts">
	import { goto, invalidate } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { api } from '#lib/app.ts';
	import ConfirmButton from '#lib/components/ConfirmButton.svelte';
	import PodcastForm from '#lib/components/PodcastForm.svelte';
	import PodcastImageForm from '#lib/components/PodcastImageForm.svelte';
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
	let savedMessage = $state('');

	async function savePodcast(values: PodcastFormValues): Promise<void> {
		savedMessage = '';
		await api.updatePodcast(data.podcast, toPodcastPatch(values));
		await invalidate(LoadDependency.podcast);
		savedMessage = 'Modifications enregistrées.';
	}

	// The image changes the podcast version (ETag): reload it before any further update.
	async function uploadImage(file: File): Promise<void> {
		await api.uploadPodcastImage(podcast, file);
		await invalidate(LoadDependency.podcast);
	}

	async function deleteImage(): Promise<void> {
		await api.deletePodcastImage(podcast);
		await invalidate(LoadDependency.podcast);
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
	{#if savedMessage}
		<p class="notice success" role="status">{savedMessage}</p>
	{/if}
	{#key data.podcast}
		<PodcastForm
			initialValues={podcastFormValues(podcast)}
			slugEditable={false}
			submitLabel="Enregistrer"
			onSubmit={savePodcast}
		/>
	{/key}
</section>

<section class="card" aria-labelledby="image-title">
	<h2 id="image-title">Image de couverture</h2>
	<PodcastImageForm
		image={podcast.image}
		podcastTitle={podcast.title}
		onUpload={uploadImage}
		onDelete={deleteImage}
	/>
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
