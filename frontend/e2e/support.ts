import AxeBuilder from '@axe-core/playwright';
import { expect, type Page, type Route } from '@playwright/test';
import { SIGNED_IN_PORT, SIGNED_OUT_PORT } from './servers';

export const SIGNED_OUT = `http://localhost:${SIGNED_OUT_PORT}`;
export const SIGNED_IN = `http://localhost:${SIGNED_IN_PORT}`;
export const PODCAST_PATH = '/podcasts/mon-podcast';
export const PODCAST_API = '**/api/v1/podcasts/mon-podcast';

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
