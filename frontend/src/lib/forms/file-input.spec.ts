import { describe, expect, it } from 'vitest';
import { clearFileInput } from './file-input';

describe('clearFileInput', () => {
	it('empties the value of the input', () => {
		const input = { value: 'C:\\fakepath\\cover.png' } as HTMLInputElement;

		clearFileInput(input);

		expect(input.value).toBe('');
	});

	it('ignores an input that is not mounted yet', () => {
		expect(() => clearFileInput(undefined)).not.toThrow();
	});
});
