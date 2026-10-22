export const PAGE_PARAMETER = 'page';
const FIRST_PAGE_NUMBER = 1;

/**
 * Pages are numbered from 1 in the address bar (for humans) and from 0 in the API (Spring Data).
 * Invalid values fall back to the first page.
 */
export function pageIndexFromUrl(url: {
	readonly searchParams: { get(name: string): string | null };
}): number {
	const pageNumber = Number(url.searchParams.get(PAGE_PARAMETER) ?? FIRST_PAGE_NUMBER);
	return Number.isInteger(pageNumber) && pageNumber >= FIRST_PAGE_NUMBER
		? pageNumber - FIRST_PAGE_NUMBER
		: 0;
}

/** Accepts SvelteKit's read-only `page.url` as well as a plain `URL`. */
export function pageHref(currentUrl: { readonly href: string }, pageIndex: number): string {
	const target = new URL(currentUrl.href);
	target.searchParams.set(PAGE_PARAMETER, String(pageIndex + FIRST_PAGE_NUMBER));
	return `${target.pathname}${target.search}`;
}
