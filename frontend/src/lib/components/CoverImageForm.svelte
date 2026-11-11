<script lang="ts">
	import type { Snippet } from 'svelte';
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
	import PendingButton from './PendingButton.svelte';
	import ProblemAlert from './ProblemAlert.svelte';
	import RequiredFieldsLegend from './RequiredFieldsLegend.svelte';
	import StatusMessage from './StatusMessage.svelte';

	interface Props {
		/** Cover image of a podcast or of an episode: the two share their rules and representation. */
		image: PodcastImage | null;
		/** Title of the podcast or episode, used in the alternative text and the confirmation. */
		subjectTitle: string;
		/** Identifies the file input, unique on the page. */
		inputId: string;
		/** Explains what the image is for and the accepted files. */
		intro: Snippet;
		/** Said when there is no image yet. */
		absent: Snippet;
		/** Consequence of the deletion, shown in the confirmation. */
		deletionNotice: Snippet;
		onUpload: (file: File) => Promise<void>;
		onDelete: () => Promise<void>;
	}

	let { image, subjectTitle, inputId, intro, absent, deletionNotice, onUpload, onDelete }: Props =
		$props();

	let files = $state<FileList | undefined>();
	let fileInput: HTMLInputElement | undefined = $state();
	let statusMessage = $state('');
	const form = new FormState();

	const warnings = $derived(image ? imageRecommendationWarnings(image) : []);

	function validate(): FieldErrors {
		const error = validateImageFile(files?.[0]);
		return error === null ? {} : { image: error };
	}

	async function handleSubmit(
		event: SubmitEvent & { currentTarget: EventTarget & HTMLFormElement }
	): Promise<void> {
		event.preventDefault();
		statusMessage = '';
		const file = files?.[0];
		const replacing = image !== null;
		const uploaded = await form.submit(
			validate,
			async () => {
				if (file) {
					await onUpload(file);
				}
			},
			event.currentTarget
		);
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
	{@render intro()}
	JPEG ou PNG, {formatFileSize(MAX_IMAGE_FILE_SIZE_BYTES)} au maximum ; au-delà de 3000 px, le serveur
	la réduit. Apple Podcasts recommande une image carrée de 1400 à 3000 px de côté.
</p>

{#if image}
	<div class="current">
		<img
			src={imagePreviewUrl(image)}
			alt="Couverture actuelle de « {subjectTitle} »"
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
{:else}
	<p>{@render absent()}</p>
{/if}

<!-- Announced when an upload brings them; always rendered, as a live region must be. -->
<div role="status">
	{#if warnings.length > 0}
		<div class="notice warning">
			{#each warnings as warning (warning)}
				<p>{warning}</p>
			{/each}
		</div>
	{/if}
</div>

<form novalidate onsubmit={handleSubmit}>
	<RequiredFieldsLegend />
	<FormField
		id={inputId}
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
	<ProblemAlert problem={form.problem} inlineFields={['image']} />
	<div class="actions">
		<PendingButton type="submit" pending={form.pending} pendingLabel="Envoi en cours…">
			{image ? 'Remplacer l’image' : 'Envoyer l’image'}
		</PendingButton>
		{#if image}
			<ConfirmButton
				label="Supprimer l’image"
				title="Supprimer l’image de « {subjectTitle} » ?"
				onConfirm={deleteImage}
				afterConfirm={() => fileInput?.focus()}
			>
				<p>{@render deletionNotice()}</p>
			</ConfirmButton>
		{/if}
		<StatusMessage message={statusMessage} />
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
