import { api, throwAsPageError } from '#lib/app.ts';
import { LoadDependency } from '#lib/load-dependencies.ts';
import type { LayoutLoad } from './$types';

export const load: LayoutLoad = async ({ params, url, depends }) => {
	depends(LoadDependency.podcast);
	try {
		return { podcast: await api.getPodcast(params.slug) };
	} catch (cause) {
		throwAsPageError(cause, url);
	}
};
