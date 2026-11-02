const SERVER_ERROR = 500;

const TITLES: Record<number, string> = {
	403: 'Accès refusé',
	404: 'Page introuvable',
	503: 'Service indisponible'
};

/** Heading and page title of the error page; a `load` that could not reach the API ends in a 503. */
export function errorPageTitle(status: number): string {
	return TITLES[status] ?? `Erreur ${status}`;
}

/** Server-side failures may be transient (cold start, storage), unlike a missing page. */
export function isRetryable(status: number): boolean {
	return status >= SERVER_ERROR;
}
