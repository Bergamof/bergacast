import { ApiError } from './problem';

/**
 * Runs a change through the API, then refreshes the page data. A 503 refreshes too: the object
 * storage may have failed after the change was saved (only the feed could not be written), and a
 * retry must send the stored version (`If-Match`) rather than fail with a 412. Any failure is
 * rethrown for the form to show.
 */
export async function changeThenRefresh(
	change: () => Promise<unknown>,
	refresh: () => Promise<unknown>
): Promise<void> {
	try {
		await change();
	} catch (error) {
		if (error instanceof ApiError && error.isServiceUnavailable) {
			// The 503 is what the form must show (with its recovery action), not a refresh failure.
			await refresh().catch(() => undefined);
		}
		throw error;
	}
	await refresh();
}
