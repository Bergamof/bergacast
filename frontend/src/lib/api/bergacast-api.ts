import { withQuery, type ApiClient } from './client';
import { parseCollection, requireLinkHref, type HalResource, type PagedCollection } from './hal';
import type {
	AccessTokenResponse,
	AdminUser,
	ApiToken,
	CreatedApiToken,
	Episode,
	EpisodeMetadata,
	Podcast,
	PodcastInput,
	UploadMetadata,
	Versioned
} from './resources';

export const API_ENTRY_POINT = '/api/v1';
export const DEFAULT_PAGE_SIZE = 20;
/** Public Swagger UI, served by the backend at the root of the origin (outside the SPA base path). */
export const OPENAPI_DOCUMENTATION_PATH = '/swagger-ui.html';

export interface PageRequest {
	/** Zero-based page index. */
	page: number;
	size?: number;
}

export interface EpisodeUpload {
	metadata: UploadMetadata;
	audio: File;
	/** Lets a retried upload return the already created episode instead of a duplicate. */
	idempotencyKey: string;
}

type PodcastPatch = Omit<PodcastInput, 'slug'>;

function pageQuery({ page, size = DEFAULT_PAGE_SIZE }: PageRequest): Record<string, number> {
	return { page, size };
}

/**
 * Bergacast use cases on top of the HTTP client. Navigation starts from the API root and follows
 * HAL links; only item URIs below a collection are derived (`{collection}/{id}`), as the API
 * documents them.
 */
export class BergacastApi {
	readonly #client: ApiClient;
	#rootLinks: Promise<HalResource> | undefined;

	constructor(client: ApiClient) {
		this.#client = client;
	}

	async #rootLink(relation: string): Promise<string> {
		this.#rootLinks ??= this.#client.get<HalResource>(API_ENTRY_POINT).then(
			(response) => response.body,
			(error: unknown) => {
				// Do not cache a failure: the next call should retry the discovery.
				this.#rootLinks = undefined;
				throw error;
			}
		);
		return requireLinkHref(await this.#rootLinks, relation);
	}

	async logIn(username: string, password: string): Promise<AccessTokenResponse> {
		const href = await this.#rootLink('accessTokens');
		const response = await this.#client.post<AccessTokenResponse>(href, { username, password });
		return response.body;
	}

	async listPodcasts(pageRequest: PageRequest): Promise<PagedCollection<Podcast>> {
		const href = await this.#rootLink('podcasts');
		const response = await this.#client.get(withQuery(href, pageQuery(pageRequest)));
		return parseCollection<Podcast>(response.body, 'podcasts');
	}

	async #podcastHref(slug: string): Promise<string> {
		return `${await this.#rootLink('podcasts')}/${encodeURIComponent(slug)}`;
	}

	async getPodcast(slug: string): Promise<Versioned<Podcast>> {
		const response = await this.#client.get<Podcast>(await this.#podcastHref(slug));
		return { resource: response.body, etag: response.etag };
	}

	async createPodcast(input: PodcastInput): Promise<Podcast> {
		const href = await this.#rootLink('podcasts');
		return (await this.#client.post<Podcast>(href, input)).body;
	}

	async updatePodcast(
		current: Versioned<Podcast>,
		patch: PodcastPatch
	): Promise<Versioned<Podcast>> {
		const href = requireLinkHref(current.resource, 'self');
		const response = await this.#client.patch<Podcast>(href, patch, current.etag);
		return { resource: response.body, etag: response.etag };
	}

	async deletePodcast(podcast: Podcast): Promise<void> {
		await this.#client.delete(requireLinkHref(podcast, 'self'));
	}

	async listEpisodes(
		podcast: Podcast,
		pageRequest: PageRequest
	): Promise<PagedCollection<Episode>> {
		const href = withQuery(requireLinkHref(podcast, 'episodes'), pageQuery(pageRequest));
		return parseCollection<Episode>((await this.#client.get(href)).body, 'episodes');
	}

	async getEpisode(podcast: Podcast, episodeId: string): Promise<Versioned<Episode>> {
		const href = `${requireLinkHref(podcast, 'episodes')}/${encodeURIComponent(episodeId)}`;
		const response = await this.#client.get<Episode>(href);
		return { resource: response.body, etag: response.etag };
	}

	async uploadEpisode(podcast: Podcast, upload: EpisodeUpload): Promise<Episode> {
		const formData = new FormData();
		formData.append(
			'metadata',
			new Blob([JSON.stringify(upload.metadata)], { type: 'application/json' })
		);
		formData.append('audio', upload.audio, upload.audio.name);
		const response = await this.#client.postMultipart<Episode>(
			requireLinkHref(podcast, 'episodes'),
			formData,
			{ 'Idempotency-Key': upload.idempotencyKey }
		);
		return response.body;
	}

	async updateEpisode(
		current: Versioned<Episode>,
		metadata: EpisodeMetadata
	): Promise<Versioned<Episode>> {
		const href = requireLinkHref(current.resource, 'self');
		const response = await this.#client.patch<Episode>(href, metadata, current.etag);
		return { resource: response.body, etag: response.etag };
	}

	async deleteEpisode(episode: Episode): Promise<void> {
		await this.#client.delete(requireLinkHref(episode, 'self'));
	}

	async listApiTokens(podcast: Podcast): Promise<ApiToken[]> {
		const response = await this.#client.get(requireLinkHref(podcast, 'apiTokens'));
		return parseCollection<ApiToken>(response.body, 'apiTokens').items;
	}

	async createApiToken(podcast: Podcast, name: string): Promise<CreatedApiToken> {
		const href = requireLinkHref(podcast, 'apiTokens');
		return (await this.#client.post<CreatedApiToken>(href, { name })).body;
	}

	async revokeApiToken(token: ApiToken): Promise<void> {
		await this.#client.delete(requireLinkHref(token, 'self'));
	}

	async listAdminUsers(): Promise<AdminUser[]> {
		const response = await this.#client.get(await this.#rootLink('adminUsers'));
		return parseCollection<AdminUser>(response.body, 'adminUsers').items;
	}

	async createAdminUser(username: string, password: string): Promise<AdminUser> {
		const href = await this.#rootLink('adminUsers');
		return (await this.#client.post<AdminUser>(href, { username, password })).body;
	}

	async changeAdminUserPassword(user: AdminUser, password: string): Promise<void> {
		await this.#client.patch(requireLinkHref(user, 'self'), { password });
	}

	async deleteAdminUser(user: AdminUser): Promise<void> {
		await this.#client.delete(requireLinkHref(user, 'self'));
	}
}
