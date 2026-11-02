import { ApiError } from './problem';

/**
 * Runs a change through the API. On a 503, the change may be saved although the feed could not be
 * written: the page data is refreshed anyway, so that a retry sends the stored version (`If-Match`)
 * instead of failing with a 412, then the error is rethrown for the form to show.
 */
export async function withRefreshOnUnavailable<T>(
	change: () => Promise<T>,
	refresh: () => Promise<unknown>
): Promise<T> {
	try {
		return await change();
	} catch (error) {
		if (error instanceof ApiError && error.isServiceUnavailable) {
			await refresh();
		}
		throw error;
	}
}
