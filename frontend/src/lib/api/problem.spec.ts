import { describe, expect, it } from 'vitest';
import { ApiError, problemFromError, toProblemDetails } from './problem';

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
	});

	it('wraps unexpected errors', () => {
		expect(problemFromError(new Error('boom'))).toEqual({
			title: 'Erreur inattendue',
			detail: 'boom'
		});
		expect(problemFromError('raw').detail).toBe('raw');
	});
});
