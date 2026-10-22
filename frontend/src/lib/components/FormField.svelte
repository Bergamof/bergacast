<script lang="ts" module>
	/** Attributes the field passes to its control so that label, hint and error are announced. */
	export interface ControlAttributes {
		id: string;
		'aria-describedby': string | undefined;
		'aria-invalid': 'true' | undefined;
	}
</script>

<script lang="ts">
	import type { Snippet } from 'svelte';

	interface Props {
		id: string;
		label: string;
		error?: string;
		hint?: string;
		required?: boolean;
		control: Snippet<[ControlAttributes]>;
	}

	let { id, label, error, hint, required = false, control }: Props = $props();

	const hintId = $derived(`${id}-hint`);
	const errorId = $derived(`${id}-error`);
	const attributes = $derived<ControlAttributes>({
		id,
		'aria-describedby':
			[hint ? hintId : null, error ? errorId : null].filter(Boolean).join(' ') || undefined,
		'aria-invalid': error ? 'true' : undefined
	});
</script>

<div class="field">
	<label for={id}>
		{label}
		{#if required}<span aria-hidden="true" class="required">*</span>{/if}
	</label>
	{@render control(attributes)}
	{#if hint}
		<p class="hint" id={hintId}>{hint}</p>
	{/if}
	{#if error}
		<p class="error" id={errorId}>{error}</p>
	{/if}
</div>

<style>
	.field {
		display: grid;
		gap: var(--space-xs);
		align-content: start;
	}

	.hint,
	.error {
		margin: 0;
		font-size: 0.875rem;
	}

	.hint {
		color: var(--color-muted);
	}

	.error {
		color: var(--color-danger);
		font-weight: 600;
	}

	.required {
		color: var(--color-danger);
	}
</style>
