<script lang="ts">
	import type { Snippet } from 'svelte';
	import type { HTMLButtonAttributes, MouseEventHandler } from 'svelte/elements';

	interface Props extends Omit<HTMLButtonAttributes, 'disabled' | 'children'> {
		/** While true, the button ignores activation and shows `pendingLabel`. */
		pending: boolean;
		pendingLabel: string;
		/** The rendered button, e.g. to give it the focus back. */
		element?: HTMLButtonElement;
		children: Snippet;
	}

	let {
		pending,
		pendingLabel,
		element = $bindable(),
		children,
		onclick,
		...attributes
	}: Props = $props();

	const handleClick: MouseEventHandler<HTMLButtonElement> = (event) => {
		if (pending) {
			event.preventDefault();
			return;
		}
		onclick?.(event);
	};
</script>

<!-- `aria-disabled` rather than `disabled`: a disabled button drops the focus to the page. -->
<button
	{...attributes}
	bind:this={element}
	aria-disabled={pending ? 'true' : undefined}
	onclick={handleClick}
>
	{#if pending}{pendingLabel}{:else}{@render children()}{/if}
</button>
