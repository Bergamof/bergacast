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

	it.each([
		null,
		'',
		'admin/podcasts',
		'https://evil.example/admin/',
		'/api/v1',
		'/administrator',
		'//evil.example',
		'/\\evil.example/admin/',
		'/admin/../api/v1'
	])('rejects %s', (candidate) => {
		expect(safeRedirectTarget(candidate, ROOT, FALLBACK)).toBe(FALLBACK);
	});

	it('keeps the query string and the fragment', () => {
		expect(safeRedirectTarget('/admin/podcasts?page=2#episodes', ROOT, FALLBACK)).toBe(
			'/admin/podcasts?page=2#episodes'
		);
	});

	it.each(['//evil.example', '/\\evil.example'])(
		'rejects %s even when served at the root',
		(candidate) => {
			expect(safeRedirectTarget(candidate, '/', '/podcasts')).toBe('/podcasts');
		}
	);
});
