<script lang="ts">
	import { beforeNavigate } from '$app/navigation';
	import type { UploadTransfer } from '../api/client';
	import type { FeedPublication } from '../api/resources';
	import { formatFileSize } from '../format/file-size';
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
	let successMessage = $state('');
	// Kept across retries of the same upload so that a request that actually succeeded despite a
	// network error does not create a duplicate episode; renewed once the upload is confirmed.
	let idempotencyKey = newIdempotencyKey();
	const form = new FormState();
	const upload = new UploadTracker();
	const LEAVE_WARNING = 'Un fichier audio est en cours d’envoi. Quitter la page annule l’envoi.';
	let submitArea: HTMLElement | undefined = $state();

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

	function cancelUpload(): void {
		// The cancel button disappears with the upload: keep the focus in the form.
		submitArea?.querySelector<HTMLElement>('button[type="submit"]')?.focus();
		upload.cancel();
	}

	function validate() {
		const errors = validateEpisodeForm(values, { publicationDateRequired: false });
		const audioError = validateAudioFile(files?.[0]);
		return audioError === null ? errors : { ...errors, audio: audioError };
	}

	async function handleSubmit(
		event: SubmitEvent & { currentTarget: EventTarget & HTMLFormElement }
	): Promise<void> {
		event.preventDefault();
		successMessage = '';
		const title = values.title;
		const audio = files?.[0];
		const uploaded = await form.submit(
			validate,
			async () => {
				if (audio) {
					try {
						await onSubmit($state.snapshot(values), audio, idempotencyKey, upload.start());
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
	<EpisodeFields
		bind:values
		errors={form.fieldErrors}
		publicationDateRequired={false}
		guidEditable={true}
	/>
	<ProblemAlert
		problem={form.problem}
		inlineFields={[...Object.keys(values), 'durationSeconds', 'audio']}
	/>
	<RecoveryActions problem={form.problem} {onPublishFeed} />
	{#if upload.active}
		<div class="upload-progress">
			<label for="episode-upload-progress">Envoi du fichier audio</label>
			<progress id="episode-upload-progress" max={upload.total || 1} value={upload.loaded}
			></progress>
			<span class="muted">{upload.sent ? 'Enregistrement de l’épisode…' : upload.label}</span>
		</div>
	{/if}
	<p class="visually-hidden" role="status">{upload.announcement}</p>
	<div class="actions" bind:this={submitArea}>
		<PendingButton type="submit" pending={form.pending} pendingLabel="Envoi en cours…">
			Publier l’épisode
		</PendingButton>
		{#if upload.cancellable}
			<button type="button" class="secondary" onclick={cancelUpload}>Annuler l’envoi</button>
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
