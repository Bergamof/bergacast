<script lang="ts">
	import { tick, untrack } from 'svelte';
	import type { FeedPublication } from '../api/resources';
	import {
		SLUG_MAX_LENGTH,
		validatePodcastForm,
		type PodcastFormValues
	} from '../forms/podcast-form';
	import { FormState } from '../forms/form-state.svelte';
	import FormField from './FormField.svelte';
	import PendingButton from './PendingButton.svelte';
	import ProblemAlert from './ProblemAlert.svelte';
	import RecoveryActions from './RecoveryActions.svelte';
	import RequiredFieldsLegend from './RequiredFieldsLegend.svelte';
	import StatusMessage from './StatusMessage.svelte';

	interface Props {
		initialValues: PodcastFormValues;
		/** The slug can only be chosen at creation time. */
		slugEditable: boolean;
		submitLabel: string;
		/** Announced next to the button once saved; none when the page navigates away. */
		successMessage?: string;
		onSubmit: (values: PodcastFormValues) => Promise<void>;
		/** Reloads the latest version of the podcast, offered after a concurrent change (412). */
		onReload?: () => Promise<void>;
		/** Offered after a 503, when the change may be saved but the feed not written. */
		onPublishFeed?: () => Promise<FeedPublication>;
	}

	let {
		initialValues,
		slugEditable,
		submitLabel,
		successMessage = '',
		onSubmit,
		onReload,
		onPublishFeed
	}: Props = $props();

	// The form edits its own copy, reloaded from `initialValues` once saved or reloaded. It is never
	// re-created, so that the focus stays where it was.
	let values = $state(untrack(() => structuredClone(initialValues)));
	let statusMessage = $state('');
	let formElement: HTMLFormElement | undefined = $state();
	const form = new FormState();
	const errors = $derived(form.fieldErrors);

	async function loadInitialValues(): Promise<void> {
		await tick();
		values = structuredClone(initialValues);
	}

	async function handleSubmit(event: SubmitEvent): Promise<void> {
		event.preventDefault();
		statusMessage = '';
		const saved = await form.submit(
			() => validatePodcastForm(values),
			() => onSubmit($state.snapshot(values)),
			formElement
		);
		if (saved) {
			await loadInitialValues();
			statusMessage = successMessage;
		}
	}

	async function reload(): Promise<void> {
		await onReload?.();
		await loadInitialValues();
		form.reset();
		statusMessage = 'Dernière version chargée : refaites vos changements.';
		formElement?.querySelector<HTMLElement>('input, select, textarea')?.focus();
	}
</script>

<form novalidate onsubmit={handleSubmit} bind:this={formElement}>
	<RequiredFieldsLegend />

	<fieldset>
		<legend>Informations générales</legend>
		{#if slugEditable}
			<FormField
				id="slug"
				label="Identifiant (slug)"
				required
				error={errors.slug}
				hint="Lettres minuscules, chiffres et tirets. Il apparaît dans l’URL du flux et ne pourra plus être modifié."
			>
				{#snippet control(attributes)}
					<input
						{...attributes}
						bind:value={values.slug}
						maxlength={SLUG_MAX_LENGTH}
						autocomplete="off"
						spellcheck="false"
					/>
				{/snippet}
			</FormField>
		{:else}
			<div>
				<p><strong>Identifiant (slug)</strong> : <code>{values.slug}</code></p>
				<p class="muted">
					L’identifiant n’est plus modifiable : il fait partie de l’URL du flux RSS et des fichiers
					audio déjà connus des applications d’écoute. Le changer casserait les abonnements
					existants.
				</p>
			</div>
		{/if}

		<div class="form-grid">
			<FormField id="title" label="Titre" required error={errors.title}>
				{#snippet control(attributes)}
					<input {...attributes} bind:value={values.title} />
				{/snippet}
			</FormField>
			<FormField
				id="language"
				label="Langue"
				required
				error={errors.language}
				hint="Code BCP 47, par exemple « fr » ou « fr-CA »."
			>
				{#snippet control(attributes)}
					<input {...attributes} bind:value={values.language} autocomplete="off" />
				{/snippet}
			</FormField>
		</div>

		<FormField id="description" label="Description" required error={errors.description}>
			{#snippet control(attributes)}
				<textarea {...attributes} bind:value={values.description}></textarea>
			{/snippet}
		</FormField>

		<div class="form-grid">
			<FormField id="author" label="Auteur" error={errors.author}>
				{#snippet control(attributes)}
					<input {...attributes} bind:value={values.author} />
				{/snippet}
			</FormField>
			<FormField
				id="category"
				label="Catégorie Apple Podcasts"
				error={errors.category}
				hint="Par exemple « Technology » ou « Society & Culture »."
			>
				{#snippet control(attributes)}
					<input {...attributes} bind:value={values.category} />
				{/snippet}
			</FormField>
			<FormField id="ownerName" label="Nom du propriétaire" error={errors.ownerName}>
				{#snippet control(attributes)}
					<input {...attributes} bind:value={values.ownerName} autocomplete="name" />
				{/snippet}
			</FormField>
			<FormField id="ownerEmail" label="E-mail du propriétaire" error={errors.ownerEmail}>
				{#snippet control(attributes)}
					<input {...attributes} type="email" bind:value={values.ownerEmail} autocomplete="email" />
				{/snippet}
			</FormField>
			<FormField
				id="imageUrl"
				label="URL de l’image (couverture)"
				hint="Utilisée dans le flux seulement si aucune image n’a été envoyée."
				error={errors.imageUrl}
			>
				{#snippet control(attributes)}
					<input {...attributes} type="url" bind:value={values.imageUrl} />
				{/snippet}
			</FormField>
			<FormField id="websiteUrl" label="URL du site web" error={errors.websiteUrl}>
				{#snippet control(attributes)}
					<input {...attributes} type="url" bind:value={values.websiteUrl} />
				{/snippet}
			</FormField>
		</div>

		<label class="choice">
			<input type="checkbox" bind:checked={values.explicit} />
			Contenu explicite
		</label>
	</fieldset>

	<fieldset>
		<legend>Réglages du flux RSS</legend>

		<fieldset class="nested">
			<legend>Épisodes publiés dans le flux</legend>
			<label class="choice">
				<input type="radio" name="limitEpisodes" value={false} bind:group={values.limitEpisodes} />
				Tous les épisodes
			</label>
			<label class="choice">
				<input type="radio" name="limitEpisodes" value={true} bind:group={values.limitEpisodes} />
				Seulement les plus récents
			</label>
		</fieldset>

		<div class="form-grid">
			<FormField
				id="maxEpisodes"
				label="Nombre maximal d’épisodes"
				error={errors.maxEpisodes}
				required={values.limitEpisodes}
			>
				{#snippet control(attributes)}
					<input
						{...attributes}
						type="number"
						min="1"
						step="1"
						bind:value={values.maxEpisodes}
						disabled={!values.limitEpisodes}
					/>
				{/snippet}
			</FormField>
			<FormField
				id="evictOldestBy"
				label="Retirer d’abord les plus anciens selon"
				error={errors.evictOldestBy}
				hint="Uniquement lorsqu’un nombre maximal est défini."
			>
				{#snippet control(attributes)}
					<select
						{...attributes}
						bind:value={values.evictOldestBy}
						disabled={!values.limitEpisodes}
					>
						<option value="PUBLICATION_DATE">la date de publication</option>
						<option value="ADDED_DATE">la date d’ajout</option>
					</select>
				{/snippet}
			</FormField>
			<FormField id="sortBy" label="Trier par" error={errors.sortBy}>
				{#snippet control(attributes)}
					<select {...attributes} bind:value={values.sortBy}>
						<option value="PUBLICATION_DATE">Date de publication</option>
						<option value="ADDED_DATE">Date d’ajout</option>
					</select>
				{/snippet}
			</FormField>
			<FormField id="sortDirection" label="Ordre" error={errors.sortDirection}>
				{#snippet control(attributes)}
					<select {...attributes} bind:value={values.sortDirection}>
						<option value="DESC">Du plus récent au plus ancien</option>
						<option value="ASC">Du plus ancien au plus récent</option>
					</select>
				{/snippet}
			</FormField>
		</div>

		<label class="choice">
			<input type="checkbox" bind:checked={values.includeFutureEpisodes} />
			Inclure les épisodes programmés (date de publication future)
		</label>
	</fieldset>

	<ProblemAlert problem={form.problem} inlineFields={Object.keys(values)} />
	<RecoveryActions
		problem={form.problem}
		onReload={onReload ? reload : undefined}
		{onPublishFeed}
	/>
	<div class="actions">
		<PendingButton type="submit" pending={form.pending} pendingLabel="Enregistrement…">
			{submitLabel}
		</PendingButton>
		<StatusMessage message={statusMessage} />
	</div>
</form>

<style>
	.nested {
		border: none;
		padding: 0;
		gap: var(--space-xs);
	}
</style>
