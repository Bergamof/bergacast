import { describe, expect, it } from 'vitest';
import { recoveryActionFor } from './recovery-action';

const BOTH = { reload: true, publishFeed: true };

describe('recoveryActionFor', () => {
	it('offers to reload after a concurrent change (412)', () => {
		expect(recoveryActionFor({ status: 412 }, BOTH)).toBe('reload');
	});

	it('offers to rewrite the feed after a 503', () => {
		expect(recoveryActionFor({ status: 503 }, BOTH)).toBe('publish-feed');
	});

	it('offers nothing the form cannot do', () => {
		expect(recoveryActionFor({ status: 412 }, { reload: false, publishFeed: true })).toBeNull();
		expect(recoveryActionFor({ status: 503 }, { reload: true, publishFeed: false })).toBeNull();
	});

	it.each([null, { status: 422 }, { status: 500 }, {}])(
		'offers nothing otherwise: %j',
		(problem) => {
			expect(recoveryActionFor(problem, BOTH)).toBeNull();
		}
	);
});
