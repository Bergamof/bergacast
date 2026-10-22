<script lang="ts">
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
	import ProblemAlert from '#lib/components/ProblemAlert.svelte';
	import { FormState } from '#lib/forms/form-state.svelte.ts';
	import { REQUIRED_MESSAGE, isBlank } from '#lib/forms/validators.ts';
	import type { FieldErrors } from '#lib/forms/field-errors.ts';

	let username = $state('');
	let password = $state('');
	const form = new FormState();
	const sessionExpired = $derived(page.url.searchParams.has(EXPIRED_PARAMETER));

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

	async function handleSubmit(event: SubmitEvent): Promise<void> {
		event.preventDefault();
		await form.submit(validate, async () => {
			const token = await api.logIn(username.trim(), password);
			sessionStore.save(token.accessToken, username.trim(), token.expiresIn);
			const target = safeRedirectTarget(
				page.url.searchParams.get(REDIRECT_PARAMETER),
				resolve('/(app)'),
				resolve('/(app)/podcasts')
			);
			// eslint-disable-next-line svelte/no-navigation-without-resolve -- target is either resolved or checked to stay under the base path
			await goto(target, { replaceState: true });
		});
	}
</script>

<svelte:head>
	<title>Connexion — Bergacast</title>
</svelte:head>

<main class="login">
	<h1>Bergacast</h1>
	<section class="card" aria-labelledby="login-title">
		<h2 id="login-title">Connexion à l’administration</h2>
		{#if sessionExpired}
			<p class="notice warning" role="status">Votre session a expiré. Reconnectez-vous.</p>
		{/if}
		<form novalidate onsubmit={handleSubmit}>
			<ProblemAlert problem={form.problem} inlineFields={['username', 'password']} />
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
			<button type="submit" disabled={form.pending}>
				{form.pending ? 'Connexion…' : 'Se connecter'}
			</button>
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
