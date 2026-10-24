// Development settings: the Vite dev server proxies the API, so it shares the origin of the SPA.
// The production image replaces this file at startup from BERGACAST_API_URL (see docker-entrypoint.sh).
globalThis.bergacastConfig = { apiUrl: '' };
