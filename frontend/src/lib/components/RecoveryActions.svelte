<script lang="ts">
	import type { ProblemDetails } from '../api/problem';
	import type { FeedPublication } from '../api/resources';
	import { PRECONDITION_FAILED, SERVICE_UNAVAILABLE } from '../api/problem';
	import FeedPublishButton from './FeedPublishButton.svelte';
	import PendingButton from './PendingButton.svelte';

	interface Props {
		problem: ProblemDetails | null;
		/** Offered on a 412: loads the latest version of the resource into the form. */
		onReload?: () => Promise<void>;
		/** Offered on a 503: the change may be saved while the feed could not be written. */
		onPublishFeed?: () => Promise<FeedPublication>;
	}

	let { problem, onReload, onPublishFeed }: Props = $props();

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

{#if problem?.status === PRECONDITION_FAILED && onReload}
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
{:else if problem?.status === SERVICE_UNAVAILABLE && onPublishFeed}
	<FeedPublishButton {onPublishFeed} />
{/if}
