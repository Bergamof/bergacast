import { defineConfig } from '@playwright/test';
import { SIGNED_IN_PORT, SIGNED_OUT_PORT } from './e2e/servers';

// Keyboard and screen reader journeys on the built SPA, served with the fake API of the Lighthouse audit.
const REQUEST_LOG = 'test-results/api-requests.log';

function server(port: number, signedIn: boolean) {
	return {
		command: `node lighthouse/server.mjs build ${port} ${REQUEST_LOG}${signedIn ? ' --signed-in' : ''}`,
		url: `http://localhost:${port}/login`,
		reuseExistingServer: !process.env.CI
	};
}

export default defineConfig({
	testDir: 'e2e',
	forbidOnly: !!process.env.CI,
	retries: 0,
	reporter: [['list'], ['json', { outputFile: 'test-results/e2e.json' }]],
	use: {
		// The installed Chrome (preinstalled on GitHub runners): no browser download.
		// E2E_CHROMIUM_PATH points to another Chromium build instead, e.g. in a sandbox.
		...(process.env.E2E_CHROMIUM_PATH
			? { launchOptions: { executablePath: process.env.E2E_CHROMIUM_PATH } }
			: { channel: 'chrome' }),
		locale: 'fr-FR',
		trace: 'retain-on-failure'
	},
	webServer: [server(SIGNED_OUT_PORT, false), server(SIGNED_IN_PORT, true)]
});
