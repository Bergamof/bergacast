import { api, throwAsPageError } from '#lib/app.ts';
import { LoadDependency } from '#lib/load-dependencies.ts';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ parent, params, url, depends }) => {
	depends(LoadDependency.episode);
	const { podcast } = await parent();
	try {
		return { episode: await api.getEpisode(podcast.resource, params.episodeId) };
	} catch (cause) {
		throwAsPageError(cause, url);
	}
};
