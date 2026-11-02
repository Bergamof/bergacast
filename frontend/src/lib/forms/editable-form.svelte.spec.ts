import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/problem';
import { fakeControlContainer } from '../testing/fake-control-container';
import { EditableForm, RELOADED_MESSAGE } from './editable-form.svelte';
import type { FieldErrors } from './field-errors';
import { noValidation } from './form-state.svelte';

interface Values {
	title: string;
}

/** Page data that a save or a refresh replaces, as `invalidate` does. */
function storedResource(title: string) {
	const stored = { current: { title } };
	return {
		stored,
		form: new EditableForm<Values>(() => stored.current, 'Modifications enregistrées.')
	};
}

describe('EditableForm', () => {
	it('edits a copy of the initial values', () => {
		const { stored, form } = storedResource('Initial');

		form.values.title = 'Edited';

		expect(stored.current.title).toBe('Initial');
	});

	it('sends the edited values, then reloads the saved ones and announces the save', async () => {
		const { stored, form } = storedResource('Initial');
		form.values.title = '  Edited  ';
		const action = vi.fn(async (values: Values) => {
			stored.current = { title: values.title.trim() };
		});

		await form.save(noValidation, action);

		expect(action).toHaveBeenCalledWith({ title: '  Edited  ' });
		expect(form.values).toEqual({ title: 'Edited' });
		expect(form.statusMessage).toBe('Modifications enregistrées.');
	});

	it('validates the edited values', async () => {
		const { form } = storedResource('Initial');
		form.values.title = '';
		const validate = vi.fn((values: Values): FieldErrors =>
			values.title ? {} : { title: 'obligatoire' }
		);

		await form.save(validate, vi.fn());

		expect(validate).toHaveBeenCalledWith({ title: '' });
		expect(form.submission.fieldErrors).toEqual({ title: 'obligatoire' });
	});

	it('keeps the edited values and announces nothing when the save fails', async () => {
		const { form } = storedResource('Initial');
		form.statusMessage = 'Modifications enregistrées.';
		form.values.title = 'Edited';

		await form.save(noValidation, () => Promise.reject(new ApiError(412, { title: 'Conflit' })));

		expect(form.values.title).toBe('Edited');
		expect(form.statusMessage).toBe('');
		expect(form.submission.problem?.status).toBe(412);
	});

	it('reloads the latest version, clears the problem and focuses the first control', async () => {
		const { stored, form } = storedResource('Initial');
		form.values.title = 'Edited';
		await form.save(noValidation, () => Promise.reject(new ApiError(412, { title: 'Conflit' })));
		const { container, control } = fakeControlContainer();

		await form.reload(async () => {
			stored.current = { title: 'Changed elsewhere' };
		}, container);

		expect(form.values.title).toBe('Changed elsewhere');
		expect(form.submission.problem).toBeNull();
		expect(form.statusMessage).toBe(RELOADED_MESSAGE);
		expect(container.querySelector).toHaveBeenCalledWith('input, select, textarea');
		expect(control.focus).toHaveBeenCalledOnce();
	});
});
