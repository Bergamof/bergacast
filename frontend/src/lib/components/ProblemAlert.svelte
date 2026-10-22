<script lang="ts">
	import type { ProblemDetails } from '../api/problem';
	import { toFormFieldName } from '../forms/field-errors';

	interface Props {
		problem: ProblemDetails | null;
		/** Fields already shown next to their input, to avoid listing them twice. */
		inlineFields?: readonly string[];
	}

	let { problem, inlineFields = [] }: Props = $props();

	const otherErrors = $derived(
		(problem?.errors ?? []).filter(({ field }) => !inlineFields.includes(toFormFieldName(field)))
	);
</script>

{#if problem}
	<div class="problem" role="alert">
		<p class="title">{problem.title}</p>
		{#if problem.detail}
			<p>{problem.detail}</p>
		{/if}
		{#if otherErrors.length > 0}
			<ul>
				{#each otherErrors as violation (violation.field)}
					<li><code>{violation.field}</code> : {violation.message}</li>
				{/each}
			</ul>
		{/if}
	</div>
{/if}

<style>
	.problem {
		padding: var(--space-m);
		border: 1px solid var(--color-danger);
		border-radius: var(--radius);
		background: var(--color-danger-background);
		color: var(--color-danger);
	}

	p {
		margin: 0;
	}

	.title {
		font-weight: 700;
	}

	ul {
		margin: var(--space-s) 0 0;
	}
</style>
