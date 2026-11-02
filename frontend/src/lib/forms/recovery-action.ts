import { PRECONDITION_FAILED, SERVICE_UNAVAILABLE, type ProblemDetails } from '../api/problem';

/** What a form offers after a failed change, beyond showing the problem. */
export type RecoveryAction = 'reload' | 'publish-feed' | null;

/**
 * A 412 means the form edits an outdated version: reloading it is the way out. After a 503, the
 * change may be saved while the feed could not be written: rewriting the feed completes it.
 */
export function recoveryActionFor(
	problem: ProblemDetails | null,
	available: { reload: boolean; publishFeed: boolean }
): RecoveryAction {
	if (problem?.status === PRECONDITION_FAILED && available.reload) {
		return 'reload';
	}
	if (problem?.status === SERVICE_UNAVAILABLE && available.publishFeed) {
		return 'publish-feed';
	}
	return null;
}
