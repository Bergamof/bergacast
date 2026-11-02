import AxeBuilder from '@axe-core/playwright';
import { expect, type Page, type Route } from '@playwright/test';
import { SIGNED_IN_PORT, SIGNED_OUT_PORT } from './servers';

export const SIGNED_OUT = `http://localhost:${SIGNED_OUT_PORT}`;
export const SIGNED_IN = `http://localhost:${SIGNED_IN_PORT}`;
export const PODCAST_PATH = '/podcasts/mon-podcast';
export const PODCAST_API = '**/api/v1/podcasts/mon-podcast';
export const EPISODES_API = '**/api/v1/mon-podcast/episodes';
export const API_TOKENS_API = `${PODCAST_API}/api-tokens`;

const WCAG_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa', 'best-practice'];

export async function expectNoAxeViolations(page: Page): Promise<void> {
	const { violations } = await new AxeBuilder({ page }).withTags(WCAG_TAGS).analyze();
	expect(violations.map(({ id, nodes }) => `${id}: ${nodes.map((node) => node.target)}`)).toEqual(
		[]
	);
}

export function problem(route: Route, status: number, title: string): Promise<void> {
	return route.fulfill({
		status,
		contentType: 'application/problem+json',
		body: JSON.stringify({ title, status })
	});
}

/** A response held until the test releases it, to observe the page while a request is pending. */
export function heldResponse() {
	let release: () => void = () => {};
	const released = new Promise<void>((resolve) => (release = resolve));
	return { released, release };
}

/** Drives the uploads of a page whose XMLHttpRequest was replaced by `installControllableUploads`. */
export interface UploadDriver {
	progress(loaded: number, total: number): Promise<void>;
	respond(status: number, body: unknown): Promise<void>;
	failNetwork(): Promise<void>;
}

/**
 * Replaces XMLHttpRequest, used for uploads only, by one the test drives: Playwright reports no
 * upload progress for the requests it intercepts.
 */
export async function installControllableUploads(page: Page): Promise<UploadDriver> {
	await page.addInitScript(() => {
		type Listener = ((event?: unknown) => void) | null;
		class ControllableXhr {
			static current: ControllableXhr | null = null;
			status = 0;
			responseText = '';
			upload: { onprogress: Listener } = { onprogress: null };
			onload: Listener = null;
			onerror: Listener = null;
			onabort: Listener = null;
			open(): void {}
			setRequestHeader(): void {}
			getAllResponseHeaders(): string {
				return 'content-type: application/hal+json\r\n';
			}
			send(): void {
				ControllableXhr.current = this;
			}
			abort(): void {
				this.onabort?.();
			}
		}
		Object.assign(window, {
			XMLHttpRequest: ControllableXhr,
			uploadDriver: {
				progress: (loaded: number, total: number) =>
					ControllableXhr.current?.upload.onprogress?.({ lengthComputable: true, loaded, total }),
				respond: (status: number, body: string) => {
					const request = ControllableXhr.current;
					if (request) {
						request.status = status;
						request.responseText = body;
						request.onload?.();
					}
				},
				failNetwork: () => ControllableXhr.current?.onerror?.()
			}
		});
	});
	const call = (script: string) => page.evaluate(script).then(() => undefined);
	return {
		progress: (loaded, total) => call(`uploadDriver.progress(${loaded}, ${total})`),
		respond: (status, body) =>
			call(`uploadDriver.respond(${status}, ${JSON.stringify(JSON.stringify(body))})`),
		failNetwork: () => call('uploadDriver.failNetwork()')
	};
}
