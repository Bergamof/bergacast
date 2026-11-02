import { expect, test } from '@playwright/test';
import { PODCAST_API, PODCAST_PATH, SIGNED_IN, SIGNED_OUT, heldResponse, problem } from './support';

test('shows and announces a slow navigation', async ({ page }) => {
	const response = heldResponse();
	await page.goto(`${SIGNED_IN}/podcasts`);
	await page.route(PODCAST_API, async (route) => {
		await response.released;
		await route.continue();
	});

	await page
		.getByRole('link', { name: 'Mon podcast avec un titre assez long', exact: true })
		.click();

	await expect(page.getByRole('status').filter({ hasText: 'Chargement…' })).toBeAttached();
	await expect(page.getByRole('main')).toHaveAttribute('aria-busy', 'true');
	response.release();
	await expect(page.getByRole('heading', { level: 1 })).toHaveText(
		'Mon podcast avec un titre assez long'
	);
	await expect(page.getByRole('main')).not.toHaveAttribute('aria-busy');
});

test('shows a failed load inside the layout, with a working retry', async ({ page }) => {
	let unavailable = true;
	await page.route(PODCAST_API, (route) =>
		unavailable ? problem(route, 503, 'Service indisponible') : route.continue()
	);
	await page.goto(`${SIGNED_IN}${PODCAST_PATH}`);

	await expect(page.getByRole('heading', { level: 1 })).toHaveText('Service indisponible');
	await expect(page).toHaveTitle('Service indisponible — Bergacast');
	await expect(page.getByRole('navigation', { name: 'Navigation principale' })).toBeVisible();
	unavailable = false;
	await page.getByRole('button', { name: 'Réessayer' }).click();

	await expect(page.getByRole('heading', { level: 1 })).toHaveText(
		'Mon podcast avec un titre assez long'
	);
});

test('names the page of an unknown podcast without offering a retry', async ({ page }) => {
	await page.goto(`${SIGNED_IN}/podcasts/inconnu`);

	await expect(page).toHaveTitle('Page introuvable — Bergacast');
	await expect(page.getByRole('button', { name: 'Réessayer' })).toHaveCount(0);
});

test('shows a loading message until the first page has its data', async ({ page }) => {
	const response = heldResponse();
	await page.route('**/api/v1/podcasts?*', async (route) => {
		await response.released;
		await route.continue();
	});

	await page.goto(`${SIGNED_IN}/podcasts`, { waitUntil: 'commit' });

	await expect(
		page.getByRole('status').filter({ hasText: 'Chargement de Bergacast…' })
	).toBeVisible();
	response.release();
	await expect(page.getByRole('heading', { level: 1 })).toHaveText('Podcasts');
	await expect(page.getByText('Chargement de Bergacast…')).toHaveCount(0);
});

test('announces an expired session on the login page', async ({ page }) => {
	await page.goto(`${SIGNED_OUT}/login?expired=true`);

	await expect(page.getByRole('status').filter({ hasText: 'expiré' })).toHaveText(
		'Votre session a expiré. Reconnectez-vous.'
	);
});
