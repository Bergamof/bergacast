import { expect, test } from '@playwright/test';
import { API_TOKENS_API, EPISODES_API, PODCAST_PATH, SIGNED_IN, heldResponse } from './support';

const EPISODE_TITLE = 'Épisode 2 : un titre';

test.describe('episode deletion', () => {
	let deletion: ReturnType<typeof heldResponse>;

	test.beforeEach(async ({ page }) => {
		let deleted = false;
		deletion = heldResponse();
		await page.route(`${EPISODES_API}/ep-2`, async (route) => {
			await deletion.released;
			deleted = true;
			await route.fulfill({ status: 204 });
		});
		await page.route(`${EPISODES_API}?*`, async (route) => {
			const listed = await route.fetch();
			const body = await listed.json();
			if (deleted) {
				body._embedded.episodes = body._embedded.episodes.filter(
					(episode: { id: string }) => episode.id !== 'ep-2'
				);
			}
			await route.fulfill({ response: listed, json: body });
		});
		await page.goto(`${SIGNED_IN}${PODCAST_PATH}/episodes`);
		await page.getByRole('button', { name: `Supprimer « ${EPISODE_TITLE} »` }).click();
	});

	test('focuses Cancel when the dialog opens', async ({ page }) => {
		await expect(page.getByRole('alertdialog')).toBeVisible();

		await expect(page.getByRole('button', { name: 'Annuler' })).toBeFocused();
	});

	test('keeps the dialog open on Escape while deleting, then focuses the list and announces it', async ({
		page
	}) => {
		const dialog = page.getByRole('alertdialog');
		await dialog.getByRole('button', { name: 'Supprimer', exact: true }).click();

		await page.keyboard.press('Escape');
		await expect(dialog.getByRole('button', { name: 'Annuler' })).toHaveAttribute(
			'aria-disabled',
			'true'
		);
		expect(await dialog.evaluate((element) => (element as HTMLDialogElement).open)).toBe(true);
		deletion.release();

		await expect(dialog).toBeHidden();
		await expect(page.getByRole('heading', { name: 'Épisodes', exact: true })).toBeFocused();
		await expect(page.getByRole('status').filter({ hasText: 'supprimé' })).toHaveText(
			`Épisode « ${EPISODE_TITLE} » supprimé.`
		);
		await expect(page.getByRole('heading', { name: EPISODE_TITLE })).toHaveCount(0);
	});
});

test.describe('API tokens', () => {
	test('focuses the created token, then the name field once dismissed', async ({ page }) => {
		await page.route(API_TOKENS_API, (route) =>
			route.request().method() === 'POST'
				? route.fulfill({
						status: 201,
						contentType: 'application/hal+json',
						body: JSON.stringify({
							id: 't3',
							name: 'Make',
							token: 'bgc_secret',
							tokenPrefix: 'bgc_secr',
							createdAt: '2026-11-02T10:00:00Z',
							lastUsedAt: null,
							_links: { self: { href: '/api/v1/podcasts/mon-podcast/api-tokens/t3' } }
						})
					})
				: route.continue()
		);
		await page.goto(`${SIGNED_IN}${PODCAST_PATH}/tokens`);
		await page.getByLabel('Nom').fill('Make');

		await page.getByRole('button', { name: 'Créer le jeton' }).click();

		await expect(page.getByText('Jeton « Make » créé.')).toBeVisible();
		await expect(page.getByRole('group', { name: 'Jeton « Make » créé.' })).toBeFocused();
		await page.getByRole('button', { name: 'J’ai copié le jeton' }).click();
		await expect(page.getByLabel('Nom')).toBeFocused();
	});

	test('focuses the list after a revocation and announces it', async ({ page }) => {
		await page.route(`${API_TOKENS_API}/t1`, (route) => route.fulfill({ status: 204 }));
		await page.goto(`${SIGNED_IN}${PODCAST_PATH}/tokens`);

		await page.getByRole('button', { name: 'Révoquer le jeton « n8n »' }).click();
		await page.getByRole('alertdialog').getByRole('button', { name: 'Révoquer' }).click();

		await expect(page.getByRole('heading', { name: 'Jetons existants' })).toBeFocused();
		await expect(page.getByRole('status').filter({ hasText: 'révoqué' })).toHaveText(
			'Jeton « n8n » révoqué.'
		);
	});
});

test.describe('administrators', () => {
	test('focuses the list after a deletion and announces it', async ({ page }) => {
		await page.route('**/api/v1/admin-users/u1', (route) => route.fulfill({ status: 204 }));
		await page.goto(`${SIGNED_IN}/admin-users`);

		await page.getByRole('button', { name: 'Supprimer le compte « adrien »' }).click();
		await page.getByRole('alertdialog').getByRole('button', { name: 'Supprimer' }).click();

		await expect(page.getByRole('heading', { name: 'Comptes existants' })).toBeFocused();
		await expect(page.getByRole('status').filter({ hasText: 'supprimé' })).toHaveText(
			'Compte « adrien » supprimé.'
		);
	});

	test('closes the confirmation on Escape and gives the focus back', async ({ page }) => {
		await page.goto(`${SIGNED_IN}/admin-users`);
		const opener = page.getByRole('button', { name: 'Supprimer le compte « adrien »' });
		await opener.click();

		await page.keyboard.press('Escape');

		await expect(page.getByRole('alertdialog')).toBeHidden();
		await expect(opener).toBeFocused();
	});
});
