import { describe, expect, it, vi } from 'vitest';
import { ApiError } from './problem';
import { withRefreshOnUnavailable } from './storage-unavailable';

describe('withRefreshOnUnavailable', () => {
	it('returns the result of the change without refreshing', async () => {
		const refresh = vi.fn();

		await expect(withRefreshOnUnavailable(() => Promise.resolve('saved'), refresh)).resolves.toBe(
			'saved'
		);
		expect(refresh).not.toHaveBeenCalled();
	});

	it('refreshes then rethrows on a 503, as the change may be saved', async () => {
		const unavailable = new ApiError(503, { title: 'Service indisponible' });
		const refresh = vi.fn(() => Promise.resolve());

		await expect(withRefreshOnUnavailable(() => Promise.reject(unavailable), refresh)).rejects.toBe(
			unavailable
		);
		expect(refresh).toHaveBeenCalledOnce();
	});

	it.each([
		new ApiError(412, { title: 'Conflit' }),
		new ApiError(500, { title: 'Erreur' }),
		new TypeError('Failed to fetch')
	])('does not refresh on any other failure: %s', async (failure) => {
		const refresh = vi.fn();

		await expect(withRefreshOnUnavailable(() => Promise.reject(failure), refresh)).rejects.toBe(
			failure
		);
		expect(refresh).not.toHaveBeenCalled();
	});
});
