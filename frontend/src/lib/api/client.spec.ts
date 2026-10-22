import { describe, expect, it, vi } from 'vitest';
import { createFakeFetch, emptyResponse, jsonResponse } from '../testing/fake-fetch';
import { ApiClient, withQuery } from './client';
import { ApiError, NETWORK_ERROR_STATUS } from './problem';

const ORIGIN = 'http://admin.example';

function createClient(fetch: typeof globalThis.fetch, accessToken: string | null = 'jwt-token') {
	const onUnauthorized = vi.fn();
	const client = new ApiClient({
		fetch,
		getAccessToken: () => accessToken,
		onUnauthorized,
		origin: ORIGIN
	});
	return { client, onUnauthorized };
}

describe('ApiClient', () => {
	it('sends the bearer token and HAL accept header', async () => {
		const { fetch, requests } = createFakeFetch(jsonResponse(200, { title: 'Podcast' }));
		const { client } = createClient(fetch);

		const response = await client.get<{ title: string }>('/api/v1/podcasts/demo');

		expect(response.body.title).toBe('Podcast');
		expect(requests[0].headers.get('Authorization')).toBe('Bearer jwt-token');
		expect(requests[0].headers.get('Accept')).toContain('application/hal+json');
	});

	it('omits the authorization header when logged out', async () => {
		const { fetch, requests } = createFakeFetch(jsonResponse(200, {}));
		const { client } = createClient(fetch, null);

		await client.get('/api/v1');

		expect(requests[0].headers.has('Authorization')).toBe(false);
	});

	it('requests only the path of absolute links, staying on the SPA origin', async () => {
		const { fetch, requests } = createFakeFetch(jsonResponse(200, {}));
		const { client } = createClient(fetch);

		await client.get('http://internal-host:8080/api/v1/podcasts?page=1');

		expect(requests[0].url).toBe('/api/v1/podcasts?page=1');
	});

	it('returns the ETag and Location headers', async () => {
		const { fetch } = createFakeFetch(
			jsonResponse(201, { id: '1' }, { ETag: 'W/"3"', Location: '/api/v1/podcasts/demo' })
		);
		const { client } = createClient(fetch);

		const response = await client.post('/api/v1/podcasts', { slug: 'demo' });

		expect(response).toMatchObject({
			status: 201,
			etag: 'W/"3"',
			location: '/api/v1/podcasts/demo'
		});
	});

	it('posts JSON bodies', async () => {
		const { fetch, requests } = createFakeFetch(jsonResponse(201, {}));
		const { client } = createClient(fetch);

		await client.post('/api/v1/admin-users', { username: 'alice' });

		expect(requests[0].method).toBe('POST');
		expect(requests[0].headers.get('Content-Type')).toBe('application/json');
		expect(requests[0].body).toBe('{"username":"alice"}');
	});

	it('sends merge patches with If-Match', async () => {
		const { fetch, requests } = createFakeFetch(jsonResponse(200, {}));
		const { client } = createClient(fetch);

		await client.patch('/api/v1/podcasts/demo', { title: 'New' }, '"7"');

		expect(requests[0].method).toBe('PATCH');
		expect(requests[0].headers.get('Content-Type')).toBe('application/merge-patch+json');
		expect(requests[0].headers.get('If-Match')).toBe('"7"');
	});

	it('lets the browser set the multipart content type', async () => {
		const { fetch, requests } = createFakeFetch(jsonResponse(201, {}));
		const { client } = createClient(fetch);
		const formData = new FormData();

		await client.postMultipart('/api/v1/demo/episodes', formData, { 'Idempotency-Key': 'k1' });

		expect(requests[0].body).toBe(formData);
		expect(requests[0].headers.has('Content-Type')).toBe(false);
		expect(requests[0].headers.get('Idempotency-Key')).toBe('k1');
	});

	it('accepts empty 204 responses', async () => {
		const { fetch, requests } = createFakeFetch(emptyResponse(204));
		const { client } = createClient(fetch);

		await expect(client.delete('/api/v1/podcasts/demo')).resolves.toBeUndefined();
		expect(requests[0].method).toBe('DELETE');
	});

	it('throws problem details with field errors', async () => {
		const problem = {
			type: 'about:blank',
			title: 'Validation failed',
			status: 422,
			detail: 'Invalid podcast',
			errors: [{ field: 'feed.maxEpisodes', message: 'must be >= 1' }, { bogus: true }]
		};
		const { fetch } = createFakeFetch(jsonResponse(422, problem, {}, 'application/problem+json'));
		const { client } = createClient(fetch);

		const error = await client.post('/api/v1/podcasts', {}).catch((cause: unknown) => cause);

		expect(error).toBeInstanceOf(ApiError);
		expect((error as ApiError).status).toBe(422);
		expect((error as ApiError).problem).toMatchObject({
			title: 'Validation failed',
			detail: 'Invalid podcast',
			errors: [{ field: 'feed.maxEpisodes', message: 'must be >= 1' }]
		});
	});

	it('builds a French problem when the error body is not JSON', async () => {
		const { fetch } = createFakeFetch(
			new Response('<html>Bad gateway</html>', {
				status: 404,
				headers: { 'Content-Type': 'text/html' }
			})
		);
		const { client } = createClient(fetch);

		await expect(client.get('/api/v1/podcasts/missing')).rejects.toMatchObject({
			status: 404,
			problem: { title: 'Ressource introuvable', status: 404 }
		});
	});

	it('ignores malformed JSON bodies', async () => {
		const { fetch } = createFakeFetch(
			new Response('{not json', { status: 500, headers: { 'Content-Type': 'application/json' } })
		);
		const { client } = createClient(fetch);

		await expect(client.get('/api/v1')).rejects.toMatchObject({
			problem: { title: 'Erreur HTTP 500' }
		});
	});

	it('notifies the application when the sent token is rejected', async () => {
		const { fetch } = createFakeFetch(jsonResponse(401, { title: 'Unauthorized' }));
		const { client, onUnauthorized } = createClient(fetch);

		await expect(client.get('/api/v1/podcasts')).rejects.toMatchObject({ isUnauthorized: true });
		expect(onUnauthorized).toHaveBeenCalledOnce();
	});

	it('does not treat failed logins as an expired session', async () => {
		const { fetch } = createFakeFetch(jsonResponse(401, { title: 'Bad credentials' }));
		const { client, onUnauthorized } = createClient(fetch, null);

		await expect(client.post('/api/v1/access-tokens', {})).rejects.toBeInstanceOf(ApiError);
		expect(onUnauthorized).not.toHaveBeenCalled();
	});

	it('reports network failures as an unreachable server', async () => {
		const fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));
		const { client } = createClient(fetch);

		await expect(client.get('/api/v1')).rejects.toMatchObject({
			status: NETWORK_ERROR_STATUS,
			problem: { title: 'Serveur injoignable', detail: 'Failed to fetch' }
		});
	});
});

describe('withQuery', () => {
	it('adds parameters to an href without query', () => {
		expect(withQuery('/api/v1/podcasts', { page: 2, size: 20 })).toBe(
			'/api/v1/podcasts?page=2&size=20'
		);
	});

	it('replaces existing parameters and keeps the others', () => {
		expect(withQuery('/api/v1/podcasts?page=0&sort=title', { page: 1 })).toBe(
			'/api/v1/podcasts?page=1&sort=title'
		);
	});
});
