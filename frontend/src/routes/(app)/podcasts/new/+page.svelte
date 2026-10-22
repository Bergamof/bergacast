<script lang="ts">
	import { goto } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { api } from '#lib/app.ts';
	import PodcastForm from '#lib/components/PodcastForm.svelte';
	import {
		emptyPodcastFormValues,
		toPodcastInput,
		type PodcastFormValues
	} from '#lib/forms/podcast-form.ts';

	async function createPodcast(values: PodcastFormValues): Promise<void> {
		const podcast = await api.createPodcast(toPodcastInput(values));
		await goto(resolve('/(app)/podcasts/[slug]', { slug: podcast.slug }));
	}
</script>

<svelte:head>
	<title>Nouveau podcast — Bergacast</title>
</svelte:head>

<h1>Nouveau podcast</h1>
<section class="card">
	<PodcastForm
		initialValues={emptyPodcastFormValues()}
		slugEditable={true}
		submitLabel="Créer le podcast"
		onSubmit={createPodcast}
	/>
</section>
