import { describe, expect, it } from 'vitest';
import { blankToNull, isBlank, isEmail, isHttpUrl, isIntegerAtLeast } from './validators';

describe('validators', () => {
	it('detects blank text', () => {
		expect(isBlank('  ')).toBe(true);
		expect(isBlank(' a ')).toBe(false);
	});

	it('validates e-mail addresses', () => {
		expect(isEmail('owner@example.com')).toBe(true);
		expect(isEmail('owner@example')).toBe(false);
		expect(isEmail('not an email')).toBe(false);
	});

	it('accepts only absolute http(s) URLs', () => {
		expect(isHttpUrl('https://example.com/cover.jpg')).toBe(true);
		expect(isHttpUrl('ftp://example.com')).toBe(false);
		expect(isHttpUrl('/relative')).toBe(false);
	});

	it('checks integer lower bounds', () => {
		expect(isIntegerAtLeast(1, 1)).toBe(true);
		expect(isIntegerAtLeast(0, 1)).toBe(false);
		expect(isIntegerAtLeast(1.5, 1)).toBe(false);
		expect(isIntegerAtLeast(null, 1)).toBe(false);
	});

	it('maps blank text to null', () => {
		expect(blankToNull('  ')).toBeNull();
		expect(blankToNull(' value ')).toBe('value');
	});
});
