import { describe, expect, it } from 'vitest';
import { pageHref, pageIndexFromUrl } from './pagination';

describe('pagination', () => {
	it.each([
		['/admin/podcasts', 0],
		['/admin/podcasts?page=1', 0],
		['/admin/podcasts?page=3', 2],
		['/admin/podcasts?page=0', 0],
		['/admin/podcasts?page=abc', 0],
		['/admin/podcasts?page=1.5', 0]
	])('reads the API page index of %s', (path, expected) => {
		expect(pageIndexFromUrl(new URL(path, 'http://localhost'))).toBe(expected);
	});

	it('builds the href of another page, keeping other parameters', () => {
		const url = new URL('http://localhost/admin/podcasts?filter=x&page=1');

		expect(pageHref(url, 1)).toBe('/admin/podcasts?filter=x&page=2');
	});
});
