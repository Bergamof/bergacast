/** « 1 épisode », « 3 épisodes » (French: plural from 2 on, « 0 épisode »). */
export function formatEpisodeCount(count: number): string {
	return `${count} ${count > 1 ? 'épisodes' : 'épisode'}`;
}
