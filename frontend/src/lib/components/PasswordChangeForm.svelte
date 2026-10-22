<script lang="ts">
	import { MINIMUM_PASSWORD_LENGTH, validatePassword } from '../forms/admin-user-form';
	import { singleFieldError } from '../forms/field-errors';
	import { FormState } from '../forms/form-state.svelte';
	import FormField from './FormField.svelte';
	import ProblemAlert from './ProblemAlert.svelte';

	interface Props {
		/** Distinguishes the field ids when several forms are on the page. */
		idPrefix: string;
		onSubmit: (password: string) => Promise<void>;
	}

	let { idPrefix, onSubmit }: Props = $props();

	let password = $state('');
	let successMessage = $state('');
	const form = new FormState();

	async function handleSubmit(event: SubmitEvent): Promise<void> {
		event.preventDefault();
		successMessage = '';
		const changed = await form.submit(
			() => singleFieldError('password', validatePassword(password)),
			() => onSubmit(password)
		);
		if (changed) {
			password = '';
			successMessage = 'Mot de passe modifié.';
		}
	}
</script>

<form novalidate onsubmit={handleSubmit}>
	<ProblemAlert problem={form.problem} inlineFields={['password']} />
	{#if successMessage}
		<p class="notice success" role="status">{successMessage}</p>
	{/if}
	<FormField
		id="{idPrefix}-new-password"
		label="Nouveau mot de passe"
		required
		error={form.fieldErrors.password}
		hint="{MINIMUM_PASSWORD_LENGTH} caractères minimum."
	>
		{#snippet control(attributes)}
			<input {...attributes} type="password" bind:value={password} autocomplete="new-password" />
		{/snippet}
	</FormField>
	<div class="actions">
		<button type="submit" disabled={form.pending}>
			{form.pending ? 'Modification…' : 'Changer le mot de passe'}
		</button>
	</div>
</form>
