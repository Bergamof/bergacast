import { vi } from 'vitest';

export interface RecordedRequest {
	url: string;
	method: string;
	headers: Headers;
	body: BodyInit | null | undefined;
}

export function jsonResponse(
	status: number,
	body: unknown,
	headers: Record<string, string> = {},
	contentType = 'application/hal+json'
): Response {
	return new Response(JSON.stringify(body), {
		status,
		headers: { 'Content-Type': contentType, ...headers }
	});
}

export function emptyResponse(status: number, headers: Record<string, string> = {}): Response {
	return new Response(null, { status, headers });
}

/** A `fetch` double answering queued responses in order and recording every request. */
export function createFakeFetch(...responses: Response[]) {
	const requests: RecordedRequest[] = [];
	const queue = [...responses];
	const fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
		requests.push({
			url: String(input),
			method: init?.method ?? 'GET',
			headers: new Headers(init?.headers),
			body: init?.body
		});
		const response = queue.shift();
		if (response === undefined) {
			throw new Error(`Unexpected request to ${String(input)}`);
		}
		return response;
	});
	return { fetch: fetch as unknown as typeof globalThis.fetch, requests };
}
