export const REDIRECT_PARAMETER = 'redirectTo';
export const EXPIRED_PARAMETER = 'expired';

/** Placeholder origin: only used to resolve the candidate the way a browser would. */
const SAME_ORIGIN = 'https://bergacast.invalid';

/**
 * Only paths of this application are accepted, so the login page cannot be used as an open
 * redirect. The candidate is resolved as a browser would (`//host` and `/\host` both lead to
 * another site, `..` segments are collapsed) and the normalized path is returned, so the
 * navigation goes exactly where the check looked.
 */
export function safeRedirectTarget(
	candidate: string | null,
	applicationRoot: string,
	fallback: string
): string {
	if (candidate === null || !candidate.startsWith('/')) {
		return fallback;
	}
	const resolved = new URL(candidate, SAME_ORIGIN);
	const isApplicationPath =
		resolved.origin === SAME_ORIGIN && resolved.pathname.startsWith(applicationRoot);
	return isApplicationPath ? `${resolved.pathname}${resolved.search}${resolved.hash}` : fallback;
}
