<script lang="ts">
	import type { Snippet } from 'svelte';
	import { problemFromError, type ProblemDetails } from '../api/problem';
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

	const uid = $props.id();
	const titleId = `${uid}-title`;
	const descriptionId = `${uid}-description`;
	let dialog: HTMLDialogElement | undefined = $state();
	let cancelButton: HTMLButtonElement | undefined = $state();
	let pending = $state(false);
	let problem = $state<ProblemDetails | null>(null);

	function open(): void {
		problem = null;
		dialog?.showModal();
		// The safe choice gets the focus, not the destructive one.
		cancelButton?.focus();
	}

	function cancel(): void {
		if (!pending) {
			dialog?.close();
		}
	}

	// Escape would hide the dialog while the request runs, and with it the error it may end with.
	// `closedby` prevents it; browsers without it only let `cancel` be prevented once per user
	// activation, hence reopening a dialog closed while pending.
	function preventCancelWhilePending(event: Event): void {
		if (pending) {
			event.preventDefault();
		}
	}

	function reopenWhilePending(): void {
		if (pending) {
			dialog?.showModal();
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
			// Before closing: a dialog closed while pending is reopened (`reopenWhilePending`).
			pending = false;
		}
		dialog?.close();
		await afterConfirm?.();
	}
</script>

<button type="button" class="danger" aria-label={accessibleLabel} onclick={open}>{label}</button>

<dialog
	bind:this={dialog}
	role="alertdialog"
	aria-labelledby={titleId}
	aria-describedby={descriptionId}
	closedby={pending ? 'none' : 'closerequest'}
	oncancel={preventCancelWhilePending}
	onclose={reopenWhilePending}
>
	<h2 id={titleId}>{title}</h2>
	<div class="content">
		<div id={descriptionId}>{@render children()}</div>
		<ProblemAlert {problem} />
	</div>
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
</dialog>

<style>
	dialog {
		max-width: min(32rem, calc(100vw - 2 * var(--space-m)));
		padding: var(--space-l);
		color: var(--color-text);
		background: var(--color-surface);
		border: 1px solid var(--color-border);
		border-radius: var(--radius);
	}

	dialog::backdrop {
		background: rgb(0 0 0 / 50%);
	}

	.content {
		display: grid;
		gap: var(--space-m);
		margin-bottom: var(--space-l);
	}
</style>
