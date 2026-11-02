<script lang="ts">
	import type { Snippet } from 'svelte';

	interface Props {
		title: string;
		/** Escape and outside closings are refused while false (e.g. a request is running). */
		dismissible: boolean;
		/** Hides the page behind the dialog, e.g. once the session has expired. */
		opaque?: boolean;
		/** Read as the dialog description (`aria-describedby`). */
		description: Snippet;
		children: Snippet;
		onclose?: () => void;
	}

	let { title, dismissible, opaque = false, description, children, onclose }: Props = $props();

	const uid = $props.id();
	let dialog: HTMLDialogElement | undefined = $state();
	let closingOnPurpose = false;

	export function open(): void {
		if (dialog && !dialog.open) {
			dialog.showModal();
		}
	}

	/** Closes the dialog whatever `dismissible` says: the page decided it. */
	export function close(): void {
		if (dialog?.open) {
			closingOnPurpose = true;
			dialog.close();
		}
	}

	export function isOpen(): boolean {
		return dialog?.open ?? false;
	}

	function preventCancelUnlessDismissible(event: Event): void {
		if (!dismissible) {
			event.preventDefault();
		}
	}

	// `closedby` keeps the dialog open where supported; elsewhere `cancel` can only be prevented once
	// per user activation, hence reopening a dialog the user closed while it was not dismissible.
	function handleClose(): void {
		const reopen = !closingOnPurpose && !dismissible;
		closingOnPurpose = false;
		if (reopen) {
			dialog?.showModal();
			return;
		}
		onclose?.();
	}
</script>

<dialog
	bind:this={dialog}
	class:opaque
	role="alertdialog"
	aria-labelledby="{uid}-title"
	aria-describedby="{uid}-description"
	closedby={dismissible ? 'closerequest' : 'none'}
	oncancel={preventCancelUnlessDismissible}
	onclose={handleClose}
>
	<h2 id="{uid}-title">{title}</h2>
	<div id="{uid}-description" class="description">{@render description()}</div>
	{@render children()}
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

	dialog.opaque::backdrop {
		background: var(--color-background);
	}

	.description {
		margin-bottom: var(--space-m);
	}
</style>
