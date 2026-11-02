import { describe, expect, it, vi } from 'vitest';
import { isAbortError } from './abort';
import { createXhrUploadTransport } from './upload-transport';

/** The part of XMLHttpRequest the transport uses, driven by the test. */
class FakeXhr {
	method = '';
	url = '';
	headers: Record<string, string> = {};
	sent: unknown = undefined;
	status = 0;
	responseText = '';
	responseHeaders = '';
	upload: { onprogress: ((event: Partial<ProgressEvent>) => void) | null } = { onprogress: null };
	onload: (() => void) | null = null;
	onerror: (() => void) | null = null;
	onabort: (() => void) | null = null;
	abort = vi.fn(() => this.onabort?.());

	open(method: string, url: string): void {
		this.method = method;
		this.url = url;
	}

	setRequestHeader(name: string, value: string): void {
		this.headers[name] = value;
	}

	getAllResponseHeaders(): string {
		return this.responseHeaders;
	}

	send(body: unknown): void {
		this.sent = body;
	}

	respond(status: number, responseText: string, responseHeaders = ''): void {
		this.status = status;
		this.responseText = responseText;
		this.responseHeaders = responseHeaders;
		this.onload?.();
	}
}

function transportWith(xhr: FakeXhr) {
	return createXhrUploadTransport(() => xhr as unknown as XMLHttpRequest);
}

describe('createXhrUploadTransport', () => {
	it('sends the request and resolves with its response', async () => {
		const xhr = new FakeXhr();
		const body = new FormData();

		const response = transportWith(xhr)('https://api.example/episodes', {
			method: 'POST',
			headers: { Authorization: 'Bearer jwt' },
			body,
			onUploadProgress: vi.fn()
		});
		xhr.respond(201, '{"id":"e1"}', 'Content-Type: application/hal+json\r\nETag: "1"\r\n');

		const resolved = await response;
		expect([xhr.method, xhr.url, xhr.headers, xhr.sent]).toEqual([
			'POST',
			'https://api.example/episodes',
			{ Authorization: 'Bearer jwt' },
			body
		]);
		expect(resolved.status).toBe(201);
		expect(resolved.headers.get('ETag')).toBe('"1"');
		expect(await resolved.json()).toEqual({ id: 'e1' });
	});

	it('resolves a response without body for a 204', async () => {
		const xhr = new FakeXhr();
		const response = transportWith(xhr)('/x', {
			method: 'POST',
			headers: {},
			onUploadProgress: vi.fn()
		});

		xhr.respond(204, '');

		expect((await response).status).toBe(204);
	});

	it('reports the progress of the body when its size is known', async () => {
		const xhr = new FakeXhr();
		const onUploadProgress = vi.fn();
		const response = transportWith(xhr)('/x', { method: 'POST', headers: {}, onUploadProgress });

		xhr.upload.onprogress?.({ lengthComputable: true, loaded: 50, total: 200 });
		xhr.upload.onprogress?.({ lengthComputable: false, loaded: 60, total: 0 });
		xhr.respond(200, '');
		await response;

		expect(onUploadProgress.mock.calls).toEqual([[50, 200]]);
	});

	it('rejects a network failure like fetch', async () => {
		const xhr = new FakeXhr();
		const response = transportWith(xhr)('/x', {
			method: 'POST',
			headers: {},
			onUploadProgress: vi.fn()
		});

		xhr.onerror?.();

		await expect(response).rejects.toBeInstanceOf(TypeError);
	});

	it('aborts the request when the signal fires', async () => {
		const xhr = new FakeXhr();
		const controller = new AbortController();
		const response = transportWith(xhr)('/x', {
			method: 'POST',
			headers: {},
			signal: controller.signal,
			onUploadProgress: vi.fn()
		});

		controller.abort();

		await expect(response).rejects.toSatisfy(isAbortError);
		expect(xhr.abort).toHaveBeenCalledOnce();
	});

	it('stops listening to the signal once the request is over', async () => {
		const xhr = new FakeXhr();
		const controller = new AbortController();
		const response = transportWith(xhr)('/x', {
			method: 'POST',
			headers: {},
			signal: controller.signal,
			onUploadProgress: vi.fn()
		});
		xhr.respond(200, '');
		await response;

		controller.abort();

		expect(xhr.abort).not.toHaveBeenCalled();
	});

	it('does not send a request whose signal already fired', async () => {
		const xhr = new FakeXhr();
		const controller = new AbortController();
		controller.abort();

		await expect(
			transportWith(xhr)('/x', {
				method: 'POST',
				headers: {},
				signal: controller.signal,
				onUploadProgress: vi.fn()
			})
		).rejects.toSatisfy(isAbortError);
		expect(xhr.sent).toBeUndefined();
	});
});
