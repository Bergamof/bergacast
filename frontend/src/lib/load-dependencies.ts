/** Custom identifiers passed to `depends()` so that pages can `invalidate()` exactly what they changed. */
export const LoadDependency = {
	podcast: 'app:podcast',
	episodes: 'app:episodes',
	episode: 'app:episode',
	apiTokens: 'app:api-tokens',
	adminUsers: 'app:admin-users'
} as const;
