<script lang="ts">
	import { MINIMUM_PASSWORD_LENGTH, validatePassword } from '../forms/admin-user-form';
	import { singleFieldError } from '../forms/field-errors';
	import { FormState } from '../forms/form-state.svelte';
	import FormField from './FormField.svelte';
	import PendingButton from './PendingButton.svelte';
	import ProblemAlert from './ProblemAlert.svelte';
	import StatusMessage from './StatusMessage.svelte';

	interface Props {
		/** Distinguishes the field ids when several forms are on the page. */
		idPrefix: string;
		/** Account whose password changes: tells apart the buttons of several forms. */
		username: string;
		onSubmit: (password: string) => Promise<void>;
	}

	let { idPrefix, username, onSubmit }: Props = $props();

	let password = $state('');
	let successMessage = $state('');
	let formElement: HTMLFormElement | undefined = $state();
	const form = new FormState();

	async function handleSubmit(event: SubmitEvent): Promise<void> {
		event.preventDefault();
		successMessage = '';
		const changed = await form.submit(
			() => singleFieldError('password', validatePassword(password)),
			() => onSubmit(password),
			formElement
		);
		if (changed) {
			password = '';
			successMessage = `Mot de passe de « ${username} » modifié.`;
		}
	}
</script>

<form novalidate onsubmit={handleSubmit} bind:this={formElement}>
	<FormField
		id="{idPrefix}-new-password"
		label="Nouveau mot de passe de « {username} »"
		required
		error={form.fieldErrors.password}
		hint="{MINIMUM_PASSWORD_LENGTH} caractères minimum."
	>
		{#snippet control(attributes)}
			<input {...attributes} type="password" bind:value={password} autocomplete="new-password" />
		{/snippet}
	</FormField>
	<ProblemAlert problem={form.problem} inlineFields={['password']} />
	<div class="actions">
		<PendingButton type="submit" pending={form.pending} pendingLabel="Modification…">
			Changer le mot de passe<span class="visually-hidden"> de « {username} »</span>
		</PendingButton>
		<StatusMessage message={successMessage} />
	</div>
</form>
