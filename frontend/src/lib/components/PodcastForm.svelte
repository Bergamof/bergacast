<script lang="ts">
	import { untrack } from 'svelte';
	import type { FeedPublication } from '../api/resources';
	import { EditableForm } from '../forms/editable-form.svelte';
	import {
		SLUG_MAX_LENGTH,
		validatePodcastForm,
		type PodcastFormValues
	} from '../forms/podcast-form';
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

	const editable = new EditableForm(
		() => initialValues,
		untrack(() => successMessage)
	);
	const errors = $derived(editable.submission.fieldErrors);
	let formElement: HTMLFormElement | undefined = $state();

	async function handleSubmit(event: SubmitEvent): Promise<void> {
		event.preventDefault();
		await editable.save(validatePodcastForm, onSubmit, formElement);
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
						bind:value={editable.values.slug}
						maxlength={SLUG_MAX_LENGTH}
						autocomplete="off"
						spellcheck="false"
					/>
				{/snippet}
			</FormField>
		{:else}
			<div>
				<p><strong>Identifiant (slug)</strong> : <code>{editable.values.slug}</code></p>
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
					<input {...attributes} bind:value={editable.values.title} />
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
					<input {...attributes} bind:value={editable.values.language} autocomplete="off" />
				{/snippet}
			</FormField>
		</div>

		<FormField id="description" label="Description" required error={errors.description}>
			{#snippet control(attributes)}
				<textarea {...attributes} bind:value={editable.values.description}></textarea>
			{/snippet}
		</FormField>

		<div class="form-grid">
			<FormField id="author" label="Auteur" error={errors.author}>
				{#snippet control(attributes)}
					<input {...attributes} bind:value={editable.values.author} />
				{/snippet}
			</FormField>
			<FormField
				id="category"
				label="Catégorie Apple Podcasts"
				error={errors.category}
				hint="Par exemple « Technology » ou « Society & Culture »."
			>
				{#snippet control(attributes)}
					<input {...attributes} bind:value={editable.values.category} />
				{/snippet}
			</FormField>
			<FormField id="ownerName" label="Nom du propriétaire" error={errors.ownerName}>
				{#snippet control(attributes)}
					<input {...attributes} bind:value={editable.values.ownerName} autocomplete="name" />
				{/snippet}
			</FormField>
			<FormField id="ownerEmail" label="E-mail du propriétaire" error={errors.ownerEmail}>
				{#snippet control(attributes)}
					<input
						{...attributes}
						type="email"
						bind:value={editable.values.ownerEmail}
						autocomplete="email"
					/>
				{/snippet}
			</FormField>
			<FormField
				id="imageUrl"
				label="URL de l’image (couverture)"
				hint="Utilisée dans le flux seulement si aucune image n’a été envoyée."
				error={errors.imageUrl}
			>
				{#snippet control(attributes)}
					<input {...attributes} type="url" bind:value={editable.values.imageUrl} />
				{/snippet}
			</FormField>
			<FormField id="websiteUrl" label="URL du site web" error={errors.websiteUrl}>
				{#snippet control(attributes)}
					<input {...attributes} type="url" bind:value={editable.values.websiteUrl} />
				{/snippet}
			</FormField>
		</div>

		<label class="choice">
			<input type="checkbox" bind:checked={editable.values.explicit} />
			Contenu explicite
		</label>
	</fieldset>

	<fieldset>
		<legend>Réglages du flux RSS</legend>

		<fieldset class="nested">
			<legend>Épisodes publiés dans le flux</legend>
			<label class="choice">
				<input
					type="radio"
					name="limitEpisodes"
					value={false}
					bind:group={editable.values.limitEpisodes}
				/>
				Tous les épisodes
			</label>
			<label class="choice">
				<input
					type="radio"
					name="limitEpisodes"
					value={true}
					bind:group={editable.values.limitEpisodes}
				/>
				Seulement les plus récents
			</label>
		</fieldset>

		<div class="form-grid">
			<FormField
				id="maxEpisodes"
				label="Nombre maximal d’épisodes"
				error={errors.maxEpisodes}
				required={editable.values.limitEpisodes}
			>
				{#snippet control(attributes)}
					<input
						{...attributes}
						type="number"
						min="1"
						step="1"
						bind:value={editable.values.maxEpisodes}
						disabled={!editable.values.limitEpisodes}
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
						bind:value={editable.values.evictOldestBy}
						disabled={!editable.values.limitEpisodes}
					>
						<option value="PUBLICATION_DATE">la date de publication</option>
						<option value="ADDED_DATE">la date d’ajout</option>
					</select>
				{/snippet}
			</FormField>
			<FormField id="sortBy" label="Trier par" error={errors.sortBy}>
				{#snippet control(attributes)}
					<select {...attributes} bind:value={editable.values.sortBy}>
						<option value="PUBLICATION_DATE">Date de publication</option>
						<option value="ADDED_DATE">Date d’ajout</option>
					</select>
				{/snippet}
			</FormField>
			<FormField id="sortDirection" label="Ordre" error={errors.sortDirection}>
				{#snippet control(attributes)}
					<select {...attributes} bind:value={editable.values.sortDirection}>
						<option value="DESC">Du plus récent au plus ancien</option>
						<option value="ASC">Du plus ancien au plus récent</option>
					</select>
				{/snippet}
			</FormField>
		</div>

		<label class="choice">
			<input type="checkbox" bind:checked={editable.values.includeFutureEpisodes} />
			Inclure les épisodes programmés (date de publication future)
		</label>
	</fieldset>

	<ProblemAlert problem={editable.submission.problem} inlineFields={Object.keys(editable.values)} />
	<RecoveryActions
		problem={editable.submission.problem}
		onReload={onReload ? () => editable.reload(onReload, formElement) : undefined}
		{onPublishFeed}
	/>
	<div class="actions">
		<PendingButton
			type="submit"
			pending={editable.submission.pending}
			pendingLabel="Enregistrement…"
		>
			{submitLabel}
		</PendingButton>
		<StatusMessage message={editable.statusMessage} />
	</div>
</form>

<style>
	.nested {
		border: none;
		padding: 0;
		gap: var(--space-xs);
	}
</style>
