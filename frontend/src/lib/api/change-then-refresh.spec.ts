import { describe, expect, it, vi } from 'vitest';
import { changeThenRefresh } from './change-then-refresh';
import { ApiError } from './problem';

describe('changeThenRefresh', () => {
	it('refreshes after the change', async () => {
		const calls: string[] = [];

		await changeThenRefresh(
			async () => void calls.push('change'),
			async () => void calls.push('refresh')
		);

		expect(calls).toEqual(['change', 'refresh']);
	});

	it('refreshes then rethrows on a 503, as the change may be saved', async () => {
		const unavailable = new ApiError(503, { title: 'Service indisponible' });
		const refresh = vi.fn(() => Promise.resolve());

		await expect(changeThenRefresh(() => Promise.reject(unavailable), refresh)).rejects.toBe(
			unavailable
		);
		expect(refresh).toHaveBeenCalledOnce();
	});

	it('rethrows the 503 even when the refresh fails', async () => {
		const unavailable = new ApiError(503, { title: 'Service indisponible' });

		await expect(
			changeThenRefresh(
				() => Promise.reject(unavailable),
				() => Promise.reject(new TypeError('Failed to fetch'))
			)
		).rejects.toBe(unavailable);
	});

	it.each([
		new ApiError(412, { title: 'Conflit' }),
		new ApiError(500, { title: 'Erreur' }),
		new TypeError('Failed to fetch')
	])('does not refresh after any other failure: %s', async (failure) => {
		const refresh = vi.fn();

		await expect(changeThenRefresh(() => Promise.reject(failure), refresh)).rejects.toBe(failure);
		expect(refresh).not.toHaveBeenCalled();
	});
});
