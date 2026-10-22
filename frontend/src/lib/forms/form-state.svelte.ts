import { problemFromError, type ProblemDetails } from '../api/problem';
import {
	INVALID_FORM_PROBLEM,
	fieldErrorsFromProblem,
	hasFieldErrors,
	type FieldErrors
} from './field-errors';

/** Submission state shared by every form: pending flag, problem and per-field errors. */
export class FormState {
	pending = $state(false);
	problem = $state<ProblemDetails | null>(null);
	fieldErrors = $state<FieldErrors>({});

	/**
	 * Runs client-side validation, then the action. Errors are captured into the state instead
	 * of being thrown. Resolves to `true` when the action succeeded.
	 */
	async submit(validate: () => FieldErrors, action: () => Promise<void>): Promise<boolean> {
		const clientErrors = validate();
		if (hasFieldErrors(clientErrors)) {
			this.fieldErrors = clientErrors;
			this.problem = INVALID_FORM_PROBLEM;
			return false;
		}
		this.pending = true;
		this.problem = null;
		this.fieldErrors = {};
		try {
			await action();
			return true;
		} catch (error) {
			this.problem = problemFromError(error);
			this.fieldErrors = fieldErrorsFromProblem(this.problem);
			return false;
		} finally {
			this.pending = false;
		}
	}

	reset(): void {
		this.problem = null;
		this.fieldErrors = {};
	}
}

export function noValidation(): FieldErrors {
	return {};
}
