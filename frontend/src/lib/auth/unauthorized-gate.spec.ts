import { describe, expect, it, vi } from 'vitest';
import { UnauthorizedGate } from './unauthorized-gate';

function gate() {
	const endSession = vi.fn();
	return { gate: new UnauthorizedGate(endSession), endSession };
}

describe('UnauthorizedGate', () => {
	it('ends the session on a 401 by default', () => {
		const { gate: unauthorized, endSession } = gate();

		unauthorized.handle();

		expect(endSession).toHaveBeenCalledOnce();
	});

	it('defers the logout while held and reports the 401 on release', () => {
		const { gate: unauthorized, endSession } = gate();
		unauthorized.hold();

		unauthorized.handle();
		unauthorized.handle();

		expect(endSession).not.toHaveBeenCalled();
		expect(unauthorized.release()).toBe(true);
		expect(endSession).not.toHaveBeenCalled();
	});

	it('reports nothing when no 401 came while held', () => {
		const { gate: unauthorized } = gate();
		unauthorized.hold();

		expect(unauthorized.release()).toBe(false);
	});

	it('forgets a released 401 and ends the session again afterwards', () => {
		const { gate: unauthorized, endSession } = gate();
		unauthorized.hold();
		unauthorized.handle();
		unauthorized.release();

		expect(unauthorized.release()).toBe(false);
		unauthorized.handle();

		expect(endSession).toHaveBeenCalledOnce();
	});
});
