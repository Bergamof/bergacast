/**
 * Deployment settings of the SPA, set by `/config.js` before the application starts. The image
 * generates that script from its environment, so one build serves every environment.
 */
export interface RuntimeConfig {
	/** Public URL of the Bergacast backend; empty when it shares the origin of the SPA. */
	apiUrl?: string;
}

/** Origin of the API, or `undefined` when the API is served from the origin of the SPA. */
export function apiOriginOf(config: RuntimeConfig | undefined): string | undefined {
	const apiUrl = config?.apiUrl?.trim();
	if (!apiUrl) {
		return undefined;
	}
	if (!URL.canParse(apiUrl)) {
		throw new Error(`Invalid API URL in /config.js: "${apiUrl}"`);
	}
	return new URL(apiUrl).origin;
}
