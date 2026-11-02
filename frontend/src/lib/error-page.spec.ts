import { describe, expect, it } from 'vitest';
import { errorPageTitle, isRetryable } from './error-page';

describe('errorPageTitle', () => {
	it.each([
		[403, 'Accès refusé'],
		[404, 'Page introuvable'],
		[503, 'Service indisponible'],
		[500, 'Erreur 500']
	])('names the status %i', (status, title) => {
		expect(errorPageTitle(status)).toBe(title);
	});
});

describe('isRetryable', () => {
	it('offers to retry server-side failures only', () => {
		expect(isRetryable(503)).toBe(true);
		expect(isRetryable(500)).toBe(true);
		expect(isRetryable(404)).toBe(false);
		expect(isRetryable(403)).toBe(false);
	});
});
