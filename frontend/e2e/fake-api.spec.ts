import { expect, test } from '@playwright/test';
import { SIGNED_IN } from './support';

// The fake API must not turn a write a journey forgot to intercept into a success it never earned.
const PODCAST_API = `${SIGNED_IN}/api/v1/podcasts/mon-podcast`;

test.describe('fake API', () => {
	for (const method of ['patch', 'delete', 'put'] as const) {
		test(`refuses an unhandled ${method.toUpperCase()} with a 405`, async ({ request }) => {
			const response = await request[method](PODCAST_API);

			expect(response.status()).toBe(405);
			expect(await response.json()).toMatchObject({ status: 405 });
		});
	}

	test('keeps answering the writes it handles on purpose', async ({ request }) => {
		const login = await request.post(`${SIGNED_IN}/api/v1/access-tokens`);
		const creation = await request.post(`${SIGNED_IN}/api/v1/podcasts`);

		expect(login.status()).toBe(401);
		expect(creation.status()).toBe(422);
	});

	test('still serves reads', async ({ request }) => {
		const response = await request.get(PODCAST_API);

		expect(response.status()).toBe(200);
	});
});
