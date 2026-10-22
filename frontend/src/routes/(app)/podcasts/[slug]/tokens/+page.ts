import { api, throwAsPageError } from '#lib/app.ts';
import { LoadDependency } from '#lib/load-dependencies.ts';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ parent, url, depends }) => {
	depends(LoadDependency.apiTokens);
	const { podcast } = await parent();
	try {
		return { apiTokens: await api.listApiTokens(podcast.resource) };
	} catch (cause) {
		throwAsPageError(cause, url);
	}
};
