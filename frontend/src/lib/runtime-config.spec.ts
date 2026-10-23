import { describe, expect, it } from 'vitest';
import { apiOriginOf } from './runtime-config';

describe('apiOriginOf', () => {
	it('keeps only the origin of the API URL', () => {
		expect(apiOriginOf({ apiUrl: 'https://api.example.com/some/path/' })).toBe(
			'https://api.example.com'
		);
	});

	it('keeps a non-default port', () => {
		expect(apiOriginOf({ apiUrl: 'http://localhost:8080' })).toBe('http://localhost:8080');
	});

	it.each([undefined, {}, { apiUrl: '' }, { apiUrl: '   ' }])(
		'uses the SPA origin when no API URL is configured (%o)',
		(config) => {
			expect(apiOriginOf(config)).toBeUndefined();
		}
	);

	it('rejects an invalid API URL', () => {
		expect(() => apiOriginOf({ apiUrl: 'api.example.com' })).toThrow('Invalid API URL');
	});
});
