import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/problem';
import { INVALID_FORM_PROBLEM } from './field-errors';
import { FormState, noValidation } from './form-state.svelte';

describe('FormState', () => {
	it('does not run the action when client validation fails', async () => {
		const form = new FormState();
		const action = vi.fn();

		const succeeded = await form.submit(() => ({ title: 'obligatoire' }), action);

		expect(succeeded).toBe(false);
		expect(action).not.toHaveBeenCalled();
		expect(form.fieldErrors).toEqual({ title: 'obligatoire' });
		expect(form.problem).toEqual(INVALID_FORM_PROBLEM);
	});

	it('runs the action and clears previous errors', async () => {
		const form = new FormState();
		await form.submit(() => ({ title: 'obligatoire' }), vi.fn());

		const succeeded = await form.submit(noValidation, async () => {
			expect(form.pending).toBe(true);
		});

		expect(succeeded).toBe(true);
		expect(form.pending).toBe(false);
		expect(form.problem).toBeNull();
		expect(form.fieldErrors).toEqual({});
	});

	it('captures API problems and their field errors', async () => {
		const form = new FormState();
		const problem = { title: 'Conflit', errors: [{ field: 'slug', message: 'déjà utilisé' }] };

		const succeeded = await form.submit(noValidation, () =>
			Promise.reject(new ApiError(409, problem))
		);

		expect(succeeded).toBe(false);
		expect(form.problem).toEqual(problem);
		expect(form.fieldErrors).toEqual({ slug: 'déjà utilisé' });
		expect(form.pending).toBe(false);
	});

	it('ignores a submission while another one is pending', async () => {
		const form = new FormState();
		let finish: () => void = () => {};
		const first = form.submit(
			noValidation,
			() => new Promise<void>((resolve) => (finish = resolve))
		);
		const second = vi.fn();

		const ignored = await form.submit(noValidation, second);
		finish();

		expect(ignored).toBe(false);
		expect(second).not.toHaveBeenCalled();
		expect(await first).toBe(true);
	});

	it('focuses the first invalid control after a client validation failure', async () => {
		const control = { focus: vi.fn() };
		const formElement = { querySelector: vi.fn(() => control) };

		await new FormState().submit(() => ({ title: 'obligatoire' }), vi.fn(), formElement as never);

		expect(formElement.querySelector).toHaveBeenCalledWith('[aria-invalid="true"]');
		expect(control.focus).toHaveBeenCalledOnce();
	});

	it('focuses the first invalid control after a validation problem of the API', async () => {
		const control = { focus: vi.fn() };
		const formElement = { querySelector: vi.fn(() => control) };
		const problem = { title: 'Données invalides', errors: [{ field: 'title', message: 'vide' }] };

		await new FormState().submit(
			noValidation,
			() => Promise.reject(new ApiError(422, problem)),
			formElement as never
		);

		expect(control.focus).toHaveBeenCalledOnce();
	});

	it('leaves the focus alone on a problem without field errors', async () => {
		const formElement = { querySelector: vi.fn() };

		await new FormState().submit(
			noValidation,
			() => Promise.reject(new ApiError(503, { title: 'Indisponible' })),
			formElement as never
		);

		expect(formElement.querySelector).not.toHaveBeenCalled();
	});

	it('resets its errors', async () => {
		const form = new FormState();
		await form.submit(() => ({ title: 'obligatoire' }), vi.fn());

		form.reset();

		expect(form.problem).toBeNull();
		expect(form.fieldErrors).toEqual({});
	});
});
