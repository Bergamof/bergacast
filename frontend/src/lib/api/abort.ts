/** The error a cancelled request rejects with, like `fetch` given an aborted `AbortSignal`. */
export function abortError(): DOMException {
	return new DOMException('The request was cancelled', 'AbortError');
}

export function isAbortError(error: unknown): boolean {
	return error instanceof DOMException && error.name === 'AbortError';
}
