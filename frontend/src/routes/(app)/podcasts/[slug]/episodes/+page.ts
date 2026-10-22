import { api, throwAsPageError } from '#lib/app.ts';
import { LoadDependency } from '#lib/load-dependencies.ts';
import { pageIndexFromUrl } from '#lib/pagination.ts';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ parent, url, depends }) => {
	depends(LoadDependency.episodes);
	const { podcast } = await parent();
	try {
		return {
			episodes: await api.listEpisodes(podcast.resource, { page: pageIndexFromUrl(url) })
		};
	} catch (cause) {
		throwAsPageError(cause, url);
	}
};
