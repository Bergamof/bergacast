import { describe, expect, it } from 'vitest';
import { RENEWAL_NOTICE_MS, expiryDelay, latestSession, renewalDelay } from './session-renewal';

const NOW = 1_000_000;

function session(expiresAt: number) {
	return { accessToken: 'jwt', username: 'admin', expiresAt };
}

describe('session renewal timing', () => {
	it('offers the renewal one minute before the expiry', () => {
		expect(RENEWAL_NOTICE_MS).toBe(60_000);
		expect(renewalDelay(session(NOW + 15 * 60_000), NOW)).toBe(14 * 60_000);
	});

	it('offers it at once when less than a minute is left, or once expired', () => {
		expect(renewalDelay(session(NOW + 30_000), NOW)).toBe(0);
		expect(renewalDelay(session(NOW - 1), NOW)).toBe(0);
	});

	it('counts down to the expiry, never below zero', () => {
		expect(expiryDelay(session(NOW + 5_000), NOW)).toBe(5_000);
		expect(expiryDelay(session(NOW - 5_000), NOW)).toBe(0);
	});

	it('keeps the session expiring last', () => {
		const stored = session(NOW + 1_000);
		const renewed = session(NOW + 900_000);

		expect(latestSession(stored, null)).toBe(stored);
		expect(latestSession(stored, renewed)).toBe(renewed);
		expect(latestSession(renewed, stored)).toBe(renewed);
	});
});
