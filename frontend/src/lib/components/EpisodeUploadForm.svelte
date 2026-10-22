<script lang="ts">
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
	import ProblemAlert from './ProblemAlert.svelte';

	interface Props {
		onSubmit: (values: EpisodeFormValues, audio: File, idempotencyKey: string) => Promise<void>;
	}

	let { onSubmit }: Props = $props();

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

	async function handleSubmit(event: SubmitEvent): Promise<void> {
		event.preventDefault();
		successMessage = '';
		const title = values.title;
		const audio = files?.[0];
		const uploaded = await form.submit(validate, async () => {
			if (audio) {
				await onSubmit($state.snapshot(values), audio, idempotencyKey);
			}
		});
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
	<ProblemAlert
		problem={form.problem}
		inlineFields={[...Object.keys(values), 'durationSeconds', 'audio']}
	/>
	{#if successMessage}
		<p class="notice success" role="status">{successMessage}</p>
	{/if}
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
	<div class="actions">
		<button type="submit" disabled={form.pending}>
			{form.pending ? 'Envoi en cours…' : 'Publier l’épisode'}
		</button>
	</div>
</form>
