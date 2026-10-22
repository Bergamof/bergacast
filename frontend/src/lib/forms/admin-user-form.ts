import type { FieldErrors } from './field-errors';
import { REQUIRED_MESSAGE, isBlank } from './validators';

export const MINIMUM_PASSWORD_LENGTH = 12;

export function validatePassword(password: string): string | null {
	if (password.length === 0) {
		return REQUIRED_MESSAGE;
	}
	if (password.length < MINIMUM_PASSWORD_LENGTH) {
		return `${MINIMUM_PASSWORD_LENGTH} caractères minimum.`;
	}
	return null;
}

export function validateAdminUserForm(username: string, password: string): FieldErrors {
	const errors: FieldErrors = {};
	if (isBlank(username)) {
		errors.username = REQUIRED_MESSAGE;
	}
	const passwordError = validatePassword(password);
	if (passwordError !== null) {
		errors.password = passwordError;
	}
	return errors;
}
