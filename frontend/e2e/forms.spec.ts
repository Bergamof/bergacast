import { expect, test } from '@playwright/test';
import {
	EPISODES_API,
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
		await expect.poll(() => patches).toBe(1);
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
					publishedAt: '2026-11-02T10:00:00Z',
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
		await expect(settings(page).getByRole('status').filter({ hasText: 'Flux réécrit' })).toHaveText(
			'Flux réécrit : 3 épisodes. L’avertissement ci-dessus ne s’applique plus.'
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
		await page.route(EPISODES_API, (route) =>
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

test('podcast creation focuses the slug the API rejects', async ({ page }) => {
	await page.goto(`${SIGNED_IN}/podcasts/new`);
	await page.getByRole('textbox', { name: 'Identifiant (slug)' }).fill('mon-podcast');
	await page.getByRole('textbox', { name: 'Titre', exact: true }).fill('Mon podcast');
	await page.getByRole('textbox', { name: 'Langue' }).fill('fr');
	await page.getByRole('textbox', { name: 'Description' }).fill('Une description.');

	await page.getByRole('button', { name: 'Créer le podcast' }).click();

	const slug = page.getByRole('textbox', { name: 'Identifiant (slug)' });
	await expect(slug).toBeFocused();
	await expect(slug).toHaveAccessibleDescription(/Ce slug est déjà utilisé\./);
});

test.describe('episode image', () => {
	const EPISODE_PATH = `${PODCAST_PATH}/episodes/ep-2`;
	const EPISODE_IMAGE_API = '**/api/v1/mon-podcast/episodes/ep-2/image';

	test('shows the current image and offers to replace or delete it', async ({ page }) => {
		await page.goto(`${SIGNED_IN}${EPISODE_PATH}`);

		const section = page.getByRole('region', { name: 'Image de l’épisode' });
		await expect(section.getByRole('img', { name: /Couverture actuelle/ })).toBeVisible();
		await expect(section.getByLabel('Nouvelle image')).toBeVisible();
		await expect(section.getByRole('button', { name: 'Remplacer l’image' })).toBeVisible();
		await expect(section.getByRole('button', { name: 'Supprimer l’image' })).toBeVisible();
	});

	test('offers the image upload without image on an episode that has none', async ({ page }) => {
		await page.goto(`${SIGNED_IN}${PODCAST_PATH}/episodes/ep-3`);

		const section = page.getByRole('region', { name: 'Image de l’épisode' });
		await expect(section.getByText('les applications affichent l’image du podcast')).toBeVisible();
		await expect(section.getByRole('button', { name: 'Envoyer l’image' })).toBeVisible();
		await expect(section.getByRole('button', { name: 'Supprimer l’image' })).toHaveCount(0);
	});

	test('refuses a file that is not an image without calling the API', async ({ page }) => {
		let called = false;
		await page.route(EPISODE_IMAGE_API, (route) => {
			called = true;
			return route.abort();
		});
		await page.goto(`${SIGNED_IN}${EPISODE_PATH}`);
		const section = page.getByRole('region', { name: 'Image de l’épisode' });
		await section
			.getByLabel('Nouvelle image')
			.setInputFiles({ name: 'notes.txt', mimeType: 'text/plain', buffer: Buffer.from('x') });

		await section.getByRole('button', { name: 'Remplacer l’image' }).click();

		const field = section.getByLabel('Nouvelle image');
		await expect(field).toBeFocused();
		await expect(field).toHaveAccessibleDescription(/JPEG ou PNG/);
		expect(called).toBe(false);
	});

	test('shows the problem the API reports for an invalid image', async ({ page }) => {
		await page.route(EPISODE_IMAGE_API, (route) =>
			route.request().method() === 'PUT'
				? route.fulfill({
						status: 422,
						contentType: 'application/problem+json',
						body: JSON.stringify({
							title: 'Données invalides',
							status: 422,
							errors: [{ field: '$', message: 'is not a readable PNG image' }]
						})
					})
				: route.continue()
		);
		await page.goto(`${SIGNED_IN}${EPISODE_PATH}`);
		const section = page.getByRole('region', { name: 'Image de l’épisode' });
		await section.getByLabel('Nouvelle image').setInputFiles({
			name: 'cover.png',
			mimeType: 'image/png',
			buffer: Buffer.from('not a png')
		});

		await section.getByRole('button', { name: 'Remplacer l’image' }).click();

		await expect(section.getByRole('alert')).toContainText('is not a readable PNG image');
	});
});

test('episode upload offers an optional image', async ({ page }) => {
	await page.goto(`${SIGNED_IN}${PODCAST_PATH}/episodes`);
	await page.getByText('Publier un épisode depuis l’interface').click();

	const image = page.getByLabel('Image de l’épisode');
	await expect(image).toBeVisible();
	await expect(image).not.toHaveAttribute('required', '');
	await expect(image).toHaveAccessibleDescription(/Facultative/);
});
