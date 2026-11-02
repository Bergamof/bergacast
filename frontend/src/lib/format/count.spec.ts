import { describe, expect, it } from 'vitest';
import { formatEpisodeCount } from './count';

describe('formatEpisodeCount', () => {
	it.each([
		[0, '0 épisode'],
		[1, '1 épisode'],
		[2, '2 épisodes'],
		[12, '12 épisodes']
	])('formats %i', (count, text) => {
		expect(formatEpisodeCount(count)).toBe(text);
	});
});
