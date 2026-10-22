import { describe, expect, it } from 'vitest';
import {
	fieldErrorsFromProblem,
	hasFieldErrors,
	singleFieldError,
	toFormFieldName
} from './field-errors';

describe('fieldErrorsFromProblem', () => {
	it('maps API field paths to form field names', () => {
		expect(
			fieldErrorsFromProblem({
				errors: [
					{ field: 'feed.maxEpisodes', message: 'doit être ≥ 1' },
					{ field: 'title', message: 'obligatoire' },
					{ field: 'title', message: 'trop long' }
				]
			})
		).toEqual({ maxEpisodes: 'doit être ≥ 1', title: 'obligatoire trop long' });
	});

	it('returns no errors without problem or field errors', () => {
		expect(fieldErrorsFromProblem(null)).toEqual({});
		expect(fieldErrorsFromProblem({ title: 'Conflit' })).toEqual({});
	});
});

describe('field error helpers', () => {
	it('extracts the last path segment', () => {
		expect(toFormFieldName('a.b.c')).toBe('c');
		expect(toFormFieldName('slug')).toBe('slug');
	});

	it('builds single-field errors', () => {
		expect(singleFieldError('name', 'obligatoire')).toEqual({ name: 'obligatoire' });
		expect(singleFieldError('name', null)).toEqual({});
	});

	it('tells whether there are errors', () => {
		expect(hasFieldErrors({})).toBe(false);
		expect(hasFieldErrors({ name: 'x' })).toBe(true);
	});
});
