<script lang="ts">
	interface Props {
		value: string;
		label: string;
		/** Renders a preformatted block (e.g. a shell command) instead of a single line. */
		multiline?: boolean;
	}

	let { value, label, multiline = false }: Props = $props();

	const FEEDBACK_DURATION_MS = 2000;
	let feedback = $state('');

	async function copy(): Promise<void> {
		try {
			await navigator.clipboard.writeText(value);
			feedback = 'Copié !';
		} catch {
			feedback = 'Copie impossible : sélectionnez le texte manuellement.';
		}
		setTimeout(() => (feedback = ''), FEEDBACK_DURATION_MS);
	}
</script>

<div class="copyable">
	{#if multiline}
		<pre aria-label={label}><code>{value}</code></pre>
	{:else}
		<code class="single-line" aria-label={label}>{value}</code>
	{/if}
	<div class="actions">
		<button type="button" class="secondary" onclick={copy}>Copier</button>
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
