import { expect, test, type Page } from '@playwright/test';
import {
	EPISODES_API,
	PODCAST_PATH,
	SIGNED_IN,
	expectNoAxeViolations,
	heldResponse
} from './support';

const AUDIO = {
	name: 'episode.mp3',
	mimeType: 'audio/mpeg',
	buffer: Buffer.alloc(2 * 1024 * 1024)
};

/** Starts an upload whose request stays pending (Playwright then reports no progress). */
async function startHeldUpload(page: Page) {
	const response = heldResponse();
	await page.route(EPISODES_API, async (route) => {
		if (route.request().method() !== 'POST') {
			return route.continue();
		}
		await response.released;
		await route.abort().catch(() => undefined);
	});
	await page.goto(`${SIGNED_IN}${PODCAST_PATH}/episodes`);
	await page.getByText('Publier un épisode depuis l’interface').click();
	await page.getByLabel('Fichier audio').setInputFiles(AUDIO);
	await page.getByRole('textbox', { name: 'Titre', exact: true }).fill('Un épisode');
	await page.getByRole('button', { name: 'Publier l’épisode' }).click();
	return { release: response.release };
}

test.describe('audio upload', () => {
	test('shows the chosen file, then the progress of the upload', async ({ page }) => {
		const upload = await startHeldUpload(page);

		await expect(page.getByText('episode.mp3 · 2 Mio')).toBeVisible();
		await expect(page.getByLabel('Envoi du fichier audio')).toBeVisible();
		await expect(page.getByText('Démarrage de l’envoi…')).toBeVisible();
		await expectNoAxeViolations(page);
		upload.release();
	});

	test('cancels the upload, says nothing was published and keeps the focus', async ({ page }) => {
		await startHeldUpload(page);

		await page.getByRole('button', { name: 'Annuler l’envoi' }).click();

		await expect(page.getByRole('alert')).toContainText('Envoi annulé');
		await expect(page.getByRole('button', { name: 'Publier l’épisode' })).toBeFocused();
		await expect(page.getByLabel('Envoi du fichier audio')).toHaveCount(0);
	});

	test('asks before leaving the page during an upload', async ({ page }) => {
		await startHeldUpload(page);
		const navigation = page.getByRole('navigation', { name: 'Navigation principale' });

		page.once('dialog', (dialog) => dialog.dismiss());
		await navigation.getByRole('link', { name: 'Administrateurs' }).click();
		await expect(page.getByRole('button', { name: 'Annuler l’envoi' })).toBeVisible();
		expect(new URL(page.url()).pathname).toBe(`${PODCAST_PATH}/episodes`);

		page.once('dialog', (dialog) => dialog.accept());
		await navigation.getByRole('link', { name: 'Administrateurs' }).click();
		await expect(page).toHaveURL(/\/admin-users$/);
	});
});

const SESSION_LIFETIME = 60 * 60_000;
const RENEWED_TOKEN = { accessToken: 'renewed-jwt', tokenType: 'Bearer', expiresIn: 900 };

test.describe('session renewal', () => {
	test.beforeEach(async ({ page }) => {
		// The signed-in server stores a session of one hour: time is moved forward instead of waited.
		await page.clock.install();
		await page.goto(`${SIGNED_IN}/podcasts`);
		await page.getByRole('link', { name: 'Nouveau podcast' }).focus();
	});

	test('offers to stay logged in one minute before the expiry, without leaving the page', async ({
		page
	}) => {
		await page.route('**/api/v1/access-tokens', (route) =>
			route.fulfill({
				status: 200,
				contentType: 'application/json',
				body: JSON.stringify(RENEWED_TOKEN)
			})
		);

		await page.clock.fastForward(SESSION_LIFETIME - 59_000);

		const dialog = page.getByRole('alertdialog', { name: 'Session bientôt expirée' });
		await expect(dialog).toBeVisible();
		const password = dialog.getByLabel('Mot de passe de « admin »');
		await expect(password).toBeFocused();
		await expectNoAxeViolations(page);
		await password.fill('the-password');
		await dialog.getByRole('button', { name: 'Rester connecté' }).click();

		await expect(dialog).toBeHidden();
		await expect(page.getByRole('link', { name: 'Nouveau podcast' })).toBeFocused();
		expect(await page.evaluate(() => sessionStorage.getItem('bergacast.session'))).toContain(
			'renewed-jwt'
		);
		await expect(page).toHaveURL(/\/podcasts$/);
	});

	test('keeps the session and the dialog after a wrong password', async ({ page }) => {
		await page.clock.fastForward(SESSION_LIFETIME - 59_000);
		const dialog = page.getByRole('alertdialog', { name: 'Session bientôt expirée' });

		await dialog.getByLabel('Mot de passe de « admin »').fill('wrong');
		await dialog.getByRole('button', { name: 'Rester connecté' }).click();

		await expect(dialog.getByRole('alert')).toContainText('Identifiants invalides');
		await expect(dialog).toBeVisible();
		await expect(page).toHaveURL(/\/podcasts$/);
	});

	test('still lets the user log in again in place once expired, or log out', async ({ page }) => {
		await page.clock.fastForward(SESSION_LIFETIME + 1_000);

		const dialog = page.getByRole('alertdialog', { name: 'Session expirée' });
		await expect(dialog).toBeVisible();
		await dialog.getByRole('button', { name: 'Se déconnecter' }).click();

		await expect(page).toHaveURL(/\/login$/);
	});
});
