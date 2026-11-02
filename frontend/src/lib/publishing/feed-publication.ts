import type { FeedPublication } from '../api/resources';
import { formatEpisodeCount } from '../format/count';

/** Confirmation shown once the feed has been rewritten, e.g. « Flux réécrit : 12 épisodes. ». */
export function feedPublicationMessage({ episodeCount }: FeedPublication): string {
	return `Flux réécrit : ${formatEpisodeCount(episodeCount)}.`;
}
