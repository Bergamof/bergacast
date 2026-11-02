<script lang="ts">
	import { tick, untrack } from 'svelte';
	import type { FeedPublication } from '../api/resources';
	import { validateEpisodeForm, type EpisodeFormValues } from '../forms/episode-form';
	import { FormState } from '../forms/form-state.svelte';
	import EpisodeFields from './EpisodeFields.svelte';
	import PendingButton from './PendingButton.svelte';
	import ProblemAlert from './ProblemAlert.svelte';
	import RecoveryActions from './RecoveryActions.svelte';
	import RequiredFieldsLegend from './RequiredFieldsLegend.svelte';
	import StatusMessage from './StatusMessage.svelte';

	interface Props {
		initialValues: EpisodeFormValues;
		onSubmit: (values: EpisodeFormValues) => Promise<void>;
		/** Reloads the latest version of the episode, offered after a concurrent change (412). */
		onReload: () => Promise<void>;
		/** Offered after a 503, when the change may be saved but the feed not written. */
		onPublishFeed: () => Promise<FeedPublication>;
	}

	let { initialValues, onSubmit, onReload, onPublishFeed }: Props = $props();

	// The form edits its own copy, reloaded from `initialValues` once saved or reloaded. It is never
	// re-created, so that the focus stays where it was.
	let values = $state(untrack(() => structuredClone(initialValues)));
	let statusMessage = $state('');
	let formElement: HTMLFormElement | undefined = $state();
	const form = new FormState();

	async function loadInitialValues(): Promise<void> {
		await tick();
		values = structuredClone(initialValues);
	}

	async function handleSubmit(event: SubmitEvent): Promise<void> {
		event.preventDefault();
		statusMessage = '';
		const saved = await form.submit(
			() => validateEpisodeForm(values, { publicationDateRequired: true }),
			() => onSubmit($state.snapshot(values)),
			formElement
		);
		if (saved) {
			await loadInitialValues();
			statusMessage = 'Modifications enregistrées.';
		}
	}

	async function reload(): Promise<void> {
		await onReload();
		await loadInitialValues();
		form.reset();
		statusMessage = 'Dernière version chargée : refaites vos changements.';
		formElement?.querySelector<HTMLElement>('input, select, textarea')?.focus();
	}
</script>

<form novalidate onsubmit={handleSubmit} bind:this={formElement}>
	<RequiredFieldsLegend />
	<EpisodeFields
		bind:values
		errors={form.fieldErrors}
		publicationDateRequired={true}
		guidEditable={false}
	/>
	<ProblemAlert problem={form.problem} inlineFields={[...Object.keys(values), 'durationSeconds']} />
	<RecoveryActions problem={form.problem} onReload={reload} {onPublishFeed} />
	<div class="actions">
		<PendingButton type="submit" pending={form.pending} pendingLabel="Enregistrement…">
			Enregistrer
		</PendingButton>
		<StatusMessage message={statusMessage} />
	</div>
</form>
