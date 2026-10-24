<script lang="ts">
	import { apiDocumentationHref } from '../app';
	import type { Podcast } from '../api/resources';
	import { buildPublishCurlExample } from '../publishing/curl-example';
	import CopyableText from './CopyableText.svelte';

	interface Props {
		podcast: Podcast;
		apiTokensHref: string;
	}

	let { podcast, apiTokensHref }: Props = $props();

	const curlExample = $derived(buildPublishCurlExample(podcast.publishUrl));
</script>

<section class="card" aria-labelledby="publishing-title">
	<h2 id="publishing-title">Diffusion et publication</h2>

	<h3>Flux RSS public</h3>
	<p class="muted">L’adresse à donner aux applications d’écoute et annuaires de podcasts.</p>
	<CopyableText value={podcast.feedUrl} label="URL du flux RSS" />
	<p>
		<!-- eslint-disable-next-line svelte/no-navigation-without-resolve -- public feed served by the backend, outside the SPA -->
		<a href={podcast.feedUrl} target="_blank" rel="noopener">Ouvrir le flux</a>
	</p>

	<h3>Publication automatisée</h3>
	<p class="muted">
		Les outils automatisés publient un épisode par une requête <code>POST</code>
		<code>multipart/form-data</code> sur cette adresse, avec une partie <code>metadata</code> (JSON
		:
		<code>title</code> obligatoire, <code>description</code>, <code>publicationDate</code>,
		<code>guid</code>, <code>durationSeconds</code>, <code>season</code>,
		<code>episodeNumber</code>, <code>episodeType</code>, <code>explicit</code>) et une partie
		<code>audio</code> (fichier <code>audio/*</code>). Ils s’authentifient avec un
		<!-- eslint-disable-next-line svelte/no-navigation-without-resolve -- resolved by the parent page -->
		<a href={apiTokensHref}>jeton d’API</a> propre à ce podcast. L’en-tête facultatif
		<code>Idempotency-Key</code> évite les doublons en cas de nouvel essai.
	</p>
	<CopyableText value={podcast.publishUrl} label="URL de publication" />
	<h4>Exemple avec curl</h4>
	<CopyableText value={curlExample} label="Exemple de commande curl" multiline />
	<p>
		<!-- eslint-disable-next-line svelte/no-navigation-without-resolve -- API documentation served by the backend, outside the SPA -->
		<a href={apiDocumentationHref} target="_blank" rel="noopener">
			Documentation OpenAPI de l’API
		</a>
	</p>
</section>

<style>
	h3 {
		margin-top: var(--space-l);
	}

	h4 {
		margin: var(--space-m) 0 var(--space-s);
	}
</style>
