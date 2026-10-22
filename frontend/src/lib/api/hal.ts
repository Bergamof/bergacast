export interface HalLink {
	href: string;
	templated?: boolean;
	title?: string;
}

export interface HalResource {
	_links?: Record<string, HalLink | HalLink[] | undefined>;
}

/** Spring Data pagination metadata (`page` member of a HAL collection). */
export interface PageMetadata {
	size: number;
	totalElements: number;
	totalPages: number;
	/** Zero-based index of the current page. */
	number: number;
}

export interface PagedCollection<T> {
	items: T[];
	page: PageMetadata;
}

interface HalCollectionBody {
	_embedded?: Record<string, unknown>;
	page?: Partial<PageMetadata>;
}

/** Returns the href of a link relation, or `undefined` if the resource does not expose it. */
export function findLinkHref(resource: HalResource, relation: string): string | undefined {
	const link = resource._links?.[relation];
	const firstLink = Array.isArray(link) ? link[0] : link;
	return firstLink ? removeUriTemplateVariables(firstLink.href) : undefined;
}

/** Same as {@link findLinkHref} but fails loudly: a missing link means the API contract changed. */
export function requireLinkHref(resource: HalResource, relation: string): string {
	const href = findLinkHref(resource, relation);
	if (href === undefined) {
		throw new Error(`Lien HAL « ${relation} » absent de la réponse de l’API`);
	}
	return href;
}

/**
 * Drops RFC 6570 expressions such as `{?page,size,sort}`: the client appends its own query
 * parameters, so templated links are only needed for their base URI.
 */
export function removeUriTemplateVariables(href: string): string {
	return href.replace(/\{[^}]*\}/g, '');
}

function findEmbeddedItems(
	embedded: Record<string, unknown> | undefined,
	relation: string
): unknown[] {
	if (embedded === undefined) {
		return [];
	}
	const named = embedded[relation];
	if (Array.isArray(named)) {
		return named;
	}
	// Tolerate a collection relation named after the server-side model class.
	const arrays = Object.values(embedded).filter(Array.isArray);
	return arrays.length === 1 ? arrays[0] : [];
}

/**
 * Parses a HAL collection. Spring HATEOAS omits `_embedded` for empty collections and `page`
 * for unpaged ones, so both are optional here.
 */
export function parseCollection<T>(body: unknown, embeddedRelation: string): PagedCollection<T> {
	const collection = (body ?? {}) as HalCollectionBody;
	const items = findEmbeddedItems(collection._embedded, embeddedRelation) as T[];
	const page = collection.page;
	return {
		items,
		page: {
			size: page?.size ?? items.length,
			totalElements: page?.totalElements ?? items.length,
			totalPages: page?.totalPages ?? (items.length > 0 ? 1 : 0),
			number: page?.number ?? 0
		}
	};
}
