import { expect, test } from '@playwright/test';
import {
	PODCAST_API,
	PODCAST_PATH,
	SIGNED_IN,
	SIGNED_OUT,
	expectNoAxeViolations,
	heldResponse,
	problem
} from './support';

test.describe('login', () => {
	test('focuses the first invalid field after an empty submission', async ({ page }) => {
		await page.goto(`${SIGNED_OUT}/login`);

		await page.getByRole('button', { name: 'Se connecter' }).click();

		const username = page.getByLabel('Identifiant');
		await expect(username).toBeFocused();
		await expect(username).toHaveAttribute('aria-invalid', 'true');
		await expect(username).toHaveAccessibleDescription('Ce champ est obligatoire.');
	});

	test('ignores the button while pending and keeps the focus after a 401', async ({ page }) => {
		const response = heldResponse();
		await page.route('**/api/v1/access-tokens', async (route) => {
			await response.released;
			await problem(route, 401, 'Identifiants invalides');
		});
		await page.goto(`${SIGNED_OUT}/login`);
		await page.getByLabel('Identifiant').fill('admin');
		await page.getByLabel('Mot de passe').fill('wrong-password');

		const button = page.getByRole('button', { name: 'Se connecter' });
		await button.focus();
		await page.keyboard.press('Enter');
		await expect(page.getByRole('button', { name: 'Connexion…' })).toHaveAttribute(
			'aria-disabled',
			'true'
		);
		await expect(page.getByRole('button', { name: 'Connexion…' })).toBeFocused();
		response.release();

		await expect(page.getByRole('alert')).toContainText('Identifiants invalides');
		await expect(button).toBeFocused();
	});
});

test.describe('podcast settings', () => {
	const settings = (page: import('@playwright/test').Page) =>
		page.getByRole('region', { name: 'Réglages du podcast' });

	test('saves once despite a double click, keeps the focus and announces it', async ({ page }) => {
		const response = heldResponse();
		let patches = 0;
		await page.route(PODCAST_API, async (route) => {
			if (route.request().method() !== 'PATCH') {
				return route.continue();
			}
			patches++;
			await response.released;
			await route.fulfill({ status: 200, contentType: 'application/hal+json', body: '{}' });
		});
		await page.goto(`${SIGNED_IN}${PODCAST_PATH}`);
		const save = settings(page).getByRole('button', { name: 'Enregistrer' });

		await save.focus();
		await page.keyboard.press('Enter');
		await expect(settings(page).getByRole('button', { name: 'Enregistrement…' })).toBeFocused();
		// Playwright refuses to click an aria-disabled button: the keyboard reaches it all the same.
		await page.keyboard.press('Enter');
		response.release();

		await expect(settings(page).getByRole('status')).toHaveText('Modifications enregistrées.');
		await expect(save).toBeFocused();
		expect(patches).toBe(1);
	});

	test('after a 503, reloads the podcast, keeps the input and offers to rewrite the feed', async ({
		page
	}) => {
		let reads = 0;
		await page.route(PODCAST_API, (route) => {
			if (route.request().method() === 'PATCH') {
				return problem(route, 503, 'Stockage indisponible');
			}
			reads++;
			return route.continue();
		});
		await page.route('**/feed-publications', (route) =>
			route.fulfill({
				status: 200,
				contentType: 'application/json',
				body: JSON.stringify({
					feedUrl: 'https://media.example/x.xml',
					publishedAt: '',
					episodeCount: 3
				})
			})
		);
		await page.goto(`${SIGNED_IN}${PODCAST_PATH}`);
		const readsBefore = reads;
		await settings(page).getByLabel('Titre').fill('Titre modifié');

		await settings(page).getByRole('button', { name: 'Enregistrer' }).click();

		await expect(settings(page).getByRole('alert')).toContainText('Stockage indisponible');
		await expect.poll(() => reads).toBeGreaterThan(readsBefore);
		await expect(settings(page).getByLabel('Titre')).toHaveValue('Titre modifié');
		await settings(page).getByRole('button', { name: 'Réécrire le flux' }).click();
		await expect(settings(page).getByRole('status').first()).toHaveText(
			'Flux réécrit : 3 épisodes.'
		);
	});

	test('after a 412, reloads the latest version and focuses the first field', async ({ page }) => {
		await page.route(PODCAST_API, (route) =>
			route.request().method() === 'PATCH'
				? problem(route, 412, 'Precondition Failed')
				: route.continue()
		);
		await page.goto(`${SIGNED_IN}${PODCAST_PATH}`);
		const title = settings(page).getByLabel('Titre');
		await title.fill('Titre modifié');
		await settings(page).getByRole('button', { name: 'Enregistrer' }).click();

		await settings(page).getByRole('button', { name: 'Recharger la dernière version' }).click();

		await expect(title).toHaveValue('Mon podcast avec un titre assez long');
		await expect(title).toBeFocused();
		await expect(settings(page).getByRole('alert')).toHaveCount(0);
		await expect(settings(page).getByText('Dernière version chargée')).toBeVisible();
	});
});

test.describe('episode upload', () => {
	test('has no axe violation once opened', async ({ page }) => {
		await page.goto(`${SIGNED_IN}${PODCAST_PATH}/episodes`);

		await page.getByText('Publier un épisode depuis l’interface').click();

		await expect(page.getByLabel('Fichier audio')).toBeVisible();
		await expectNoAxeViolations(page);
	});

	test('focuses the first field the API rejects', async ({ page }) => {
		await page.route('**/api/v1/mon-podcast/episodes', (route) =>
			route.request().method() === 'POST'
				? route.fulfill({
						status: 422,
						contentType: 'application/problem+json',
						body: JSON.stringify({
							title: 'Données invalides',
							status: 422,
							errors: [{ field: 'title', message: 'Titre trop long' }]
						})
					})
				: route.continue()
		);
		await page.goto(`${SIGNED_IN}${PODCAST_PATH}/episodes`);
		await page.getByText('Publier un épisode depuis l’interface').click();
		await page.getByLabel('Fichier audio').setInputFiles({
			name: 'episode.mp3',
			mimeType: 'audio/mpeg',
			buffer: Buffer.alloc(1000)
		});
		const title = page.getByRole('textbox', { name: 'Titre', exact: true });
		await title.fill('Un titre');

		await page.getByRole('button', { name: 'Publier l’épisode' }).click();

		await expect(title).toBeFocused();
		await expect(title).toHaveAccessibleDescription('Titre trop long');
	});
});
