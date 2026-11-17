import { describe, expect, it } from 'vitest';
import { ApiError, problemFromError, toProblemDetails, withRetryDelay } from './problem';

describe('toProblemDetails', () => {
	it('keeps the members of a valid problem', () => {
		expect(
			toProblemDetails(409, {
				type: 'https://bergacast/problems/slug-taken',
				title: 'Slug already used',
				detail: 'demo',
				instance: '/api/v1/podcasts'
			})
		).toEqual({
			type: 'https://bergacast/problems/slug-taken',
			title: 'Slug already used',
			status: 409,
			detail: 'demo',
			instance: '/api/v1/podcasts',
			errors: undefined
		});
	});

	it('uses a French default title and ignores malformed members', () => {
		expect(toProblemDetails(412, { title: 42, errors: 'nope' })).toMatchObject({
			title: 'Ressource modifiée entre-temps',
			errors: undefined
		});
	});

	it.each([
		[413, 'Contenu trop volumineux'],
		[429, 'Trop de tentatives, réessayez plus tard']
	])('gives a French default title to status %i', (status, title) => {
		expect(toProblemDetails(status, null).title).toBe(title);
	});

	it('falls back to a generic title for unknown statuses', () => {
		expect(toProblemDetails(418, null).title).toBe('Erreur HTTP 418');
	});
});

describe('ApiError', () => {
	it('uses the detail as message', () => {
		expect(new ApiError(400, { title: 'Bad', detail: 'Missing title' }).message).toBe(
			'Missing title'
		);
	});

	it('falls back to the title, then to the status', () => {
		expect(new ApiError(400, { title: 'Bad' }).message).toBe('Bad');
		expect(new ApiError(400, {}).message).toBe('Erreur HTTP 400');
	});

	it('tells a concurrent change (412) and an unavailable service (503)', () => {
		expect(new ApiError(412, {}).isPreconditionFailed).toBe(true);
		expect(new ApiError(409, {}).isPreconditionFailed).toBe(false);
		expect(new ApiError(503, {}).isServiceUnavailable).toBe(true);
		expect(new ApiError(500, {}).isServiceUnavailable).toBe(false);
	});
});

describe('problemFromError', () => {
	it('returns the problem of an API error', () => {
		const problem = { title: 'Conflit', status: 409 };

		expect(problemFromError(new ApiError(409, problem))).toBe(problem);
	});

	it('explains concurrent modifications (412)', () => {
		const problem = problemFromError(new ApiError(412, { title: 'Precondition Failed' }));

		expect(problem.title).toBe('Modification concurrente');
		expect(problem.detail).toContain('Rechargez');
		expect(problem.status).toBe(412);
	});

	it('tells a cancelled upload', () => {
		expect(problemFromError(new DOMException('x', 'AbortError'))).toEqual({
			title: 'Envoi annulé',
			detail: 'Rien n’a été publié.'
		});
	});

	it('wraps unexpected errors', () => {
		expect(problemFromError(new Error('boom'))).toEqual({
			title: 'Erreur inattendue',
			detail: 'boom'
		});
		expect(problemFromError('raw').detail).toBe('raw');
	});
});

describe('French wording of the API messages', () => {
	it('translates the default English title of a status', () => {
		expect(toProblemDetails(404, { title: 'Not Found' }).title).toBe('Ressource introuvable');
		expect(toProblemDetails(429, { title: 'Too Many Requests' }).title).toBe(
			'Trop de tentatives, réessayez plus tard'
		);
	});

	it('keeps a title that is not the default one of its status', () => {
		expect(toProblemDetails(503, { title: 'Stockage indisponible' }).title).toBe(
			'Stockage indisponible'
		);
	});

	it.each([
		['Authentication failed', 'Identifiant ou mot de passe incorrect.'],
		[
			"The slug 'demo' is already used by another podcast",
			'L’identifiant « demo » est déjà utilisé par un autre podcast.'
		],
		["The username 'ada' is already taken", 'Le nom d’utilisateur « ada » est déjà pris.'],
		[
			'The last administrator cannot be deleted',
			'Le dernier administrateur ne peut pas être supprimé.'
		],
		['An unexpected error occurred', 'Une erreur inattendue est survenue côté serveur.']
	])('translates the detail "%s"', (detail, french) => {
		expect(toProblemDetails(409, { detail }).detail).toBe(french);
	});

	it('does not claim the change was lost when only the feed could not be written', () => {
		const detail = toProblemDetails(503, {
			detail: "The object storage is unavailable: the feed of the podcast 'demo' was not written"
		}).detail;

		expect(detail).toContain('est enregistrée');
		expect(detail).toContain('Ne la rejouez pas');
	});

	it('translates validation messages and leaves unknown ones untouched', () => {
		const problem = toProblemDetails(422, {
			errors: [
				{ field: 'title', message: 'must not be blank' },
				{ field: 'slug', message: 'size must be between 1 and 64' },
				{ field: 'feed.maxEpisodes', message: 'must be greater than or equal to 1' },
				{ field: 'other', message: 'something new' }
			]
		});

		expect(problem.errors).toEqual([
			{ field: 'title', message: 'ne peut pas être vide' },
			{ field: 'slug', message: 'doit faire entre 1 et 64 caractères' },
			{ field: 'feed.maxEpisodes', message: 'doit être supérieur ou égal à 1' },
			{ field: 'other', message: 'something new' }
		]);
	});

	it('passes a French message through unchanged', () => {
		expect(toProblemDetails(409, { detail: 'Ce slug est déjà utilisé.' }).detail).toBe(
			'Ce slug est déjà utilisé.'
		);
	});
});

describe('withRetryDelay', () => {
	it('adds the delay announced by the server', () => {
		expect(withRetryDelay({ detail: 'Trop d’échecs.' }, '20').detail).toBe(
			'Trop d’échecs. Réessayez dans 20 s.'
		);
	});

	it.each([null, '', 'soon', '0', '-3', '1.5'])('ignores the Retry-After %j', (header) => {
		const problem = { detail: 'Trop d’échecs.' };

		expect(withRetryDelay(problem, header)).toBe(problem);
	});
});
