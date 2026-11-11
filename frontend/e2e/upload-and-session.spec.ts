import { expect, test, type Page } from '@playwright/test';
import { SESSION_LIFETIME_MILLISECONDS } from '../lighthouse/session.mjs';
import {
	EPISODES_API,
	PODCAST_PATH,
	SIGNED_IN,
	expectNoAxeViolations,
	heldResponse,
	installControllableUploads
} from './support';

const AUDIO = {
	name: 'episode.mp3',
	mimeType: 'audio/mpeg',
	buffer: Buffer.alloc(2 * 1024 * 1024)
};

test.afterEach(async ({ page }) => {
	// Held responses are never released by some journeys: drop their pending routes.
	await page.unrouteAll({ behavior: 'ignoreErrors' });
});

async function startUpload(page: Page): Promise<void> {
	await page.goto(`${SIGNED_IN}${PODCAST_PATH}/episodes`);
	await page.getByText('Publier un épisode depuis l’interface').click();
	await page.getByLabel('Fichier audio').setInputFiles(AUDIO);
	await page.getByRole('textbox', { name: 'Titre', exact: true }).fill('Un épisode');
	await page.getByRole('button', { name: 'Publier l’épisode' }).click();
}

/** Starts an upload whose request never gets an answer (Playwright then reports no progress). */
async function startPendingUpload(page: Page): Promise<void> {
	await page.route(EPISODES_API, (route) =>
		route.request().method() === 'POST' ? new Promise<void>(() => {}) : route.continue()
	);
	await startUpload(page);
}

const status = (page: Page, text: string) => page.getByRole('status').filter({ hasText: text });

test.describe('audio upload', () => {
	test('shows the chosen file and an upload in progress', async ({ page }) => {
		await startPendingUpload(page);

		await expect(page.getByText('episode.mp3 · 2 Mio')).toBeVisible();
		const progress = page.getByRole('progressbar', { name: 'Envoi du fichier audio' });
		await expect(progress).toBeVisible();
		await expect(progress).not.toHaveAttribute('value');
		await expect(page.getByRole('button', { name: 'Annuler l’envoi' })).toBeVisible();
		await expectNoAxeViolations(page);
	});

	test('announces every 10 %, then keeps the focus when the cancel button goes', async ({
		page
	}) => {
		const uploads = await installControllableUploads(page);
		await startUpload(page);
		const cancel = page.getByRole('button', { name: 'Annuler l’envoi' });
		await cancel.focus();

		await uploads.progress(25, 100);
		await expect(status(page, 'Envoi du fichier audio')).toHaveText(
			'Envoi du fichier audio : 25 %'
		);
		await expect(page.getByRole('progressbar')).toHaveAttribute('value', '25');
		await uploads.progress(100, 100);
		// Indeterminate while the server stores the episode, not back to 0 %.
		await expect(page.getByRole('progressbar')).not.toHaveAttribute('value');

		await expect(cancel).toHaveCount(0);
		await expect(page.getByRole('button', { name: 'Envoi en cours…' })).toBeFocused();
		await expect(status(page, 'Fichier envoyé')).toBeAttached();
		await uploads.respond(201, { id: 'ep-4' });
		await expect(status(page, 'publié')).toHaveText('Épisode « Un épisode » publié.');
	});

	test('keeps the focus in the form when the upload fails', async ({ page }) => {
		const uploads = await installControllableUploads(page);
		await startUpload(page);
		await page.getByRole('button', { name: 'Annuler l’envoi' }).focus();

		await uploads.failNetwork();

		await expect(page.getByRole('alert')).toContainText('Serveur injoignable');
		await expect(page.getByRole('button', { name: 'Publier l’épisode' })).toBeFocused();
	});

	test('cancels the upload, says nothing was published and keeps the focus', async ({ page }) => {
		await startPendingUpload(page);

		await page.getByRole('button', { name: 'Annuler l’envoi' }).click();

		await expect(page.getByRole('alert')).toContainText('Envoi annulé');
		await expect(page.getByRole('button', { name: 'Publier l’épisode' })).toBeFocused();
		await expect(page.getByRole('progressbar')).toHaveCount(0);
	});

	test('asks before leaving the page during an upload', async ({ page }) => {
		await startPendingUpload(page);
		const navigation = page.getByRole('navigation', { name: 'Navigation principale' });

		// click() can return before the dialog event arrives: wait for each dialog on its own, or a late
		// first dialog would be answered by both handlers.
		const link = navigation.getByRole('link', { name: 'Administrateurs' });
		const [firstWarning] = await Promise.all([page.waitForEvent('dialog'), link.click()]);
		await firstWarning.dismiss();
		await expect(page.getByRole('button', { name: 'Annuler l’envoi' })).toBeVisible();
		expect(new URL(page.url()).pathname).toBe(`${PODCAST_PATH}/episodes`);

		const [secondWarning] = await Promise.all([page.waitForEvent('dialog'), link.click()]);
		await secondWarning.accept();
		await expect(page).toHaveURL(/\/admin-users$/);
	});
});

const RENEWED_TOKEN = { accessToken: 'renewed-jwt', tokenType: 'Bearer', expiresIn: 900 };
const BEFORE_EXPIRY = SESSION_LIFETIME_MILLISECONDS - 50_000;
const AFTER_EXPIRY = SESSION_LIFETIME_MILLISECONDS + 1_000;

/** The login answers a renewed token, once `answered` resolves (at once by default). */
async function renewalSucceeds(page: Page, answered: Promise<void> = Promise.resolve()) {
	await page.route('**/api/v1/access-tokens', async (route) => {
		await answered;
		await route.fulfill({
			status: 200,
			contentType: 'application/json',
			body: JSON.stringify(RENEWED_TOKEN)
		});
	});
}

async function expectRenewedInPlace(page: Page): Promise<void> {
	await expect(page.getByRole('alertdialog')).toBeHidden();
	await expect(page.getByRole('link', { name: 'Nouveau podcast' })).toBeFocused();
	expect(await page.evaluate(() => sessionStorage.getItem('bergacast.session'))).toContain(
		'renewed-jwt'
	);
	await expect(page).toHaveURL(/\/podcasts$/);
}

test.describe('session renewal', () => {
	test.beforeEach(async ({ page }) => {
		// The signed-in server stores a session of one hour: time is moved forward instead of waited.
		await page.clock.install();
		await page.goto(`${SIGNED_IN}/podcasts`);
		await page.getByRole('link', { name: 'Nouveau podcast' }).focus();
	});

	test('offers to stay logged in before the expiry, without leaving the page', async ({ page }) => {
		const response = heldResponse();
		await renewalSucceeds(page, response.released);
		await page.clock.fastForward(BEFORE_EXPIRY);

		const dialog = page.getByRole('alertdialog', { name: 'Session bientôt expirée' });
		const password = dialog.getByLabel('Mot de passe de « admin »');
		await expect(password).toBeFocused();
		await expectNoAxeViolations(page);
		await password.fill('the-password');
		await dialog.getByRole('button', { name: 'Rester connecté' }).click();
		await page.keyboard.press('Escape');
		await expect(dialog).toBeVisible();
		response.release();

		await expectRenewedInPlace(page);
	});

	test('keeps the session and the dialog after a wrong password', async ({ page }) => {
		await page.clock.fastForward(BEFORE_EXPIRY);
		const dialog = page.getByRole('alertdialog', { name: 'Session bientôt expirée' });

		await dialog.getByLabel('Mot de passe de « admin »').fill('wrong');
		await dialog.getByRole('button', { name: 'Rester connecté' }).click();

		await expect(dialog.getByRole('alert')).toContainText('Identifiants invalides');
		await expect(dialog).toBeVisible();
		await expect(page).toHaveURL(/\/podcasts$/);
	});

	test('lets the user log in again in place once expired', async ({ page }) => {
		await renewalSucceeds(page);
		await page.clock.fastForward(AFTER_EXPIRY);

		const dialog = page.getByRole('alertdialog', { name: 'Session expirée' });
		await expect(dialog.getByRole('status')).toHaveText('Votre session a expiré.');
		await dialog.getByLabel('Mot de passe de « admin »').fill('the-password');
		await dialog.getByRole('button', { name: 'Rester connecté' }).click();

		await expectRenewedInPlace(page);
	});

	test('keeps the expired dialog open on Escape, until the user logs out', async ({ page }) => {
		await page.clock.fastForward(AFTER_EXPIRY);
		const dialog = page.getByRole('alertdialog', { name: 'Session expirée' });

		await page.keyboard.press('Escape');
		await expect(dialog).toBeVisible();
		await dialog.getByRole('button', { name: 'Se déconnecter' }).click();

		await expect(page).toHaveURL(/\/login$/);
	});
});
