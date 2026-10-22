import { redirect } from '@sveltejs/kit';
import { loginPageHref, sessionStore } from '#lib/app.ts';
import type { LayoutLoad } from './$types';

export const load: LayoutLoad = ({ url }) => {
	const session = sessionStore.read();
	if (session === null) {
		redirect(307, loginPageHref({ returnTo: `${url.pathname}${url.search}` }));
	}
	return { session };
};
