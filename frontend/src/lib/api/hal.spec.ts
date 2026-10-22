import { describe, expect, it } from 'vitest';
import { findLinkHref, parseCollection, removeUriTemplateVariables, requireLinkHref } from './hal';

describe('HAL links', () => {
	const resource = {
		_links: {
			self: { href: 'http://localhost/api/v1/podcasts/demo' },
			episodes: { href: 'http://localhost/api/v1/demo/episodes{?page,size,sort}', templated: true },
			alternate: [{ href: '/first' }, { href: '/second' }]
		}
	};

	it('finds a link href', () => {
		expect(findLinkHref(resource, 'self')).toBe('http://localhost/api/v1/podcasts/demo');
	});

	it('expands templated links to their base URI', () => {
		expect(findLinkHref(resource, 'episodes')).toBe('http://localhost/api/v1/demo/episodes');
	});

	it('takes the first link of an array', () => {
		expect(findLinkHref(resource, 'alternate')).toBe('/first');
	});

	it('returns undefined for a missing relation', () => {
		expect(findLinkHref({}, 'self')).toBeUndefined();
	});

	it('fails loudly when a required relation is missing', () => {
		expect(() => requireLinkHref(resource, 'apiTokens')).toThrow('apiTokens');
	});

	it('removes every template expression', () => {
		expect(removeUriTemplateVariables('/a{/id}/b{?x,y}')).toBe('/a/b');
	});
});

describe('parseCollection', () => {
	it('reads embedded items and page metadata', () => {
		const body = {
			_embedded: { podcasts: [{ slug: 'a' }, { slug: 'b' }] },
			page: { size: 2, totalElements: 5, totalPages: 3, number: 1 }
		};

		expect(parseCollection(body, 'podcasts')).toEqual({
			items: [{ slug: 'a' }, { slug: 'b' }],
			page: { size: 2, totalElements: 5, totalPages: 3, number: 1 }
		});
	});

	it('handles empty collections without _embedded', () => {
		expect(
			parseCollection({ page: { size: 20, totalElements: 0, totalPages: 0, number: 0 } }, 'x')
		).toEqual({ items: [], page: { size: 20, totalElements: 0, totalPages: 0, number: 0 } });
	});

	it('synthesizes page metadata for unpaged collections', () => {
		expect(
			parseCollection({ _embedded: { adminUsers: [{ id: '1' }] } }, 'adminUsers').page
		).toEqual({ size: 1, totalElements: 1, totalPages: 1, number: 0 });
	});

	it('falls back to the only embedded array when the relation name differs', () => {
		const body = { _embedded: { apiTokenResponseList: [{ id: 't' }] } };

		expect(parseCollection(body, 'apiTokens').items).toEqual([{ id: 't' }]);
	});

	it('returns no items for unexpected bodies', () => {
		expect(parseCollection(undefined, 'podcasts').items).toEqual([]);
		expect(parseCollection({ _embedded: { a: [], b: [] } }, 'podcasts').items).toEqual([]);
	});
});
