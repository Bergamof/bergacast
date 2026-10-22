import { describe, expect, it } from 'vitest';
import type { Episode } from '../api/resources';
import {
	emptyEpisodeFormValues,
	episodeFormValues,
	toEpisodeMetadata,
	toUploadMetadata,
	validateAudioFile,
	validateEpisodeForm,
	type EpisodeFormValues
} from './episode-form';

function values(overrides: Partial<EpisodeFormValues> = {}): EpisodeFormValues {
	return { ...emptyEpisodeFormValues(), title: 'Épisode 1', ...overrides };
}

const episode = {
	id: 'e1',
	guid: 'guid-1',
	title: 'Épisode 1',
	description: null,
	publicationDate: new Date(2026, 9, 22, 8, 30).toISOString(),
	addedAt: '2026-10-21T10:00:00Z',
	durationSeconds: 3723,
	season: 2,
	episodeNumber: 7,
	episodeType: 'BONUS',
	explicit: false,
	audio: { mimeType: 'audio/mpeg', sizeBytes: 10, sha256: 'abc', url: 'http://x/a.mp3' }
} as Episode;

describe('validateEpisodeForm', () => {
	it('accepts minimal upload values', () => {
		expect(validateEpisodeForm(values(), { publicationDateRequired: false })).toEqual({});
	});

	it('requires the publication date when editing', () => {
		expect(validateEpisodeForm(values(), { publicationDateRequired: true })).toEqual({
			publicationDate: expect.any(String)
		});
	});

	it('reports every invalid field', () => {
		const errors = validateEpisodeForm(
			values({
				title: '',
				publicationDate: 'garbage',
				duration: '1:99',
				season: 0,
				episodeNumber: 1.5
			}),
			{ publicationDateRequired: true }
		);

		expect(Object.keys(errors)).toEqual([
			'title',
			'publicationDate',
			'durationSeconds',
			'season',
			'episodeNumber'
		]);
	});
});

describe('validateAudioFile', () => {
	it('requires a file', () => {
		expect(validateAudioFile(undefined)).toContain('Choisissez');
	});

	it('accepts audio files only', () => {
		expect(validateAudioFile(new File(['x'], 'a.mp3', { type: 'audio/mpeg' }))).toBeNull();
		expect(validateAudioFile(new File(['x'], 'a.txt', { type: 'text/plain' }))).toContain(
			'text/plain'
		);
		expect(validateAudioFile(new File(['x'], 'a'))).toContain('inconnu');
	});
});

describe('episode form conversions', () => {
	it('loads an episode into the form', () => {
		expect(episodeFormValues(episode)).toEqual({
			title: 'Épisode 1',
			description: '',
			publicationDate: '2026-10-22T08:30',
			duration: '1:02:03',
			season: 2,
			episodeNumber: 7,
			episodeType: 'BONUS',
			explicit: 'no',
			guid: 'guid-1'
		});
	});

	it('maps the explicit flag both ways', () => {
		expect(episodeFormValues({ ...episode, explicit: null }).explicit).toBe('inherit');
		expect(episodeFormValues({ ...episode, explicit: true }).explicit).toBe('yes');
		expect(toEpisodeMetadata(values({ explicit: 'inherit' })).explicit).toBeNull();
		expect(toEpisodeMetadata(values({ explicit: 'yes' })).explicit).toBe(true);
	});

	it('round-trips the editable metadata', () => {
		expect(toEpisodeMetadata(episodeFormValues(episode))).toEqual({
			title: 'Épisode 1',
			description: null,
			publicationDate: episode.publicationDate,
			durationSeconds: 3723,
			season: 2,
			episodeNumber: 7,
			episodeType: 'BONUS',
			explicit: false
		});
	});

	it('loads an episode without duration', () => {
		expect(episodeFormValues({ ...episode, durationSeconds: null }).duration).toBe('');
	});

	it('omits empty optional values from uploads so the server applies its defaults', () => {
		expect(toUploadMetadata(values({ duration: '90', guid: ' custom-guid ' }))).toEqual({
			title: 'Épisode 1',
			durationSeconds: 90,
			episodeType: 'FULL',
			guid: 'custom-guid'
		});
	});
});
