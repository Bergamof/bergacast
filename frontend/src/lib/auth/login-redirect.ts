export const REDIRECT_PARAMETER = 'redirectTo';
export const EXPIRED_PARAMETER = 'expired';

/**
 * Only paths of this application are accepted, so the login page cannot be used as an open
 * redirect (`//host` would be a protocol-relative URL to another site).
 */
export function safeRedirectTarget(
	candidate: string | null,
	applicationRoot: string,
	fallback: string
): string {
	const isApplicationPath =
		candidate !== null && candidate.startsWith(applicationRoot) && !candidate.startsWith('//');
	return isApplicationPath ? candidate : fallback;
}
