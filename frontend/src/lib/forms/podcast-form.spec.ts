import { describe, expect, it } from 'vitest';
import type { Podcast } from '../api/resources';
import {
	emptyPodcastFormValues,
	podcastFormValues,
	toPodcastInput,
	toPodcastPatch,
	validatePodcastForm,
	validateSlug,
	type PodcastFormValues
} from './podcast-form';

function validValues(overrides: Partial<PodcastFormValues> = {}): PodcastFormValues {
	return {
		...emptyPodcastFormValues(),
		slug: 'mon-podcast',
		title: 'Mon podcast',
		description: 'Une description',
		...overrides
	};
}

const podcast = {
	id: 'p1',
	slug: 'demo',
	title: 'Démo',
	description: 'Description',
	author: null,
	ownerName: 'Adrien',
	ownerEmail: null,
	language: 'fr',
	category: 'Technology',
	explicit: true,
	imageUrl: null,
	websiteUrl: 'https://example.com',
	feed: {
		maxEpisodes: 10,
		evictOldestBy: 'ADDED_DATE',
		sortBy: 'ADDED_DATE',
		sortDirection: 'ASC',
		includeFutureEpisodes: true
	}
} as Podcast;

describe('validateSlug', () => {
	it.each(['demo', 'mon-podcast-2', 'a'])('accepts %s', (slug) => {
		expect(validateSlug(slug)).toBeNull();
	});

	it.each([
		['', 'obligatoire'],
		['Demo', 'minuscules'],
		['double--dash', 'minuscules'],
		['-start', 'minuscules'],
		['a'.repeat(65), '64'],
		['admin', 'réservé'],
		['swagger-ui', 'réservé']
	])('rejects « %s »', (slug, expectedMessagePart) => {
		expect(validateSlug(slug)).toContain(expectedMessagePart);
	});
});

describe('validatePodcastForm', () => {
	it('accepts valid values', () => {
		expect(validatePodcastForm(validValues())).toEqual({});
	});

	it('requires the title, description and language', () => {
		expect(
			Object.keys(validatePodcastForm(validValues({ title: ' ', description: '', language: '' })))
		).toEqual(['title', 'description', 'language']);
	});

	it('checks the language tag, e-mail and URLs', () => {
		const errors = validatePodcastForm(
			validValues({
				language: 'français',
				ownerEmail: 'nope',
				imageUrl: 'cover.jpg',
				websiteUrl: 'javascript:alert(1)'
			})
		);

		expect(Object.keys(errors)).toEqual(['language', 'ownerEmail', 'imageUrl', 'websiteUrl']);
	});

	it('requires a positive episode limit only when limiting', () => {
		expect(validatePodcastForm(validValues({ limitEpisodes: false, maxEpisodes: null }))).toEqual(
			{}
		);
		expect(
			validatePodcastForm(validValues({ limitEpisodes: true, maxEpisodes: 0 })).maxEpisodes
		).toBeDefined();
		expect(
			validatePodcastForm(validValues({ limitEpisodes: true, maxEpisodes: null })).maxEpisodes
		).toBeDefined();
	});
});

describe('podcast form conversions', () => {
	it('defaults to French, all episodes, newest first, no future episodes', () => {
		expect(emptyPodcastFormValues()).toMatchObject({
			language: 'fr',
			limitEpisodes: false,
			sortBy: 'PUBLICATION_DATE',
			sortDirection: 'DESC',
			includeFutureEpisodes: false
		});
	});

	it('loads a podcast into the form', () => {
		expect(podcastFormValues(podcast)).toMatchObject({
			slug: 'demo',
			author: '',
			ownerName: 'Adrien',
			limitEpisodes: true,
			maxEpisodes: 10,
			evictOldestBy: 'ADDED_DATE',
			includeFutureEpisodes: true
		});
	});

	it('loads a podcast without episode limit', () => {
		const unlimited = { ...podcast, feed: { ...podcast.feed, maxEpisodes: null } };

		expect(podcastFormValues(unlimited)).toMatchObject({ limitEpisodes: false, maxEpisodes: null });
	});

	it('round-trips a podcast', () => {
		const { slug, title, feed, ownerEmail, websiteUrl } = podcast;

		expect(toPodcastInput(podcastFormValues(podcast))).toMatchObject({
			slug,
			title,
			feed,
			ownerEmail,
			websiteUrl
		});
	});

	it('trims text, maps blanks to null and ignores the limit when unchecked', () => {
		const input = toPodcastInput(
			validValues({ title: '  Titre  ', author: '  ', limitEpisodes: false, maxEpisodes: 5 })
		);

		expect(input).toMatchObject({ title: 'Titre', author: null, feed: { maxEpisodes: null } });
	});

	it('never sends the immutable slug in patches', () => {
		expect(toPodcastPatch(validValues())).not.toHaveProperty('slug');
	});
});
