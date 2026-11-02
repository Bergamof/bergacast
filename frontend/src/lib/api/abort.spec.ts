import { describe, expect, it } from 'vitest';
import { abortError, isAbortError } from './abort';

describe('isAbortError', () => {
	it('tells a cancellation from other failures', () => {
		expect(isAbortError(abortError())).toBe(true);
		expect(isAbortError(new DOMException('x', 'AbortError'))).toBe(true);
		expect(isAbortError(new DOMException('x', 'TimeoutError'))).toBe(false);
		expect(isAbortError(new TypeError('x'))).toBe(false);
	});
});
