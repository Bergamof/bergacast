<script lang="ts">
	import { untrack } from 'svelte';
	import { validateEpisodeForm, type EpisodeFormValues } from '../forms/episode-form';
	import { FormState } from '../forms/form-state.svelte';
	import EpisodeFields from './EpisodeFields.svelte';
	import ProblemAlert from './ProblemAlert.svelte';

	interface Props {
		initialValues: EpisodeFormValues;
		onSubmit: (values: EpisodeFormValues) => Promise<void>;
	}

	let { initialValues, onSubmit }: Props = $props();

	// The form edits its own copy; parents re-create the form (`{#key}`) to load new values.
	let values = $state(untrack(() => structuredClone(initialValues)));
	const form = new FormState();

	async function handleSubmit(event: SubmitEvent): Promise<void> {
		event.preventDefault();
		await form.submit(
			() => validateEpisodeForm(values, { publicationDateRequired: true }),
			() => onSubmit($state.snapshot(values))
		);
	}
</script>

<form novalidate onsubmit={handleSubmit}>
	<ProblemAlert problem={form.problem} inlineFields={[...Object.keys(values), 'durationSeconds']} />
	<EpisodeFields
		bind:values
		errors={form.fieldErrors}
		publicationDateRequired={true}
		guidEditable={false}
	/>
	<div class="actions">
		<button type="submit" disabled={form.pending}>
			{form.pending ? 'Enregistrement…' : 'Enregistrer'}
		</button>
	</div>
</form>
