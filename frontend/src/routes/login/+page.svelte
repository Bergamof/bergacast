<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { page } from '$app/state';
	import { api, sessionStore } from '#lib/app.ts';
	import {
		EXPIRED_PARAMETER,
		REDIRECT_PARAMETER,
		safeRedirectTarget
	} from '#lib/auth/login-redirect.ts';
	import FormField from '#lib/components/FormField.svelte';
	import PendingButton from '#lib/components/PendingButton.svelte';
	import ProblemAlert from '#lib/components/ProblemAlert.svelte';
	import RequiredFieldsLegend from '#lib/components/RequiredFieldsLegend.svelte';
	import StatusMessage from '#lib/components/StatusMessage.svelte';
	import { FormState } from '#lib/forms/form-state.svelte.ts';
	import { REQUIRED_MESSAGE, isBlank } from '#lib/forms/validators.ts';
	import type { FieldErrors } from '#lib/forms/field-errors.ts';

	let username = $state('');
	let password = $state('');
	const form = new FormState();
	let expiryMessage = $state('');

	// Written once mounted, into a status region already in the page, so that it is announced.
	onMount(() => {
		if (page.url.searchParams.has(EXPIRED_PARAMETER)) {
			expiryMessage = 'Votre session a expiré. Reconnectez-vous.';
		}
	});

	function validate(): FieldErrors {
		const errors: FieldErrors = {};
		if (isBlank(username)) {
			errors.username = REQUIRED_MESSAGE;
		}
		if (password.length === 0) {
			errors.password = REQUIRED_MESSAGE;
		}
		return errors;
	}

	async function handleSubmit(
		event: SubmitEvent & { currentTarget: EventTarget & HTMLFormElement }
	): Promise<void> {
		event.preventDefault();
		await form.submit(
			validate,
			async () => {
				const token = await api.logIn(username.trim(), password);
				sessionStore.save(token.accessToken, username.trim(), token.expiresIn);
				const target = safeRedirectTarget(
					page.url.searchParams.get(REDIRECT_PARAMETER),
					resolve('/(app)'),
					resolve('/(app)/podcasts')
				);
				// eslint-disable-next-line svelte/no-navigation-without-resolve -- target is either resolved or checked to stay under the base path
				await goto(target, { replaceState: true });
			},
			event.currentTarget
		);
	}
</script>

<svelte:head>
	<title>Connexion — Bergacast</title>
</svelte:head>

<main class="login">
	<h1>Bergacast</h1>
	<section class="card" aria-labelledby="login-title">
		<h2 id="login-title">Connexion à l’administration</h2>
		<StatusMessage message={expiryMessage} tone="warning" />
		<form novalidate onsubmit={handleSubmit}>
			<RequiredFieldsLegend />
			<FormField id="username" label="Identifiant" required error={form.fieldErrors.username}>
				{#snippet control(attributes)}
					<input {...attributes} bind:value={username} autocomplete="username" />
				{/snippet}
			</FormField>
			<FormField id="password" label="Mot de passe" required error={form.fieldErrors.password}>
				{#snippet control(attributes)}
					<input
						{...attributes}
						type="password"
						bind:value={password}
						autocomplete="current-password"
					/>
				{/snippet}
			</FormField>
			<ProblemAlert problem={form.problem} inlineFields={['username', 'password']} />
			<PendingButton type="submit" pending={form.pending} pendingLabel="Connexion…">
				Se connecter
			</PendingButton>
		</form>
	</section>
</main>

<style>
	.login {
		max-width: 26rem;
		margin: 0 auto;
		padding: var(--space-xl) var(--space-m);
	}

	h1 {
		text-align: center;
	}
</style>
