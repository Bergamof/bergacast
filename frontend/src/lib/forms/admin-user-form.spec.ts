import { describe, expect, it } from 'vitest';
import {
	MINIMUM_PASSWORD_LENGTH,
	validateAdminUserForm,
	validatePassword
} from './admin-user-form';

describe('admin user validation', () => {
	it('requires a password of the minimum length', () => {
		expect(validatePassword('')).toContain('obligatoire');
		expect(validatePassword('x'.repeat(MINIMUM_PASSWORD_LENGTH - 1))).toContain('minimum');
		expect(validatePassword('x'.repeat(MINIMUM_PASSWORD_LENGTH))).toBeNull();
	});

	it('requires a username', () => {
		expect(validateAdminUserForm(' ', 'short')).toEqual({
			username: expect.any(String),
			password: expect.any(String)
		});
		expect(validateAdminUserForm('alice', 'correct horse battery')).toEqual({});
	});
});
