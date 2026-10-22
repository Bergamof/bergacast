import { isHttpError, isRedirect } from '@sveltejs/kit';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from './api/problem';

const { goto } = vi.hoisted(() => ({ goto: vi.fn() }));
vi.mock('$app/navigation', () => ({ goto }));
vi.mock('$app/paths', () => ({ resolve: (route: string) => `/admin${route}` }));

const { loginPageHref, logOut, sessionStore, throwAsPageError } = await import('./app');

const PAGE_URL = new URL('http://localhost/admin/podcasts?page=2');

function catchThrown(action: () => void): unknown {
	try {
		action();
	} catch (thrown) {
		return thrown;
	}
	throw new Error('Expected an exception');
}

describe('loginPageHref', () => {
	it('links to the login page', () => {
		expect(loginPageHref()).toBe('/admin/login');
	});

	it('remembers where to return and why', () => {
		expect(loginPageHref({ returnTo: '/admin/podcasts?page=2', expired: true })).toBe(
			'/admin/login?redirectTo=%2Fadmin%2Fpodcasts%3Fpage%3D2&expired=true'
		);
	});
});

describe('logOut', () => {
	beforeEach(() => {
		const storage = new Map<string, string>();
		vi.stubGlobal('sessionStorage', {
			getItem: (key: string) => storage.get(key) ?? null,
			setItem: (key: string, value: string) => storage.set(key, value),
			removeItem: (key: string) => storage.delete(key)
		});
		vi.stubGlobal('location', { pathname: '/admin/podcasts', search: '?page=2' });
		goto.mockReset();
	});

	it('forgets the session and goes to the login page', async () => {
		sessionStore.save('jwt', 'alice', 900);

		await logOut();

		expect(sessionStore.read()).toBeNull();
		expect(goto).toHaveBeenCalledWith('/admin/login');
	});

	it('comes back to the current page after an expired session', async () => {
		await logOut({ expired: true });

		expect(goto).toHaveBeenCalledWith(
			'/admin/login?redirectTo=%2Fadmin%2Fpodcasts%3Fpage%3D2&expired=true'
		);
	});
});

describe('throwAsPageError', () => {
	it('redirects to the login page on 401', () => {
		const thrown = catchThrown(() => throwAsPageError(new ApiError(401, {}), PAGE_URL));

		expect(isRedirect(thrown) && thrown.location).toBe(
			'/admin/login?redirectTo=%2Fadmin%2Fpodcasts%3Fpage%3D2&expired=true'
		);
	});

	it('shows API errors on the error page', () => {
		const thrown = catchThrown(() =>
			throwAsPageError(
				new ApiError(404, { title: 'Introuvable', detail: 'Podcast inconnu' }),
				PAGE_URL
			)
		);

		expect(isHttpError(thrown, 404) && thrown.body.message).toBe('Podcast inconnu');
	});

	it('reports an unreachable server as unavailable', () => {
		const thrown = catchThrown(() =>
			throwAsPageError(new ApiError(0, { title: 'Serveur injoignable' }), PAGE_URL)
		);

		expect(isHttpError(thrown, 503)).toBe(true);
	});

	it('rethrows other errors untouched', () => {
		const error = new TypeError('bug');

		expect(catchThrown(() => throwAsPageError(error, PAGE_URL))).toBe(error);
	});
});
