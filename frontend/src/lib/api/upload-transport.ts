/** Bytes sent so far and in total, as reported by the browser while uploading a request body. */
export type UploadProgressListener = (loaded: number, total: number) => void;

export interface UploadRequestInit {
	method: string;
	headers: Record<string, string>;
	body?: XMLHttpRequestBodyInit | null;
	signal?: AbortSignal;
	onUploadProgress: UploadProgressListener;
}

/** Sends a request reporting the progress of its body; resolves like `fetch`. */
export type UploadTransport = (url: string, init: UploadRequestInit) => Promise<Response>;

/** Statuses whose response cannot carry a body (`new Response` refuses one). */
const NULL_BODY_STATUSES = new Set([101, 204, 205, 304]);

function parseHeaders(rawHeaders: string): Headers {
	const headers = new Headers();
	for (const line of rawHeaders.trim().split(/[\r\n]+/)) {
		const separator = line.indexOf(':');
		if (separator > 0) {
			headers.append(line.slice(0, separator).trim(), line.slice(separator + 1).trim());
		}
	}
	return headers;
}

export function abortError(): DOMException {
	return new DOMException('The upload was cancelled', 'AbortError');
}

export function isAbortError(error: unknown): boolean {
	return error instanceof DOMException && error.name === 'AbortError';
}

/**
 * `fetch` cannot report upload progress: XMLHttpRequest can (`upload.onprogress`), and aborts
 * through the same `AbortSignal` as `fetch`. Network failures reject with a `TypeError` and
 * cancellations with an `AbortError`, as `fetch` does.
 */
export function createXhrUploadTransport(
	createRequest: () => XMLHttpRequest = () => new XMLHttpRequest()
): UploadTransport {
	return (url, init) =>
		new Promise((resolve, reject) => {
			if (init.signal?.aborted) {
				reject(abortError());
				return;
			}
			const request = createRequest();
			request.open(init.method, url);
			for (const [name, value] of Object.entries(init.headers)) {
				request.setRequestHeader(name, value);
			}
			request.upload.onprogress = (event) => {
				if (event.lengthComputable) {
					init.onUploadProgress(event.loaded, event.total);
				}
			};
			request.onload = () =>
				resolve(
					new Response(NULL_BODY_STATUSES.has(request.status) ? null : request.responseText, {
						status: request.status,
						headers: parseHeaders(request.getAllResponseHeaders())
					})
				);
			request.onerror = () => reject(new TypeError('Network request failed'));
			request.onabort = () => reject(abortError());
			init.signal?.addEventListener('abort', () => request.abort(), { once: true });
			request.send(init.body ?? null);
		});
}
