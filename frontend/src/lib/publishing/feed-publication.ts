import type { FeedPublication } from '../api/resources';

/** Confirmation shown once the feed has been rewritten, e.g. « Flux réécrit : 12 épisodes. ». */
export function feedPublicationMessage({ episodeCount }: FeedPublication): string {
	const episodes = episodeCount > 1 ? 'épisodes' : 'épisode';
	return `Flux réécrit : ${episodeCount} ${episodes}.`;
}
