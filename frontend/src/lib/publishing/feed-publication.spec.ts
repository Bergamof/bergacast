import { describe, expect, it } from 'vitest';
import type { FeedPublication } from '../api/resources';
import { feedPublicationMessage } from './feed-publication';

function publication(episodeCount: number): FeedPublication {
	return {
		feedUrl: 'https://media.example/demo.xml',
		publishedAt: '2026-10-27T08:15:00Z',
		episodeCount,
		_links: {}
	};
}

describe('feedPublicationMessage', () => {
	it('counts the episodes of the written feed', () => {
		expect(feedPublicationMessage(publication(12))).toBe('Flux réécrit : 12 épisodes.');
	});

	it('uses the singular for zero or one episode', () => {
		expect(feedPublicationMessage(publication(0))).toBe('Flux réécrit : 0 épisode.');
		expect(feedPublicationMessage(publication(1))).toBe('Flux réécrit : 1 épisode.');
	});
});
