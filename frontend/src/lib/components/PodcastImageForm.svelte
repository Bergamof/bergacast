<script lang="ts">
	import type { PodcastImage } from '../api/resources';
	import { formatFileSize } from '../format/file-size';
	import type { FieldErrors } from '../forms/field-errors';
	import { FormState } from '../forms/form-state.svelte';
	import {
		IMAGE_FILE_ACCEPT,
		MAX_IMAGE_FILE_SIZE_BYTES,
		formatImageDimensions,
		imagePreviewUrl,
		imageRecommendationWarnings,
		validateImageFile
	} from '../forms/podcast-image';
	import ConfirmButton from './ConfirmButton.svelte';
	import FormField from './FormField.svelte';
	import ProblemAlert from './ProblemAlert.svelte';

	interface Props {
		image: PodcastImage | null;
		podcastTitle: string;
		onUpload: (file: File) => Promise<void>;
		onDelete: () => Promise<void>;
	}

	let { image, podcastTitle, onUpload, onDelete }: Props = $props();

	let files = $state<FileList | undefined>();
	let fileInput: HTMLInputElement | undefined = $state();
	let statusMessage = $state('');
	const form = new FormState();

	const warnings = $derived(image ? imageRecommendationWarnings(image) : []);

	function validate(): FieldErrors {
		const error = validateImageFile(files?.[0]);
		return error === null ? {} : { image: error };
	}

	async function handleSubmit(event: SubmitEvent): Promise<void> {
		event.preventDefault();
		statusMessage = '';
		const file = files?.[0];
		const replacing = image !== null;
		const uploaded = await form.submit(validate, async () => {
			if (file) {
				await onUpload(file);
			}
		});
		if (uploaded) {
			statusMessage = replacing ? 'Image remplacée.' : 'Image enregistrée.';
			if (fileInput) {
				fileInput.value = '';
			}
			files = undefined;
		}
	}

	async function deleteImage(): Promise<void> {
		statusMessage = '';
		form.reset();
		await onDelete();
		statusMessage = 'Image supprimée.';
	}
</script>

<p class="muted">
	Une image envoyée ici remplace, dans le flux RSS, l’URL d’image externe des réglages. JPEG ou PNG,
	{formatFileSize(MAX_IMAGE_FILE_SIZE_BYTES)} au maximum ; au-delà de 3000 px, le serveur la réduit. Apple
	Podcasts recommande une image carrée de 1400 à 3000 px de côté.
</p>

<div aria-live="polite">
	{#if statusMessage}
		<p class="notice success" role="status">{statusMessage}</p>
	{/if}
</div>

{#if image}
	<div class="current">
		<img
			src={imagePreviewUrl(image)}
			alt="Couverture actuelle de « {podcastTitle} »"
			width={image.width}
			height={image.height}
		/>
		<dl>
			<dt>Dimensions</dt>
			<dd>{formatImageDimensions(image)}</dd>
			<dt>Format</dt>
			<dd><code>{image.mimeType}</code></dd>
			<dt>Taille</dt>
			<dd>{formatFileSize(image.sizeBytes)}</dd>
		</dl>
	</div>
	{#if warnings.length > 0}
		<div class="notice warning">
			{#each warnings as warning (warning)}
				<p>{warning}</p>
			{/each}
		</div>
	{/if}
{:else}
	<p>Aucune image envoyée : le flux utilise l’URL d’image externe, si elle est renseignée.</p>
{/if}

<form novalidate onsubmit={handleSubmit}>
	<ProblemAlert problem={form.problem} inlineFields={['image']} />
	<FormField
		id="podcast-image"
		label={image ? 'Nouvelle image' : 'Image'}
		required
		error={form.fieldErrors.image}
	>
		{#snippet control(attributes)}
			<input
				{...attributes}
				type="file"
				accept={IMAGE_FILE_ACCEPT}
				bind:files
				bind:this={fileInput}
			/>
		{/snippet}
	</FormField>
	<div class="actions">
		<button type="submit" disabled={form.pending}>
			{#if form.pending}
				Envoi en cours…
			{:else}
				{image ? 'Remplacer l’image' : 'Envoyer l’image'}
			{/if}
		</button>
		{#if image}
			<ConfirmButton
				label="Supprimer l’image"
				title="Supprimer l’image de « {podcastTitle} » ?"
				onConfirm={deleteImage}
			>
				<p>Le flux RSS utilisera de nouveau l’URL d’image externe, si elle est renseignée.</p>
			</ConfirmButton>
		{/if}
	</div>
</form>

<style>
	.current {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-m);
		align-items: flex-start;
		margin-bottom: var(--space-m);
	}

	img {
		width: 12rem;
		height: auto;
		border: 1px solid var(--color-border);
		border-radius: var(--radius);
	}

	dl {
		display: grid;
		grid-template-columns: auto 1fr;
		gap: var(--space-xs) var(--space-m);
		margin: 0;
	}

	dt {
		font-weight: 600;
	}

	dd {
		margin: 0;
	}

	.notice {
		margin-bottom: var(--space-m);
	}

	form {
		display: grid;
		gap: var(--space-m);
	}
</style>
