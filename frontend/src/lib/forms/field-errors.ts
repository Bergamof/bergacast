import type { ProblemDetails } from '../api/problem';

/** Error message per form field name. */
export type FieldErrors = Record<string, string>;

/**
 * The API reports nested properties with their path (e.g. `feed.maxEpisodes`) while forms use
 * flat field names, so only the last path segment is kept.
 */
export function toFormFieldName(apiField: string): string {
	return apiField.slice(apiField.lastIndexOf('.') + 1);
}

export function fieldErrorsFromProblem(problem: ProblemDetails | null): FieldErrors {
	const fieldErrors: FieldErrors = {};
	for (const { field, message } of problem?.errors ?? []) {
		const name = toFormFieldName(field);
		fieldErrors[name] = fieldErrors[name] ? `${fieldErrors[name]} ${message}` : message;
	}
	return fieldErrors;
}

/** Errors of a form with a single validated field. */
export function singleFieldError(field: string, message: string | null): FieldErrors {
	return message === null ? {} : { [field]: message };
}

export function hasFieldErrors(fieldErrors: FieldErrors): boolean {
	return Object.keys(fieldErrors).length > 0;
}

/** Problem shown above a form when client-side validation fails, before any request is sent. */
export const INVALID_FORM_PROBLEM: ProblemDetails = {
	title: 'Formulaire incomplet',
	detail: 'Corrigez les champs signalés puis réessayez.'
};
