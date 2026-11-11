<script lang="ts">
	import { beforeNavigate } from '$app/navigation';
	import type { UploadTransfer } from '../api/client';
	import type { FeedPublication } from '../api/resources';
	import { formatFileSize } from '../format/file-size';
	import { IMAGE_FILE_ACCEPT, validateImageFile } from '../forms/podcast-image';
	import {
		emptyEpisodeFormValues,
		validateAudioFile,
		validateEpisodeForm,
		type EpisodeFormValues
	} from '../forms/episode-form';
	import { FormState } from '../forms/form-state.svelte';
	import EpisodeFields from './EpisodeFields.svelte';
	import { newIdempotencyKey } from '../idempotency-key';
	import FormField from './FormField.svelte';
	import PendingButton from './PendingButton.svelte';
	import ProblemAlert from './ProblemAlert.svelte';
	import RecoveryActions from './RecoveryActions.svelte';
	import RequiredFieldsLegend from './RequiredFieldsLegend.svelte';
	import StatusMessage from './StatusMessage.svelte';
	import { UploadTracker } from '../publishing/upload-tracker.svelte';

	interface Props {
		onSubmit: (
			values: EpisodeFormValues,
			audio: File,
			image: File | null,
			idempotencyKey: string,
			transfer: UploadTransfer
		) => Promise<void>;
		/** Offered after a 503, when the episode may be saved but the feed not written. */
		onPublishFeed: () => Promise<FeedPublication>;
	}

	let { onSubmit, onPublishFeed }: Props = $props();

	let values = $state(emptyEpisodeFormValues());
	let files = $state<FileList | undefined>();
	let fileInput: HTMLInputElement | undefined = $state();
	let imageFiles = $state<FileList | undefined>();
	let imageInput: HTMLInputElement | undefined = $state();
	let successMessage = $state('');
	// Kept across retries of the same upload so that a request that actually succeeded despite a
	// network error does not create a duplicate episode; renewed once the upload is confirmed.
	let idempotencyKey = newIdempotencyKey();
	const form = new FormState();
	const upload = new UploadTracker();
	const LEAVE_WARNING =
		'Un fichier audio est en cours d’envoi. Quitter la page et abandonner l’envoi ?';
	const uid = $props.id();
	let submitButton: HTMLButtonElement | undefined = $state();
	let cancelButton: HTMLButtonElement | undefined = $state();

	const chosenFile = $derived(files?.[0]);

	// Leaving would silently drop an upload that may last minutes: ask first.
	beforeNavigate((navigation) => {
		if (!upload.cancellable) {
			return;
		}
		// Closing or reloading the tab: cancelling asks the browser to show its own warning.
		if (navigation.type === 'leave' || !confirm(LEAVE_WARNING)) {
			navigation.cancel();
			return;
		}
		upload.cancel();
	});

	// « Annuler l’envoi » disappears once the file is sent, cancelled or failed: a keyboard user on
	// it would fall back to the page, so the focus moves to the submit button first.
	$effect.pre(() => {
		if (!upload.cancellable && cancelButton && document.activeElement === cancelButton) {
			submitButton?.focus();
		}
	});

	function cancelUpload(): void {
		upload.cancel();
	}

	function validate() {
		const errors = validateEpisodeForm(values, { publicationDateRequired: false });
		const audioError = validateAudioFile(files?.[0]);
		const imageFile = imageFiles?.[0];
		// The image is optional: only a chosen file is checked.
		const imageError = imageFile ? validateImageFile(imageFile) : null;
		return {
			...errors,
			...(audioError !== null && { audio: audioError }),
			...(imageError !== null && { image: imageError })
		};
	}

	async function handleSubmit(
		event: SubmitEvent & { currentTarget: EventTarget & HTMLFormElement }
	): Promise<void> {
		event.preventDefault();
		successMessage = '';
		const title = values.title;
		const audio = files?.[0];
		const image = imageFiles?.[0] ?? null;
		const uploaded = await form.submit(
			validate,
			async () => {
				if (audio) {
					try {
						await onSubmit($state.snapshot(values), audio, image, idempotencyKey, upload.start());
					} finally {
						upload.finish();
					}
				}
			},
			event.currentTarget
		);
		if (uploaded) {
			idempotencyKey = newIdempotencyKey();
			successMessage = `Épisode « ${title} » publié.`;
			values = emptyEpisodeFormValues();
			if (fileInput) {
				fileInput.value = '';
			}
			files = undefined;
			if (imageInput) {
				imageInput.value = '';
			}
			imageFiles = undefined;
		}
	}
</script>

<form novalidate onsubmit={handleSubmit}>
	<RequiredFieldsLegend />
	<FormField id="episode-audio" label="Fichier audio" required error={form.fieldErrors.audio}>
		{#snippet control(attributes)}
			<input {...attributes} type="file" accept="audio/*" bind:files bind:this={fileInput} />
		{/snippet}
	</FormField>
	{#if chosenFile}
		<p class="muted chosen-file">{chosenFile.name} · {formatFileSize(chosenFile.size)}</p>
	{/if}
	<FormField
		id="episode-image"
		label="Image de l’épisode"
		hint="Facultative, JPEG ou PNG : sinon, les applications affichent l’image du podcast."
		error={form.fieldErrors.image}
	>
		{#snippet control(attributes)}
			<input
				{...attributes}
				type="file"
				accept={IMAGE_FILE_ACCEPT}
				bind:files={imageFiles}
				bind:this={imageInput}
			/>
		{/snippet}
	</FormField>
	<EpisodeFields
		bind:values
		errors={form.fieldErrors}
		publicationDateRequired={false}
		guidEditable={true}
	/>
	<ProblemAlert
		problem={form.problem}
		inlineFields={[...Object.keys(values), 'durationSeconds', 'audio', 'image']}
	/>
	<RecoveryActions problem={form.problem} {onPublishFeed} />
	{#if upload.active}
		<div class="upload-progress">
			<label for="{uid}-progress">Envoi du fichier audio</label>
			<!-- Indeterminate (no value attribute: Svelte would write value="0") until the size is known
				and while the server stores the episode. -->
			{#if upload.total > 0 && !upload.sent}
				<progress
					id="{uid}-progress"
					aria-describedby="{uid}-progress-label"
					max={upload.total}
					value={upload.loaded}
				></progress>
			{:else}
				<progress id="{uid}-progress" aria-describedby="{uid}-progress-label"></progress>
			{/if}
			<span id="{uid}-progress-label" class="muted">{upload.label}</span>
		</div>
	{/if}
	<!-- Not a StatusMessage: heard only, the progress bar already shows the state. -->
	<p class="visually-hidden" role="status">{upload.announcement}</p>
	<div class="actions">
		<PendingButton
			type="submit"
			pending={form.pending}
			pendingLabel="Envoi en cours…"
			bind:element={submitButton}
		>
			Publier l’épisode
		</PendingButton>
		{#if upload.cancellable}
			<button type="button" class="secondary" bind:this={cancelButton} onclick={cancelUpload}>
				Annuler l’envoi
			</button>
		{/if}
		<StatusMessage message={successMessage} />
	</div>
</form>

<style>
	.chosen-file {
		margin: 0;
		overflow-wrap: anywhere;
	}

	.upload-progress {
		display: grid;
		gap: var(--space-xs);
	}

	progress {
		width: 100%;
	}
</style>
