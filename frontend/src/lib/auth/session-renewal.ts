import type { Session } from './session-store';

/** The renewal is offered this long before the token expires (WCAG 2.2.1 asks for 20 s at least). */
export const RENEWAL_NOTICE_MS = 60_000;

/** Milliseconds before the renewal must be offered (0: now). */
export function renewalDelay(session: Session, now: number): number {
	return Math.max(0, session.expiresAt - RENEWAL_NOTICE_MS - now);
}

/** Milliseconds before the token expires (0: already expired). */
export function expiryDelay(session: Session, now: number): number {
	return Math.max(0, session.expiresAt - now);
}

/** The session expiring last: a renewal made on this page, or a newer one read from the store. */
export function latestSession(stored: Session, renewed: Session | null): Session {
	return renewed !== null && renewed.expiresAt > stored.expiresAt ? renewed : stored;
}
