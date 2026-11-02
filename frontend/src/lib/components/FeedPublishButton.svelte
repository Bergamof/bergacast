<script lang="ts">
	import { problemFromError, type ProblemDetails } from '../api/problem';
	import type { FeedPublication } from '../api/resources';
	import { feedPublicationMessage } from '../publishing/feed-publication';
	import PendingButton from './PendingButton.svelte';
	import ProblemAlert from './ProblemAlert.svelte';
	import StatusMessage from './StatusMessage.svelte';

	interface Props {
		/** Rewrites the feed on the object storage now. */
		onPublishFeed: () => Promise<FeedPublication>;
	}

	let { onPublishFeed }: Props = $props();

	let publishing = $state(false);
	let publicationMessage = $state('');
	let publicationProblem = $state<ProblemDetails | null>(null);

	async function publishFeed(): Promise<void> {
		publishing = true;
		publicationMessage = '';
		publicationProblem = null;
		try {
			publicationMessage = feedPublicationMessage(await onPublishFeed());
		} catch (error) {
			publicationProblem = problemFromError(error);
		} finally {
			publishing = false;
		}
	}
</script>

<div class="feed-publish">
	<ProblemAlert problem={publicationProblem} />
	<div class="actions">
		<PendingButton
			type="button"
			class="secondary"
			pending={publishing}
			pendingLabel="Réécriture en cours…"
			onclick={publishFeed}
		>
			Réécrire le flux
		</PendingButton>
		<StatusMessage message={publicationMessage} />
	</div>
</div>

<style>
	.feed-publish {
		display: grid;
		gap: var(--space-s);
	}
</style>
