<script lang="ts">
	import type { EpisodeFormValues } from '../forms/episode-form';
	import type { FieldErrors } from '../forms/field-errors';
	import FormField from './FormField.svelte';

	interface Props {
		values: EpisodeFormValues;
		errors: FieldErrors;
		publicationDateRequired: boolean;
		/** The GUID can only be chosen at upload time: podcast apps rely on it to identify episodes. */
		guidEditable: boolean;
	}

	let { values = $bindable(), errors, publicationDateRequired, guidEditable }: Props = $props();
</script>

<div class="form-grid">
	<FormField id="episode-title" label="Titre" required error={errors.title}>
		{#snippet control(attributes)}
			<input {...attributes} bind:value={values.title} />
		{/snippet}
	</FormField>
	<FormField
		id="episode-publication-date"
		label="Date de publication"
		required={publicationDateRequired}
		error={errors.publicationDate}
		hint={publicationDateRequired
			? 'Heure locale. Une date future programme l’épisode.'
			: 'Heure locale. Vide : maintenant. Une date future programme l’épisode.'}
	>
		{#snippet control(attributes)}
			<input {...attributes} type="datetime-local" bind:value={values.publicationDate} />
		{/snippet}
	</FormField>
</div>

<FormField id="episode-description" label="Description" error={errors.description}>
	{#snippet control(attributes)}
		<textarea {...attributes} bind:value={values.description}></textarea>
	{/snippet}
</FormField>

<div class="form-grid">
	<FormField
		id="episode-duration"
		label="Durée"
		error={errors.durationSeconds}
		hint="h:mm:ss, m:ss ou nombre de secondes."
	>
		{#snippet control(attributes)}
			<input {...attributes} bind:value={values.duration} inputmode="numeric" />
		{/snippet}
	</FormField>
	<FormField id="episode-season" label="Saison" error={errors.season}>
		{#snippet control(attributes)}
			<input {...attributes} type="number" min="1" step="1" bind:value={values.season} />
		{/snippet}
	</FormField>
	<FormField id="episode-number" label="Numéro d’épisode" error={errors.episodeNumber}>
		{#snippet control(attributes)}
			<input {...attributes} type="number" min="1" step="1" bind:value={values.episodeNumber} />
		{/snippet}
	</FormField>
	<FormField id="episode-type" label="Type" error={errors.episodeType}>
		{#snippet control(attributes)}
			<select {...attributes} bind:value={values.episodeType}>
				<option value="FULL">Complet</option>
				<option value="TRAILER">Bande-annonce</option>
				<option value="BONUS">Bonus</option>
			</select>
		{/snippet}
	</FormField>
	<FormField id="episode-explicit" label="Contenu explicite" error={errors.explicit}>
		{#snippet control(attributes)}
			<select {...attributes} bind:value={values.explicit}>
				<option value="inherit">Comme le podcast</option>
				<option value="yes">Oui</option>
				<option value="no">Non</option>
			</select>
		{/snippet}
	</FormField>
	{#if guidEditable}
		<FormField
			id="episode-guid"
			label="GUID"
			error={errors.guid}
			hint="Facultatif : un identifiant unique est généré si vide."
		>
			{#snippet control(attributes)}
				<input {...attributes} bind:value={values.guid} autocomplete="off" spellcheck="false" />
			{/snippet}
		</FormField>
	{:else}
		<div>
			<p><strong>GUID</strong></p>
			<code>{values.guid}</code>
		</div>
	{/if}
</div>
