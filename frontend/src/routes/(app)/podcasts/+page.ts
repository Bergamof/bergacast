import { api, throwAsPageError } from '#lib/app.ts';
import { pageIndexFromUrl } from '#lib/pagination.ts';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ url }) => {
	try {
		return { podcasts: await api.listPodcasts({ page: pageIndexFromUrl(url) }) };
	} catch (cause) {
		throwAsPageError(cause, url);
	}
};
