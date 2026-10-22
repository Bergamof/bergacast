import type {
	EvictionCriterion,
	FeedSortCriterion,
	Podcast,
	PodcastInput,
	SortDirection
} from '../api/resources';
import type { FieldErrors } from './field-errors';
import {
	REQUIRED_MESSAGE,
	blankToNull,
	isBlank,
	isEmail,
	isHttpUrl,
	isIntegerAtLeast
} from './validators';

export const SLUG_MAX_LENGTH = 64;
export const SLUG_PATTERN = /^[a-z0-9]+(-[a-z0-9]+)*$/;
/** Slugs that would clash with the server's own top-level routes. */
export const RESERVED_SLUGS: readonly string[] = [
	'api',
	'admin',
	'swagger-ui',
	'v3',
	'actuator',
	'podcasts',
	'admin-users',
	'access-tokens',
	'assets',
	'favicon.ico'
];
const LANGUAGE_TAG_PATTERN = /^[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})*$/;
const MINIMUM_EPISODE_LIMIT = 1;
const DEFAULT_LANGUAGE = 'fr';

export interface PodcastFormValues {
	slug: string;
	title: string;
	description: string;
	author: string;
	ownerName: string;
	ownerEmail: string;
	language: string;
	category: string;
	explicit: boolean;
	imageUrl: string;
	websiteUrl: string;
	limitEpisodes: boolean;
	maxEpisodes: number | null;
	evictOldestBy: EvictionCriterion;
	sortBy: FeedSortCriterion;
	sortDirection: SortDirection;
	includeFutureEpisodes: boolean;
}

export function emptyPodcastFormValues(): PodcastFormValues {
	return {
		slug: '',
		title: '',
		description: '',
		author: '',
		ownerName: '',
		ownerEmail: '',
		language: DEFAULT_LANGUAGE,
		category: '',
		explicit: false,
		imageUrl: '',
		websiteUrl: '',
		limitEpisodes: false,
		maxEpisodes: null,
		evictOldestBy: 'PUBLICATION_DATE',
		sortBy: 'PUBLICATION_DATE',
		sortDirection: 'DESC',
		includeFutureEpisodes: false
	};
}

export function podcastFormValues(podcast: Podcast): PodcastFormValues {
	return {
		slug: podcast.slug,
		title: podcast.title,
		description: podcast.description,
		author: podcast.author ?? '',
		ownerName: podcast.ownerName ?? '',
		ownerEmail: podcast.ownerEmail ?? '',
		language: podcast.language,
		category: podcast.category ?? '',
		explicit: podcast.explicit,
		imageUrl: podcast.imageUrl ?? '',
		websiteUrl: podcast.websiteUrl ?? '',
		limitEpisodes: podcast.feed.maxEpisodes !== null,
		maxEpisodes: podcast.feed.maxEpisodes,
		evictOldestBy: podcast.feed.evictOldestBy,
		sortBy: podcast.feed.sortBy,
		sortDirection: podcast.feed.sortDirection,
		includeFutureEpisodes: podcast.feed.includeFutureEpisodes
	};
}

export function validateSlug(slug: string): string | null {
	if (isBlank(slug)) {
		return REQUIRED_MESSAGE;
	}
	if (slug.length > SLUG_MAX_LENGTH) {
		return `${SLUG_MAX_LENGTH} caractères maximum.`;
	}
	if (!SLUG_PATTERN.test(slug)) {
		return 'Lettres minuscules, chiffres et tirets simples uniquement (ex. « mon-podcast »).';
	}
	if (RESERVED_SLUGS.includes(slug)) {
		return `« ${slug} » est réservé par l’application.`;
	}
	return null;
}

export function validatePodcastForm(values: PodcastFormValues): FieldErrors {
	const errors: FieldErrors = {};
	const slugError = validateSlug(values.slug);
	if (slugError !== null) {
		errors.slug = slugError;
	}
	for (const field of ['title', 'description', 'language'] as const) {
		if (isBlank(values[field])) {
			errors[field] = REQUIRED_MESSAGE;
		}
	}
	if (!errors.language && !LANGUAGE_TAG_PATTERN.test(values.language.trim())) {
		errors.language = 'Code de langue BCP 47 attendu (ex. « fr » ou « fr-CA »).';
	}
	if (!isBlank(values.ownerEmail) && !isEmail(values.ownerEmail)) {
		errors.ownerEmail = 'Adresse e-mail invalide.';
	}
	for (const field of ['imageUrl', 'websiteUrl'] as const) {
		if (!isBlank(values[field]) && !isHttpUrl(values[field])) {
			errors[field] = 'URL http(s) absolue attendue.';
		}
	}
	if (values.limitEpisodes && !isIntegerAtLeast(values.maxEpisodes, MINIMUM_EPISODE_LIMIT)) {
		errors.maxEpisodes = `Nombre entier supérieur ou égal à ${MINIMUM_EPISODE_LIMIT} attendu.`;
	}
	return errors;
}

/** PATCH body: the slug is immutable, sending it would only risk a 422. */
export function toPodcastPatch(values: PodcastFormValues): Omit<PodcastInput, 'slug'> {
	return {
		title: values.title.trim(),
		description: values.description.trim(),
		author: blankToNull(values.author),
		ownerName: blankToNull(values.ownerName),
		ownerEmail: blankToNull(values.ownerEmail),
		language: values.language.trim(),
		category: blankToNull(values.category),
		explicit: values.explicit,
		imageUrl: blankToNull(values.imageUrl),
		websiteUrl: blankToNull(values.websiteUrl),
		feed: {
			maxEpisodes: values.limitEpisodes ? values.maxEpisodes : null,
			evictOldestBy: values.evictOldestBy,
			sortBy: values.sortBy,
			sortDirection: values.sortDirection,
			includeFutureEpisodes: values.includeFutureEpisodes
		}
	};
}

export function toPodcastInput(values: PodcastFormValues): PodcastInput {
	return { slug: values.slug.trim(), ...toPodcastPatch(values) };
}
