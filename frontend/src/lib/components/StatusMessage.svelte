<script lang="ts">
	interface Props {
		/** Empty: nothing shown. */
		message: string;
		tone?: 'success' | 'warning';
	}

	let { message, tone = 'success' }: Props = $props();
</script>

<!-- Always rendered: screen readers announce a live region only when its content changes. -->
<div class="status" role="status">
	{#if message}
		<p class="notice {tone}">{message}</p>
	{/if}
</div>

<style>
	.notice {
		margin: 0;
	}
</style>
