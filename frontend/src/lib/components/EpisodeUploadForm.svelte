<script lang="ts">
	import type { FeedPublication } from '../api/resources';
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

	interface Props {
		onSubmit: (values: EpisodeFormValues, audio: File, idempotencyKey: string) => Promise<void>;
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
					await onSubmit($state.snapshot(values), audio, idempotencyKey);
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
	<div class="actions">
		<PendingButton type="submit" pending={form.pending} pendingLabel="Envoi en cours…">
			Publier l’épisode
		</PendingButton>
		<StatusMessage message={successMessage} />
	</div>
</form>
