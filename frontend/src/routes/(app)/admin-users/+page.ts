import { api, throwAsPageError } from '#lib/app.ts';
import { LoadDependency } from '#lib/load-dependencies.ts';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ url, depends }) => {
	depends(LoadDependency.adminUsers);
	try {
		return { adminUsers: await api.listAdminUsers() };
	} catch (cause) {
		throwAsPageError(cause, url);
	}
};
