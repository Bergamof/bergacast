<script lang="ts">
	import { expiryDelay, renewalDelay } from '../auth/session-renewal';
	import type { Session } from '../auth/session-store';
	import { singleFieldError } from '../forms/field-errors';
	import { FormState } from '../forms/form-state.svelte';
	import { REQUIRED_MESSAGE } from '../forms/validators';
	import FormField from './FormField.svelte';
	import ModalDialog from './ModalDialog.svelte';
	import PendingButton from './PendingButton.svelte';
	import ProblemAlert from './ProblemAlert.svelte';

	interface Props {
		session: Session;
		/** Renews the token with the password; the page and its unsaved work stay. */
		onRenew: (password: string) => Promise<void>;
		onLogOut: () => void;
		/** The dialog opened: the page defers whatever would take the user away from it. */
		onopen?: () => void;
		/** The dialog closed, `renewed` once the session was renewed in place. */
		onclose?: (renewed: boolean) => void;
	}

	let { session, onRenew, onLogOut, onopen, onclose }: Props = $props();

	const uid = $props.id();
	let modal: ModalDialog | undefined = $state();
	let expired = $state(false);
	let password = $state('');
	let renewedInPlace = false;
	const form = new FormState();

	// The JWT cannot be refreshed (stateless API): asking the password again before it expires
	// keeps the user on the page, with unsaved work and running uploads (WCAG 2.2.1). Each session
	// (a renewed one included) starts unexpired and schedules its own notice and expiry.
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
		if (!modal?.isOpen()) {
			form.reset();
			onopen?.();
			modal?.open();
		}
	}

	function closed(): void {
		const renewed = renewedInPlace;
		renewedInPlace = false;
		password = '';
		onclose?.(renewed);
	}

	async function renew(
		event: SubmitEvent & { currentTarget: EventTarget & HTMLFormElement }
	): Promise<void> {
		event.preventDefault();
		const renewed = await form.submit(
			() => singleFieldError('password', password.length === 0 ? REQUIRED_MESSAGE : null),
			() => onRenew(password),
			event.currentTarget
		);
		if (renewed) {
			renewedInPlace = true;
			// Closing a modal dialog gives the focus back to where it was before it opened.
			modal?.close();
		}
	}
</script>

<!-- Once expired, the page must not stay readable on an unattended screen: opaque backdrop, no
	Escape, only « Rester connecté » or « Se déconnecter ». -->
<ModalDialog
	bind:this={modal}
	title={expired ? 'Session expirée' : 'Session bientôt expirée'}
	dismissible={!form.pending && !expired}
	opaque={expired}
	onclose={closed}
>
	{#snippet description()}
		<p>
			{#if expired}
				Votre session a expiré. Saisissez votre mot de passe pour reprendre là où vous en étiez.
			{:else}
				Votre session expire dans moins d’une minute. Saisissez votre mot de passe pour rester
				connecté sans perdre votre travail en cours.
			{/if}
		</p>
	{/snippet}
	<!-- The title changes once expired, which is not announced: this region says it. -->
	<p class="visually-hidden" role="status">{expired ? 'Votre session a expiré.' : ''}</p>
	<form novalidate onsubmit={renew}>
		<!-- Lets password managers fill the right account (they ignore type="hidden"). -->
		<input
			type="text"
			name="username"
			autocomplete="username"
			value={session.username}
			readonly
			hidden
		/>
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
</ModalDialog>
