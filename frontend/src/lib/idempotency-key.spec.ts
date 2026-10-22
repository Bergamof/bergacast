import { describe, expect, it } from 'vitest';
import { newIdempotencyKey } from './idempotency-key';

describe('newIdempotencyKey', () => {
	it('generates 128-bit hexadecimal keys', () => {
		expect(newIdempotencyKey()).toMatch(/^[0-9a-f]{32}$/);
	});

	it('generates a different key each time', () => {
		expect(newIdempotencyKey()).not.toBe(newIdempotencyKey());
	});
});
