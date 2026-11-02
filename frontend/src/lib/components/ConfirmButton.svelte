<script lang="ts">
	import type { Snippet } from 'svelte';
	import { problemFromError, type ProblemDetails } from '../api/problem';
	import ModalDialog from './ModalDialog.svelte';
	import PendingButton from './PendingButton.svelte';
	import ProblemAlert from './ProblemAlert.svelte';

	interface Props {
		/** Text of the button opening the dialog. */
		label: string;
		/** Accessible name of the button when its visible label is ambiguous (e.g. in a table row). */
		accessibleLabel?: string;
		title: string;
		confirmLabel?: string;
		onConfirm: () => Promise<void>;
		/**
		 * Called once the dialog is closed after the action succeeded, e.g. to move the focus to a
		 * stable element when the opening button is gone with what it deleted.
		 */
		afterConfirm?: () => void | Promise<void>;
		children: Snippet;
	}

	let {
		label,
		accessibleLabel,
		title,
		confirmLabel = 'Supprimer',
		onConfirm,
		afterConfirm,
		children
	}: Props = $props();

	let modal: ModalDialog | undefined = $state();
	let cancelButton: HTMLButtonElement | undefined = $state();
	let pending = $state(false);
	let problem = $state<ProblemDetails | null>(null);

	function open(): void {
		problem = null;
		modal?.open();
		// The safe choice gets the focus, not the destructive one.
		cancelButton?.focus();
	}

	function cancel(): void {
		if (!pending) {
			modal?.close();
		}
	}

	async function confirm(): Promise<void> {
		pending = true;
		problem = null;
		try {
			await onConfirm();
		} catch (error) {
			problem = problemFromError(error);
			return;
		} finally {
			pending = false;
		}
		modal?.close();
		await afterConfirm?.();
	}
</script>

<button type="button" class="danger" aria-label={accessibleLabel} onclick={open}>{label}</button>

<!-- Not dismissible while the request runs: closing would hide the error it may end with. -->
<ModalDialog bind:this={modal} {title} dismissible={!pending}>
	{#snippet description()}{@render children()}{/snippet}
	<div class="problem"><ProblemAlert {problem} /></div>
	<div class="actions">
		<PendingButton
			type="button"
			class="danger"
			{pending}
			pendingLabel="{confirmLabel}…"
			onclick={confirm}
		>
			{confirmLabel}
		</PendingButton>
		<button
			type="button"
			class="secondary"
			aria-disabled={pending ? 'true' : undefined}
			bind:this={cancelButton}
			onclick={cancel}
		>
			Annuler
		</button>
	</div>
</ModalDialog>

<style>
	.problem:not(:empty) {
		margin-bottom: var(--space-m);
	}
</style>
