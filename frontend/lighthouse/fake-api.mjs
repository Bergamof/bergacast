// Fake Bergacast API (HAL, Problem Details) giving the SPA realistic data to render during the Lighthouse audit:
// a paginated list of podcasts, a podcast with a non-square image (warning), a scheduled episode, an episode with an image, tokens, admins.
// Read-only: the two writes it handles answer with the errors the pages must display well (401 on login, 422 on
// creation); any other write is refused with a 405, so a journey that forgot to intercept its write fails loudly.

const NOW = new Date().toISOString();
const PODCAST_COUNT = 25;
const EPISODE_COUNT = 3;
const SCHEDULED_EPISODE = 1;
const WITH_IMAGE_EPISODE = 2;
const DEFAULT_PAGE_SIZE = 20;
export const MAIN_PODCAST = 'mon-podcast';

/** 1×1 transparent PNG, served as the cover image. */
export const COVER_IMAGE = Buffer.from(
	'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
	'base64'
);

function podcast(origin, index) {
	const slug = index === 0 ? MAIN_PODCAST : `podcast-${index}`;
	const api = `${origin}/api/v1`;
	return {
		id: `00000000-0000-4000-8000-${String(index).padStart(12, '0')}`,
		slug,
		title: index === 0 ? 'Mon podcast avec un titre assez long' : `Podcast numéro ${index}`,
		description: 'Une émission de démonstration pour mesurer l’accessibilité de l’administration.',
		author: 'Adrien',
		ownerName: 'Adrien',
		ownerEmail: 'adrien@example.org',
		language: 'fr',
		category: 'Technology',
		explicit: false,
		imageUrl: null,
		websiteUrl: 'https://example.org',
		feed: {
			maxEpisodes: 10,
			evictOldestBy: 'PUBLICATION_DATE',
			sortBy: 'PUBLICATION_DATE',
			sortDirection: 'DESC',
			includeFutureEpisodes: false
		},
		image:
			index % 2 === 0
				? {
						mimeType: 'image/png',
						width: 1200,
						height: 1000,
						sizeBytes: 345678,
						sha256: 'a'.repeat(64),
						url: `${origin}/media/${slug}/image.png`
					}
				: null,
		feedUrl: `${origin}/media/${slug}.xml`,
		publishUrl: `${api}/${slug}/episodes`,
		episodeCount: EPISODE_COUNT,
		createdAt: NOW,
		updatedAt: NOW,
		_links: {
			self: { href: `${api}/podcasts/${slug}` },
			episodes: { href: `${api}/${slug}/episodes{?page,size}`, templated: true },
			apiTokens: { href: `${api}/podcasts/${slug}/api-tokens` },
			feedPublications: { href: `${api}/podcasts/${slug}/feed-publications` },
			image: { href: `${api}/podcasts/${slug}/image` }
		}
	};
}

function episode(origin, slug, number) {
	return {
		id: `ep-${number}`,
		guid: `guid-${number}`,
		title: `Épisode ${number} : un titre`,
		description: 'Description de l’épisode.',
		publicationDate: number === SCHEDULED_EPISODE ? '2099-01-01T10:00:00Z' : NOW,
		addedAt: NOW,
		durationSeconds: 3725,
		season: 1,
		episodeNumber: number,
		episodeType: 'FULL',
		explicit: null,
		audio: {
			mimeType: 'audio/mpeg',
			sizeBytes: 123456789,
			sha256: 'b'.repeat(64),
			url: `${origin}/media/${slug}/episodes/ep-${number}/audio.mp3`
		},
		// Only the second episode has an image of its own, to render both states of the image form.
		image:
			number === WITH_IMAGE_EPISODE
				? {
						mimeType: 'image/png',
						width: 1000,
						height: 1000,
						sizeBytes: 234567,
						sha256: 'c'.repeat(64),
						url: `${origin}/media/${slug}/episodes/ep-${number}/image.png`
					}
				: null,
		_links: {
			self: { href: `${origin}/api/v1/${slug}/episodes/ep-${number}` },
			image: { href: `${origin}/api/v1/${slug}/episodes/ep-${number}/image` }
		}
	};
}

function paged(items, url, relation) {
	const number = Number(url.searchParams.get('page') ?? 0);
	const size = Number(url.searchParams.get('size') ?? DEFAULT_PAGE_SIZE);
	const content = items.slice(number * size, (number + 1) * size);
	return {
		...(content.length > 0 && { _embedded: { [relation]: content } }),
		page: {
			size,
			totalElements: items.length,
			totalPages: Math.max(1, Math.ceil(items.length / size)),
			number
		}
	};
}

const READ_METHODS = new Set(['GET', 'HEAD']);

const hal = (body, headers = {}) => ({ status: 200, body, headers });
const problem = (status, title, extra = {}) => ({
	status,
	body: { title, status, ...extra },
	headers: {}
});

/**
 * Answers an API request: `{ status, body, headers }`, a 404 Problem Details for any unknown route
 * and a 405 for any write other than the login and the podcast creation.
 */
export function answer(method, url) {
	const origin = url.origin;
	const api = `${origin}/api/v1`;
	const path = url.pathname;
	const podcasts = Array.from({ length: PODCAST_COUNT }, (_, index) => podcast(origin, index));
	let match;

	if (path === '/api/v1/access-tokens' && method === 'POST') {
		return problem(401, 'Identifiants invalides', {
			detail: 'Identifiant ou mot de passe incorrect.'
		});
	}
	if (path === '/api/v1/podcasts' && method === 'POST') {
		return problem(422, 'Données invalides', {
			errors: [{ field: 'slug', message: 'Ce slug est déjà utilisé.' }]
		});
	}
	if (!READ_METHODS.has(method)) {
		return problem(405, 'Méthode non gérée par l’API simulée', {
			detail: `${method} ${path} doit être intercepté par le parcours (page.route).`
		});
	}

	if (path === '/api/v1') {
		return hal({
			version: '1.0.0',
			_links: {
				self: { href: api },
				podcasts: { href: `${api}/podcasts{?page,size}`, templated: true },
				adminUsers: { href: `${api}/admin-users` },
				accessTokens: { href: `${api}/access-tokens` }
			}
		});
	}
	if (path === '/api/v1/podcasts') {
		return hal(paged(podcasts, url, 'podcasts'));
	}
	if ((match = path.match(/^\/api\/v1\/podcasts\/([^/]+)$/))) {
		const found = podcasts.find((candidate) => candidate.slug === match[1]);
		return found ? hal(found, { ETag: '"1.3"' }) : problem(404, 'Podcast introuvable');
	}
	if ((match = path.match(/^\/api\/v1\/podcasts\/([^/]+)\/api-tokens$/))) {
		const tokens = [
			['t1', 'n8n', null],
			['t2', 'Zapier', NOW]
		].map(([id, name, lastUsedAt]) => ({
			id,
			name,
			tokenPrefix: `bgc_${id.toUpperCase()}ab`,
			createdAt: NOW,
			lastUsedAt,
			_links: { self: { href: `${origin}${path}/${id}` } }
		}));
		return hal({ _embedded: { apiTokens: tokens } });
	}
	if ((match = path.match(/^\/api\/v1\/([^/]+)\/episodes$/))) {
		const episodes = Array.from({ length: EPISODE_COUNT }, (_, index) =>
			episode(origin, match[1], index + 1)
		);
		return hal(paged(episodes, url, 'episodes'));
	}
	if ((match = path.match(/^\/api\/v1\/([^/]+)\/episodes\/ep-(\d+)$/))) {
		return hal(episode(origin, match[1], Number(match[2])), { ETag: '"2"' });
	}
	if (path === '/api/v1/admin-users') {
		const users = ['admin', 'adrien'].map((username, index) => ({
			id: `u${index}`,
			username,
			createdAt: NOW,
			_links: { self: { href: `${api}/admin-users/u${index}` } }
		}));
		return hal({ _embedded: { adminUsers: users } });
	}
	return problem(404, 'Ressource introuvable');
}
