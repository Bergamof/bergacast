<script lang="ts">
	import type { ProblemDetails } from '../api/problem';
	import type { FeedPublication } from '../api/resources';
	import { recoveryActionFor } from '../forms/recovery-action';
	import FeedPublishButton from './FeedPublishButton.svelte';
	import PendingButton from './PendingButton.svelte';

	interface Props {
		problem: ProblemDetails | null;
		/** Loads the latest version of the resource into the form. */
		onReload?: () => Promise<void>;
		/** Rewrites the feed on the object storage now. */
		onPublishFeed?: () => Promise<FeedPublication>;
	}

	let { problem, onReload, onPublishFeed }: Props = $props();

	const action = $derived(
		recoveryActionFor(problem, { reload: !!onReload, publishFeed: !!onPublishFeed })
	);
	let reloading = $state(false);

	async function reload(): Promise<void> {
		reloading = true;
		try {
			await onReload?.();
		} finally {
			reloading = false;
		}
	}
</script>

{#if action === 'reload'}
	<div class="actions">
		<PendingButton
			type="button"
			class="secondary"
			pending={reloading}
			pendingLabel="Chargement…"
			onclick={reload}
		>
			Recharger la dernière version
		</PendingButton>
	</div>
{:else if action === 'publish-feed' && onPublishFeed}
	<FeedPublishButton {onPublishFeed} afterFeedFailure />
{/if}
