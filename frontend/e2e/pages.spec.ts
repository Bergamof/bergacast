import { expect, test } from '@playwright/test';
import { PODCAST_PATH, SIGNED_IN, SIGNED_OUT, expectNoAxeViolations } from './support';

const PAGES = [
	`${SIGNED_OUT}/login`,
	`${SIGNED_IN}/podcasts`,
	`${SIGNED_IN}/podcasts/new`,
	`${SIGNED_IN}${PODCAST_PATH}`,
	`${SIGNED_IN}${PODCAST_PATH}/episodes`,
	`${SIGNED_IN}${PODCAST_PATH}/episodes/ep-2`,
	`${SIGNED_IN}${PODCAST_PATH}/tokens`,
	`${SIGNED_IN}/admin-users`,
	`${SIGNED_IN}/podcasts/inconnu`
];
const NARROWEST_WIDTH = 320;

for (const colorScheme of ['light', 'dark'] as const) {
	test.describe(`${colorScheme} mode`, () => {
		test.use({ colorScheme });

		for (const url of PAGES) {
			test(`${new URL(url).pathname} has no axe violation`, async ({ page }) => {
				await page.goto(url);
				await expect(page.locator('h1')).toBeVisible();

				await expectNoAxeViolations(page);
			});
		}
	});
}

test.describe('at 320 px', () => {
	test.use({ viewport: { width: NARROWEST_WIDTH, height: 800 } });

	for (const url of PAGES) {
		test(`${new URL(url).pathname} does not scroll horizontally`, async ({ page }) => {
			await page.goto(url);
			await expect(page.locator('h1')).toBeVisible();

			expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
				NARROWEST_WIDTH
			);
		});
	}
});
