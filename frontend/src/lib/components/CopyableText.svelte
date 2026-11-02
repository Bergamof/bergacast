<script lang="ts">
	interface Props {
		value: string;
		/** What the text is, e.g. « URL du flux RSS »: names the block and the copy button. */
		label: string;
		/** Renders a preformatted block (e.g. a shell command) instead of a single line. */
		multiline?: boolean;
	}

	let { value, label, multiline = false }: Props = $props();

	const FEEDBACK_DURATION_MS = 2000;
	let feedback = $state('');
	let clearFeedback: ReturnType<typeof setTimeout> | undefined;

	async function copy(): Promise<void> {
		clearTimeout(clearFeedback);
		try {
			await navigator.clipboard.writeText(value);
			feedback = 'Copié !';
		} catch {
			feedback = 'Copie impossible : sélectionnez le texte manuellement.';
		}
		clearFeedback = setTimeout(() => (feedback = ''), FEEDBACK_DURATION_MS);
	}

	$effect(() => () => clearTimeout(clearFeedback));
</script>

<div class="copyable">
	{#if multiline}
		<!-- Focusable so that a keyboard can scroll it (WCAG 2.1.1). -->
		<!-- svelte-ignore a11y_no_noninteractive_tabindex -->
		<pre tabindex="0" role="region" aria-label={label}><code>{value}</code></pre>
	{:else}
		<code class="single-line">{value}</code>
	{/if}
	<div class="actions">
		<button type="button" class="secondary" aria-label="Copier : {label}" onclick={copy}>
			Copier
		</button>
		<span role="status" class="muted">{feedback}</span>
	</div>
</div>

<style>
	.copyable {
		display: grid;
		gap: var(--space-s);
	}

	.single-line {
		display: block;
		padding: var(--space-s);
		overflow-wrap: anywhere;
		background: var(--color-background);
		border: 1px solid var(--color-border);
		border-radius: var(--radius);
	}
</style>
