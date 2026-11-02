<script lang="ts">
	import type { Snippet } from 'svelte';
	import type { HTMLButtonAttributes } from 'svelte/elements';

	interface Props extends Omit<HTMLButtonAttributes, 'disabled' | 'children'> {
		/** While true, the button ignores activation and shows `pendingLabel`. */
		pending: boolean;
		pendingLabel: string;
		children: Snippet;
	}

	let { pending, pendingLabel, children, onclick, ...attributes }: Props = $props();

	// `aria-disabled` rather than `disabled`: a disabled button drops the focus to the page.
	function handleClick(
		event: MouseEvent & { currentTarget: EventTarget & HTMLButtonElement }
	): void {
		if (pending) {
			event.preventDefault();
			return;
		}
		onclick?.(event);
	}
</script>

<button {...attributes} aria-disabled={pending ? 'true' : undefined} onclick={handleClick}>
	{#if pending}{pendingLabel}{:else}{@render children()}{/if}
</button>
