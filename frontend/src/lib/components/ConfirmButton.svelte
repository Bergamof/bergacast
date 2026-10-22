<script lang="ts">
	import type { Snippet } from 'svelte';
	import { problemFromError, type ProblemDetails } from '../api/problem';
	import ProblemAlert from './ProblemAlert.svelte';

	interface Props {
		/** Text of the button opening the dialog. */
		label: string;
		/** Accessible name of the button when its visible label is ambiguous (e.g. in a table row). */
		accessibleLabel?: string;
		title: string;
		confirmLabel?: string;
		onConfirm: () => Promise<void>;
		children: Snippet;
	}

	let {
		label,
		accessibleLabel,
		title,
		confirmLabel = 'Supprimer',
		onConfirm,
		children
	}: Props = $props();

	const uid = $props.id();
	const titleId = `${uid}-title`;
	let dialog: HTMLDialogElement | undefined = $state();
	let pending = $state(false);
	let problem = $state<ProblemDetails | null>(null);

	function open(): void {
		problem = null;
		dialog?.showModal();
	}

	async function confirm(): Promise<void> {
		pending = true;
		problem = null;
		try {
			await onConfirm();
			dialog?.close();
		} catch (error) {
			problem = problemFromError(error);
		} finally {
			pending = false;
		}
	}
</script>

<button type="button" class="danger" aria-label={accessibleLabel} onclick={open}>{label}</button>

<dialog bind:this={dialog} aria-labelledby={titleId}>
	<h2 id={titleId}>{title}</h2>
	<div class="content">
		{@render children()}
		<ProblemAlert {problem} />
	</div>
	<div class="actions">
		<button type="button" class="danger" disabled={pending} onclick={confirm}>
			{pending ? `${confirmLabel}…` : confirmLabel}
		</button>
		<button type="button" class="secondary" disabled={pending} onclick={() => dialog?.close()}>
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
