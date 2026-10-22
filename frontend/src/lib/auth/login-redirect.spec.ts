import { describe, expect, it } from 'vitest';
import { safeRedirectTarget } from './login-redirect';

const ROOT = '/admin/';
const FALLBACK = '/admin/podcasts';

describe('safeRedirectTarget', () => {
	it('accepts paths of the application', () => {
		expect(safeRedirectTarget('/admin/podcasts/demo?page=2', ROOT, FALLBACK)).toBe(
			'/admin/podcasts/demo?page=2'
		);
	});

	it.each([null, 'https://evil.example/admin/', '/api/v1', '/administrator', '//evil.example'])(
		'rejects %s',
		(candidate) => {
			expect(safeRedirectTarget(candidate, ROOT, FALLBACK)).toBe(FALLBACK);
		}
	);

	it('rejects protocol-relative URLs even when served at the root', () => {
		expect(safeRedirectTarget('//evil.example', '/', '/podcasts')).toBe('/podcasts');
	});
});
