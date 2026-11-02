import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/problem';
import { fakeControlContainer } from '../testing/fake-control-container';
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

	it('ignores any submission while another one is pending, then accepts the next one', async () => {
		const form = new FormState();
		let finish: () => void = () => {};
		const first = form.submit(
			noValidation,
			() => new Promise<void>((resolve) => (finish = resolve))
		);
		const second = vi.fn();
		const invalid = vi.fn(() => ({ title: 'obligatoire' }));

		const ignored = await form.submit(noValidation, second);
		const ignoredInvalid = await form.submit(invalid, second);

		expect([ignored, ignoredInvalid]).toEqual([false, false]);
		expect(second).not.toHaveBeenCalled();
		expect(invalid).not.toHaveBeenCalled();
		expect(form.pending).toBe(true);
		expect(form.problem).toBeNull();
		expect(form.fieldErrors).toEqual({});
		finish();
		expect(await first).toBe(true);
		expect(await form.submit(noValidation, second)).toBe(true);
		expect(second).toHaveBeenCalledOnce();
	});

	it('focuses the first invalid control after a client validation failure', async () => {
		const { container, control } = fakeControlContainer();

		const succeeded = await new FormState().submit(
			() => ({ title: 'obligatoire' }),
			vi.fn(),
			container
		);

		expect(succeeded).toBe(false);
		expect(container.querySelector).toHaveBeenCalledWith('[aria-invalid="true"]');
		expect(control.focus).toHaveBeenCalledOnce();
	});

	it('focuses the first invalid control after a validation problem of the API', async () => {
		const { container, control } = fakeControlContainer();
		const problem = { title: 'Données invalides', errors: [{ field: 'title', message: 'vide' }] };

		await new FormState().submit(
			noValidation,
			() => Promise.reject(new ApiError(422, problem)),
			container
		);

		expect(control.focus).toHaveBeenCalledOnce();
	});

	it.each([
		new ApiError(503, { title: 'Indisponible' }),
		new ApiError(422, { title: 'Données invalides' })
	])('leaves the focus alone on a problem without field errors: %s', async (failure) => {
		const { container } = fakeControlContainer();

		await new FormState().submit(noValidation, () => Promise.reject(failure), container);

		expect(container.querySelector).not.toHaveBeenCalled();
	});

	it('resets its errors', async () => {
		const form = new FormState();
		await form.submit(() => ({ title: 'obligatoire' }), vi.fn());

		form.reset();

		expect(form.problem).toBeNull();
		expect(form.fieldErrors).toEqual({});
	});
});
