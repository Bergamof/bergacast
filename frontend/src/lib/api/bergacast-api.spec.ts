import { describe, expect, it } from 'vitest';
import { createFakeFetch, emptyResponse, jsonResponse } from '../testing/fake-fetch';
import { BergacastApi, DEFAULT_PAGE_SIZE } from './bergacast-api';
import { ApiClient } from './client';
import type { Episode, Podcast } from './resources';

const ROOT = {
	version: '1.2.3',
	_links: {
		self: { href: 'http://localhost:5173/api/v1' },
		podcasts: { href: 'http://localhost:5173/api/v1/podcasts{?page,size,sort}', templated: true },
		adminUsers: { href: 'http://localhost:5173/api/v1/admin-users' },
		accessTokens: { href: 'http://localhost:5173/api/v1/access-tokens' }
	}
};

const PODCAST_LINKS = {
	self: { href: '/api/v1/podcasts/demo' },
	episodes: { href: '/api/v1/demo/episodes' },
	apiTokens: { href: '/api/v1/podcasts/demo/api-tokens' },
	feedPublications: { href: '/api/v1/podcasts/demo/feed-publications' }
};

const podcast = { slug: 'demo', _links: PODCAST_LINKS } as unknown as Podcast;

function createApi(...responses: Response[]) {
	const { fetch, requests } = createFakeFetch(...responses);
	const client = new ApiClient({
		fetch,
		getAccessToken: () => 'jwt',
		onUnauthorized: () => undefined
	});
	return { api: new BergacastApi(client), requests };
}

describe('BergacastApi', () => {
	it('logs in through the accessTokens link of the API root', async () => {
		const token = { accessToken: 'jwt', tokenType: 'Bearer', expiresIn: 900 };
		const { api, requests } = createApi(jsonResponse(200, ROOT), jsonResponse(200, token));

		await expect(api.logIn('alice', 'secret')).resolves.toEqual(token);
		expect(requests.map((request) => request.url)).toEqual(['/api/v1', '/api/v1/access-tokens']);
		expect(requests[1].body).toBe('{"username":"alice","password":"secret"}');
	});

	it('discovers the API root only once', async () => {
		const { api, requests } = createApi(
			jsonResponse(200, ROOT),
			jsonResponse(200, { _embedded: { podcasts: [{ slug: 'demo' }] } }),
			jsonResponse(200, { _embedded: { adminUsers: [] } })
		);

		await api.listPodcasts({ page: 2 });
		await api.listAdminUsers();

		expect(requests.map((request) => request.url)).toEqual([
			'/api/v1',
			`/api/v1/podcasts?page=2&size=${DEFAULT_PAGE_SIZE}`,
			'/api/v1/admin-users'
		]);
	});

	it('retries the root discovery after a failure', async () => {
		const { api } = createApi(
			jsonResponse(503, { title: 'Unavailable' }),
			jsonResponse(200, ROOT),
			jsonResponse(200, {})
		);

		await expect(api.listAdminUsers()).rejects.toMatchObject({ status: 503 });
		await expect(api.listAdminUsers()).resolves.toEqual([]);
	});

	it('gets the server version from the API root', async () => {
		const { api, requests } = createApi(jsonResponse(200, ROOT));

		await expect(api.getServerVersion()).resolves.toBe('1.2.3');
		expect(requests.map((request) => request.url)).toEqual(['/api/v1']);
	});

	it('shares the API root between the server version and the links', async () => {
		const { api, requests } = createApi(jsonResponse(200, ROOT), jsonResponse(200, {}));

		await api.getServerVersion();
		await api.listAdminUsers();
		await api.getServerVersion();

		expect(requests.map((request) => request.url)).toEqual(['/api/v1', '/api/v1/admin-users']);
	});

	it('retries the root discovery of the server version after a failure', async () => {
		const { api, requests } = createApi(
			jsonResponse(503, { title: 'Unavailable' }),
			jsonResponse(200, ROOT)
		);

		await expect(api.getServerVersion()).rejects.toMatchObject({ status: 503 });
		await expect(api.getServerVersion()).resolves.toBe('1.2.3');
		expect(requests.map((request) => request.url)).toEqual(['/api/v1', '/api/v1']);
	});

	it('gets a podcast below the podcasts collection with its ETag', async () => {
		const { api, requests } = createApi(
			jsonResponse(200, ROOT),
			jsonResponse(200, { slug: 'my podcast' }, { ETag: '"1"' })
		);

		const result = await api.getPodcast('my podcast');

		expect(requests[1].url).toBe('/api/v1/podcasts/my%20podcast');
		expect(result).toEqual({ resource: { slug: 'my podcast' }, etag: '"1"' });
	});

	it('creates a podcast on the podcasts collection', async () => {
		const { api, requests } = createApi(jsonResponse(200, ROOT), jsonResponse(201, podcast));

		await api.createPodcast({ slug: 'demo' } as Podcast);

		expect(requests[1]).toMatchObject({ url: '/api/v1/podcasts', method: 'POST' });
	});

	it('updates a podcast through its self link with If-Match', async () => {
		const { api, requests } = createApi(jsonResponse(200, { slug: 'demo' }, { ETag: '"2"' }));

		const updated = await api.updatePodcast({ resource: podcast, etag: '"1"' }, {
			title: 'New'
		} as Podcast);

		expect(requests[0].url).toBe('/api/v1/podcasts/demo');
		expect(requests[0].headers.get('If-Match')).toBe('"1"');
		expect(updated.etag).toBe('"2"');
	});

	it('uploads a podcast image as the raw file below the podcast', async () => {
		const image = { mimeType: 'image/png', width: 1400, height: 1400, sha256: 'new' };
		const { api, requests } = createApi(jsonResponse(201, image, { ETag: '"new"' }));
		const file = new File(['png'], 'cover.png', { type: 'image/png' });

		const uploaded = await api.uploadPodcastImage({ ...podcast, image: null }, file);

		expect(requests[0]).toMatchObject({ url: '/api/v1/podcasts/demo/image', method: 'PUT' });
		expect(requests[0].body).toBe(file);
		expect(requests[0].headers.get('Content-Type')).toBe('image/png');
		expect(requests[0].headers.has('If-Match')).toBe(false);
		expect(uploaded).toEqual({ resource: image, etag: '"new"' });
	});

	it('replaces a podcast image only if it is still the one displayed', async () => {
		const { api, requests } = createApi(jsonResponse(200, {}));
		const current = { ...podcast, image: { sha256: 'old' } } as Podcast;

		await api.uploadPodcastImage(current, new File(['jpg'], 'c.jpg', { type: 'image/jpeg' }));

		expect(requests[0].headers.get('If-Match')).toBe('"old"');
	});

	it('reports a rejected podcast image as problem details', async () => {
		const { api } = createApi(
			jsonResponse(413, { title: 'Image trop volumineuse' }, {}, 'application/problem+json')
		);

		await expect(
			api.uploadPodcastImage(podcast, new File(['x'], 'c.png', { type: 'image/png' }))
		).rejects.toMatchObject({ status: 413, problem: { title: 'Image trop volumineuse' } });
	});

	it('deletes a podcast image below the podcast', async () => {
		const { api, requests } = createApi(emptyResponse(204));

		await api.deletePodcastImage(podcast);

		expect(requests[0]).toMatchObject({ url: '/api/v1/podcasts/demo/image', method: 'DELETE' });
	});

	it('publishes the feed through the podcast feedPublications link', async () => {
		const publication = {
			feedUrl: 'https://media.example/demo.xml',
			publishedAt: '2026-10-27T08:15:00Z',
			episodeCount: 12
		};
		const { api, requests } = createApi(
			jsonResponse(201, publication, { Location: publication.feedUrl })
		);

		await expect(api.publishFeed(podcast)).resolves.toEqual(publication);
		expect(requests[0]).toMatchObject({
			url: '/api/v1/podcasts/demo/feed-publications',
			method: 'POST',
			body: undefined
		});
	});

	it('reports an unavailable object storage when publishing the feed', async () => {
		const { api } = createApi(
			jsonResponse(503, { title: 'Service indisponible' }, {}, 'application/problem+json')
		);

		await expect(api.publishFeed(podcast)).rejects.toMatchObject({
			status: 503,
			problem: { title: 'Service indisponible' }
		});
	});

	it('deletes a podcast through its self link', async () => {
		const { api, requests } = createApi(emptyResponse(204));

		await api.deletePodcast(podcast);

		expect(requests[0]).toMatchObject({ url: '/api/v1/podcasts/demo', method: 'DELETE' });
	});

	it('lists episodes through the podcast episodes link', async () => {
		const { api, requests } = createApi(
			jsonResponse(200, {
				_embedded: { episodes: [{ id: 'e1' }] },
				page: { size: 20, totalElements: 1, totalPages: 1, number: 0 }
			})
		);

		const episodes = await api.listEpisodes(podcast, { page: 0, size: 5 });

		expect(requests[0].url).toBe('/api/v1/demo/episodes?page=0&size=5');
		expect(episodes.items).toEqual([{ id: 'e1' }]);
	});

	it('gets, updates and deletes an episode', async () => {
		const episode = { id: 'e1', _links: { self: { href: '/api/v1/demo/episodes/e1' } } };
		const { api, requests } = createApi(
			jsonResponse(200, episode, { ETag: '"4"' }),
			jsonResponse(200, episode, { ETag: '"5"' }),
			emptyResponse(204)
		);

		const loaded = await api.getEpisode(podcast, 'e1');
		await api.updateEpisode(
			loaded as { resource: Episode; etag: string },
			{
				title: 'T'
			} as Episode
		);
		await api.deleteEpisode(loaded.resource);

		expect(requests.map((request) => `${request.method} ${request.url}`)).toEqual([
			'GET /api/v1/demo/episodes/e1',
			'PATCH /api/v1/demo/episodes/e1',
			'DELETE /api/v1/demo/episodes/e1'
		]);
		expect(requests[1].headers.get('If-Match')).toBe('"4"');
	});

	it('uploads an episode as multipart with an idempotency key', async () => {
		const { api, requests } = createApi(jsonResponse(201, { id: 'e2' }));
		const audio = new File(['ID3'], 'episode.mp3', { type: 'audio/mpeg' });

		await api.uploadEpisode(podcast, {
			metadata: { title: 'Épisode' },
			audio,
			idempotencyKey: 'key-1'
		});

		const body = requests[0].body as FormData;
		expect(requests[0].url).toBe('/api/v1/demo/episodes');
		expect(requests[0].headers.get('Idempotency-Key')).toBe('key-1');
		const metadata = body.get('metadata') as Blob;
		expect(metadata.type).toBe('application/json');
		expect(JSON.parse(await metadata.text())).toEqual({ title: 'Épisode' });
		expect((body.get('audio') as File).name).toBe('episode.mp3');
	});

	it('manages API tokens through the podcast apiTokens link', async () => {
		const token = { id: 't1', _links: { self: { href: '/api/v1/podcasts/demo/api-tokens/t1' } } };
		const { api, requests } = createApi(
			jsonResponse(200, { _embedded: { apiTokens: [token] } }),
			jsonResponse(201, { ...token, token: 'bgc_secret' }),
			emptyResponse(204)
		);

		await expect(api.listApiTokens(podcast)).resolves.toEqual([token]);
		await expect(api.createApiToken(podcast, 'CI')).resolves.toMatchObject({
			token: 'bgc_secret'
		});
		await api.revokeApiToken(token as never);

		expect(requests.map((request) => `${request.method} ${request.url}`)).toEqual([
			'GET /api/v1/podcasts/demo/api-tokens',
			'POST /api/v1/podcasts/demo/api-tokens',
			'DELETE /api/v1/podcasts/demo/api-tokens/t1'
		]);
	});

	it('manages admin users', async () => {
		const user = { id: 'u1', _links: { self: { href: '/api/v1/admin-users/u1' } } };
		const { api, requests } = createApi(
			jsonResponse(200, ROOT),
			jsonResponse(201, user),
			jsonResponse(200, user),
			emptyResponse(204)
		);

		await api.createAdminUser('bob', 'a-long-password');
		await api.changeAdminUserPassword(user as never, 'another-password');
		await api.deleteAdminUser(user as never);

		expect(requests.slice(1).map((request) => `${request.method} ${request.url}`)).toEqual([
			'POST /api/v1/admin-users',
			'PATCH /api/v1/admin-users/u1',
			'DELETE /api/v1/admin-users/u1'
		]);
		expect(requests[2].body).toBe('{"password":"another-password"}');
	});
});
