<script lang="ts">
	import { api, sessionStore } from '../app';
	import { expiryDelay, renewalDelay } from '../auth/session-renewal';
	import type { Session } from '../auth/session-store';
	import { singleFieldError } from '../forms/field-errors';
	import { FormState } from '../forms/form-state.svelte';
	import { REQUIRED_MESSAGE } from '../forms/validators';
	import FormField from './FormField.svelte';
	import PendingButton from './PendingButton.svelte';
	import ProblemAlert from './ProblemAlert.svelte';

	interface Props {
		session: Session;
		/** The token was renewed in place: the page and its unsaved work stay. */
		onRenewed: (session: Session) => void;
		onLogOut: () => void;
	}

	let { session, onRenewed, onLogOut }: Props = $props();

	const uid = $props.id();
	const titleId = `${uid}-title`;
	const descriptionId = `${uid}-description`;
	let dialog: HTMLDialogElement | undefined = $state();
	let expired = $state(false);
	let password = $state('');
	const form = new FormState();

	// The JWT cannot be refreshed (stateless API): asking the password again before it expires
	// keeps the user on the page, with unsaved work and running uploads (WCAG 2.2.1).
	$effect(() => {
		const current = session;
		expired = false;
		const now = Date.now();
		const timers = [
			setTimeout(open, renewalDelay(current, now)),
			setTimeout(
				() => {
					expired = true;
					open();
				},
				expiryDelay(current, now)
			)
		];
		return () => timers.forEach(clearTimeout);
	});

	function open(): void {
		if (dialog && !dialog.open) {
			password = '';
			form.reset();
			dialog.showModal();
		}
	}

	async function renew(
		event: SubmitEvent & { currentTarget: EventTarget & HTMLFormElement }
	): Promise<void> {
		event.preventDefault();
		const renewed = await form.submit(
			() => singleFieldError('password', password.length === 0 ? REQUIRED_MESSAGE : null),
			async () => {
				const token = await api.logIn(session.username, password);
				onRenewed(sessionStore.save(token.accessToken, session.username, token.expiresIn));
			},
			event.currentTarget
		);
		if (renewed) {
			password = '';
			// Closing a modal dialog gives the focus back to where it was before it opened.
			dialog?.close();
		}
	}
</script>

<dialog
	bind:this={dialog}
	role="alertdialog"
	aria-labelledby={titleId}
	aria-describedby={descriptionId}
	closedby="closerequest"
>
	<h2 id={titleId}>{expired ? 'Session expirée' : 'Session bientôt expirée'}</h2>
	<p id={descriptionId}>
		{#if expired}
			Votre session a expiré. Saisissez votre mot de passe pour reprendre là où vous en étiez.
		{:else}
			Votre session expire dans moins d’une minute. Saisissez votre mot de passe pour rester
			connecté sans perdre votre travail en cours.
		{/if}
	</p>
	<form novalidate onsubmit={renew}>
		<!-- Lets password managers fill the right account. -->
		<input type="hidden" name="username" autocomplete="username" value={session.username} />
		<FormField
			id="{uid}-password"
			label="Mot de passe de « {session.username} »"
			required
			error={form.fieldErrors.password}
		>
			{#snippet control(attributes)}
				<input
					{...attributes}
					type="password"
					bind:value={password}
					autocomplete="current-password"
				/>
			{/snippet}
		</FormField>
		<ProblemAlert problem={form.problem} inlineFields={['password']} />
		<div class="actions">
			<PendingButton type="submit" pending={form.pending} pendingLabel="Connexion…">
				Rester connecté
			</PendingButton>
			<button type="button" class="secondary" onclick={onLogOut}>Se déconnecter</button>
		</div>
	</form>
</dialog>

<style>
	dialog {
		max-width: min(30rem, calc(100vw - 2 * var(--space-m)));
		padding: var(--space-l);
		color: var(--color-text);
		background: var(--color-surface);
		border: 1px solid var(--color-border);
		border-radius: var(--radius);
	}

	dialog::backdrop {
		background: rgb(0 0 0 / 50%);
	}
</style>
