<script lang="ts">
	import { untrack } from 'svelte';
	import type { FeedPublication } from '../api/resources';
	import { EditableForm } from '../forms/editable-form.svelte';
	import { validateEpisodeForm, type EpisodeFormValues } from '../forms/episode-form';
	import EpisodeFields from './EpisodeFields.svelte';
	import PendingButton from './PendingButton.svelte';
	import ProblemAlert from './ProblemAlert.svelte';
	import RecoveryActions from './RecoveryActions.svelte';
	import RequiredFieldsLegend from './RequiredFieldsLegend.svelte';
	import StatusMessage from './StatusMessage.svelte';

	interface Props {
		initialValues: EpisodeFormValues;
		/** Announced next to the button once saved. */
		successMessage: string;
		onSubmit: (values: EpisodeFormValues) => Promise<void>;
		/** Reloads the latest version of the episode, offered after a concurrent change (412). */
		onReload: () => Promise<void>;
		/** Offered after a 503, when the change may be saved but the feed not written. */
		onPublishFeed: () => Promise<FeedPublication>;
	}

	let { initialValues, successMessage, onSubmit, onReload, onPublishFeed }: Props = $props();

	const editable = new EditableForm(
		() => initialValues,
		untrack(() => successMessage)
	);
	let formElement: HTMLFormElement | undefined = $state();

	async function handleSubmit(event: SubmitEvent): Promise<void> {
		event.preventDefault();
		await editable.save(
			(values) => validateEpisodeForm(values, { publicationDateRequired: true }),
			onSubmit,
			formElement
		);
	}
</script>

<form novalidate onsubmit={handleSubmit} bind:this={formElement}>
	<RequiredFieldsLegend />
	<EpisodeFields
		bind:values={editable.values}
		errors={editable.submission.fieldErrors}
		publicationDateRequired={true}
		guidEditable={false}
	/>
	<ProblemAlert
		problem={editable.submission.problem}
		inlineFields={[...Object.keys(editable.values), 'durationSeconds']}
	/>
	<RecoveryActions
		problem={editable.submission.problem}
		onReload={() => editable.reload(onReload, formElement)}
		{onPublishFeed}
	/>
	<div class="actions">
		<PendingButton
			type="submit"
			pending={editable.submission.pending}
			pendingLabel="Enregistrement…"
		>
			Enregistrer
		</PendingButton>
		<StatusMessage message={editable.statusMessage} />
	</div>
</form>
