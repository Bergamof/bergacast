import type { HalResource } from './hal';

/** Entry point of the API: server version and links to the top-level resources. */
export interface ApiRoot extends HalResource {
	/** Semantic version of the backend (`0.0.0-dev` outside of a release build). */
	version: string;
}

export type EvictionCriterion = 'PUBLICATION_DATE' | 'ADDED_DATE';
export type FeedSortCriterion = 'PUBLICATION_DATE' | 'ADDED_DATE';
export type SortDirection = 'DESC' | 'ASC';
export type EpisodeType = 'FULL' | 'TRAILER' | 'BONUS';

export interface FeedSettings {
	/** `null` means every episode is listed in the feed. */
	maxEpisodes: number | null;
	evictOldestBy: EvictionCriterion;
	sortBy: FeedSortCriterion;
	sortDirection: SortDirection;
	includeFutureEpisodes: boolean;
}

/** Writable podcast fields (body of POST, and of PATCH without `slug`). */
export interface PodcastInput {
	slug: string;
	title: string;
	description: string;
	author: string | null;
	ownerName: string | null;
	ownerEmail: string | null;
	language: string;
	category: string | null;
	explicit: boolean;
	imageUrl: string | null;
	websiteUrl: string | null;
	feed: FeedSettings;
}

/** Cover image uploaded for a podcast (it takes priority over `imageUrl` in the feed). */
export interface PodcastImage {
	mimeType: string;
	/** Dimensions in pixels, after the server downscaled the image if needed. */
	width: number;
	height: number;
	sizeBytes: number;
	sha256: string;
	/** Public, cacheable URL of the image, served by the backend. */
	url: string;
}

/** Representation of the image sub-resource (`…/podcasts/{slug}/image`). */
export interface PodcastImageResource extends PodcastImage, HalResource {}

export interface Podcast extends PodcastInput, HalResource {
	id: string;
	/** `null` when no image has been uploaded. */
	image: PodcastImage | null;
	feedUrl: string;
	publishUrl: string;
	episodeCount: number;
	createdAt: string;
	updatedAt: string;
}

export interface EpisodeAudio {
	mimeType: string;
	sizeBytes: number;
	sha256: string;
	url: string;
}

/** Writable episode metadata (PATCH body, and `metadata` part of an upload). */
export interface EpisodeMetadata {
	title: string;
	description: string | null;
	publicationDate: string | null;
	guid?: string;
	durationSeconds: number | null;
	season: number | null;
	episodeNumber: number | null;
	episodeType: EpisodeType;
	explicit: boolean | null;
}

/** `metadata` part of an upload: only the title is required, the server defaults the rest. */
export type UploadMetadata = Pick<EpisodeMetadata, 'title'> & Partial<EpisodeMetadata>;

export interface Episode extends HalResource {
	id: string;
	guid: string;
	title: string;
	description: string | null;
	publicationDate: string;
	addedAt: string;
	durationSeconds: number | null;
	season: number | null;
	episodeNumber: number | null;
	episodeType: EpisodeType;
	explicit: boolean | null;
	audio: EpisodeAudio;
}

export interface ApiToken extends HalResource {
	id: string;
	name: string;
	tokenPrefix: string;
	createdAt: string;
	lastUsedAt: string | null;
}

export interface CreatedApiToken extends ApiToken {
	/** Plain token, returned only once by the creation request. */
	token: string;
}

export interface AdminUser extends HalResource {
	id: string;
	username: string;
	createdAt: string;
}

export interface AccessTokenResponse {
	accessToken: string;
	tokenType: string;
	/** Lifetime in seconds. */
	expiresIn: number;
}

/** A representation together with the entity tag needed for conditional updates. */
export interface Versioned<T> {
	resource: T;
	etag: string | null;
}
