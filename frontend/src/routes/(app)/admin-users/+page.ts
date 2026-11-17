import { api, throwAsPageError } from '#lib/app.ts';
import { LoadDependency } from '#lib/load-dependencies.ts';
import { pageIndexFromUrl } from '#lib/pagination.ts';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ url, depends }) => {
	depends(LoadDependency.adminUsers);
	try {
		return { adminUsers: await api.listAdminUsers({ page: pageIndexFromUrl(url) }) };
	} catch (cause) {
		throwAsPageError(cause, url);
	}
};
