import { error, redirect } from '@sveltejs/kit';
import { goto } from '$app/navigation';
import { resolve } from '$app/paths';
import { ApiClient } from './api/client';
import { BergacastApi, OPENAPI_DOCUMENTATION_PATH } from './api/bergacast-api';
import { ApiError } from './api/problem';
import { EXPIRED_PARAMETER, REDIRECT_PARAMETER } from './auth/login-redirect';
import { SessionStore } from './auth/session-store';
import { apiOriginOf } from './runtime-config';

/** Application-wide singletons, wired to the browser (the SPA never renders on a server). */
export const sessionStore = new SessionStore(() => sessionStorage);

export function loginPageHref(options: { returnTo?: string; expired?: boolean } = {}): string {
	const query = new URLSearchParams();
	if (options.returnTo) {
		query.set(REDIRECT_PARAMETER, options.returnTo);
	}
	if (options.expired) {
		query.set(EXPIRED_PARAMETER, 'true');
	}
	const search = query.toString();
	return search ? `${resolve('/login')}?${search}` : resolve('/login');
}

export async function logOut(options: { expired?: boolean } = {}): Promise<void> {
	sessionStore.clear();
	const returnTo = options.expired ? `${location.pathname}${location.search}` : undefined;
	// eslint-disable-next-line svelte/no-navigation-without-resolve -- loginPageHref() is built with resolve()
	await goto(loginPageHref({ returnTo, expired: options.expired }));
}

const apiOrigin = apiOriginOf(globalThis.bergacastConfig);

export const api = new BergacastApi(
	new ApiClient({
		fetch: (input, init) => fetch(input, init),
		getAccessToken: () => sessionStore.read()?.accessToken ?? null,
		onUnauthorized: () => void logOut({ expired: true }),
		apiOrigin
	})
);

/** Swagger UI of the backend, which may be served from another origin than the SPA. */
export const apiDocumentationHref = `${apiOrigin ?? ''}${OPENAPI_DOCUMENTATION_PATH}`;

const SERVICE_UNAVAILABLE = 503;

/** Turns an API failure in a `load` function into a SvelteKit redirect (401) or error page. */
export function throwAsPageError(cause: unknown, url: URL): never {
	if (!(cause instanceof ApiError)) {
		throw cause;
	}
	if (cause.isUnauthorized) {
		redirect(307, loginPageHref({ returnTo: `${url.pathname}${url.search}`, expired: true }));
	}
	const status = cause.status >= 400 ? cause.status : SERVICE_UNAVAILABLE;
	error(status, cause.problem.detail ?? cause.problem.title ?? cause.message);
}
