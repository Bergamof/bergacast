import { ApiError, NETWORK_ERROR_STATUS, toProblemDetails } from './problem';

const HAL_JSON = 'application/hal+json';
const JSON_TYPE = 'application/json';
const MERGE_PATCH_JSON = 'application/merge-patch+json';
const PROBLEM_JSON = 'application/problem+json';
const ACCEPT_HEADER = `${HAL_JSON}, ${JSON_TYPE};q=0.9, ${PROBLEM_JSON};q=0.8`;

export interface ApiResponse<T> {
	status: number;
	body: T;
	/** Entity tag of the returned representation, to send back in `If-Match`. */
	etag: string | null;
	location: string | null;
}

export interface RequestOptions {
	method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
	/** Sent as `application/json`. */
	json?: unknown;
	/** Sent as `application/merge-patch+json` (RFC 7396). */
	mergePatch?: unknown;
	formData?: FormData;
	/** Sent as is (e.g. a file), with its own media type as `Content-Type`. */
	blob?: Blob;
	ifMatch?: string | null;
	headers?: Record<string, string>;
}

export interface ApiClientOptions {
	fetch: typeof fetch;
	/** Returns the current bearer token, or `null` when the user is not logged in. */
	getAccessToken: () => string | null;
	/** Called when the API rejects the token we sent (expired, revoked, secret rotated…). */
	onUnauthorized: () => void;
	/**
	 * Origin serving the API (scheme, host and port) when it differs from the page origin. Without
	 * it, requests stay on the page origin (development proxy).
	 */
	apiOrigin?: string;
}

interface EncodedBody {
	body?: BodyInit;
	contentType?: string;
}

function encodeBody(options: RequestOptions): EncodedBody {
	if (options.formData !== undefined) {
		// The browser must set the multipart boundary itself, so no explicit Content-Type.
		return { body: options.formData };
	}
	if (options.blob !== undefined) {
		return { body: options.blob, contentType: options.blob.type || undefined };
	}
	if (options.mergePatch !== undefined) {
		return { body: JSON.stringify(options.mergePatch), contentType: MERGE_PATCH_JSON };
	}
	if (options.json !== undefined) {
		return { body: JSON.stringify(options.json), contentType: JSON_TYPE };
	}
	return {};
}

async function readBody(response: Response): Promise<unknown> {
	const contentType = response.headers.get('Content-Type') ?? '';
	if (!contentType.includes('json')) {
		return undefined;
	}
	const text = await response.text();
	if (text.length === 0) {
		return undefined;
	}
	try {
		return JSON.parse(text);
	} catch {
		return undefined;
	}
}

/** Thin `fetch` wrapper for the Bergacast REST API: bearer auth, HAL/JSON bodies, Problem Details errors. */
export class ApiClient {
	readonly #options: ApiClientOptions;

	constructor(options: ApiClientOptions) {
		this.#options = options;
	}

	/**
	 * Links built by the backend are absolute and depend on its public URL and proxy headers; only
	 * their path is kept and sent to the configured API origin, so every call reaches the same API.
	 */
	toRequestUrl(href: string): string {
		const apiOrigin = this.#options.apiOrigin;
		const url = new URL(href, apiOrigin ?? globalThis.location?.origin ?? 'http://localhost');
		return `${apiOrigin ?? ''}${url.pathname}${url.search}`;
	}

	async request<T>(href: string, options: RequestOptions = {}): Promise<ApiResponse<T>> {
		const accessToken = this.#options.getAccessToken();
		const { body, contentType } = encodeBody(options);
		const headers: Record<string, string> = { Accept: ACCEPT_HEADER, ...options.headers };
		if (contentType) {
			headers['Content-Type'] = contentType;
		}
		if (accessToken) {
			headers.Authorization = `Bearer ${accessToken}`;
		}
		if (options.ifMatch) {
			headers['If-Match'] = options.ifMatch;
		}

		let response: Response;
		try {
			response = await this.#options.fetch(this.toRequestUrl(href), {
				method: options.method ?? 'GET',
				headers,
				body
			});
		} catch (cause) {
			throw new ApiError(
				NETWORK_ERROR_STATUS,
				toProblemDetails(NETWORK_ERROR_STATUS, {
					detail: cause instanceof Error ? cause.message : undefined
				})
			);
		}

		const responseBody = await readBody(response);
		if (!response.ok) {
			if (response.status === 401 && accessToken) {
				this.#options.onUnauthorized();
			}
			throw new ApiError(response.status, toProblemDetails(response.status, responseBody));
		}
		return {
			status: response.status,
			body: responseBody as T,
			etag: response.headers.get('ETag'),
			location: response.headers.get('Location')
		};
	}

	get<T>(href: string): Promise<ApiResponse<T>> {
		return this.request<T>(href);
	}

	post<T>(href: string, json: unknown): Promise<ApiResponse<T>> {
		return this.request<T>(href, { method: 'POST', json });
	}

	postMultipart<T>(
		href: string,
		formData: FormData,
		headers: Record<string, string> = {}
	): Promise<ApiResponse<T>> {
		return this.request<T>(href, { method: 'POST', formData, headers });
	}

	/** Replaces the resource at `href` with the raw content of `blob`. */
	putBlob<T>(href: string, blob: Blob, ifMatch: string | null = null): Promise<ApiResponse<T>> {
		return this.request<T>(href, { method: 'PUT', blob, ifMatch });
	}

	patch<T>(
		href: string,
		mergePatch: unknown,
		ifMatch: string | null = null
	): Promise<ApiResponse<T>> {
		return this.request<T>(href, { method: 'PATCH', mergePatch, ifMatch });
	}

	async delete(href: string): Promise<void> {
		await this.request<unknown>(href, { method: 'DELETE' });
	}
}

/** Appends (or replaces) query parameters on an href, keeping its other parameters. */
export function withQuery(href: string, parameters: Record<string, string | number>): string {
	const [path, existingQuery = ''] = href.split('?', 2);
	const query = new URLSearchParams(existingQuery);
	for (const [name, value] of Object.entries(parameters)) {
		query.set(name, String(value));
	}
	return `${path}?${query.toString()}`;
}
