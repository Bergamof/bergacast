import { tick } from 'svelte';
import type { FieldErrors } from './field-errors';
import { focusFirstControl, type ControlContainer } from './focus';
import { FormState } from './form-state.svelte';

export const RELOADED_MESSAGE = 'Dernière version chargée : refaites vos changements.';

/**
 * Form editing a copy of a stored resource. The copy is reloaded from the page data once saved
 * (the server may normalize values) or on demand after a concurrent change (412). The form is
 * never re-created, so that the focus stays where it was.
 */
export class EditableForm<T> {
	readonly state = new FormState();
	values: T = $state() as T;
	statusMessage = $state('');
	readonly #initialValues: () => T;

	/** `initialValues` reads the current page data, e.g. a prop of the form component. */
	constructor(initialValues: () => T) {
		this.#initialValues = initialValues;
		this.values = structuredClone(initialValues());
	}

	/** Submits the values; once saved, reloads them and announces `successMessage`. */
	async save(
		validate: (values: T) => FieldErrors,
		action: (values: T) => Promise<void>,
		successMessage: string,
		formElement?: ControlContainer
	): Promise<void> {
		this.statusMessage = '';
		const saved = await this.state.submit(
			() => validate(this.values),
			() => action($state.snapshot(this.values) as T),
			formElement
		);
		if (saved) {
			await this.#loadInitialValues();
			this.statusMessage = successMessage;
		}
	}

	/** Reloads the latest version (`refresh` updates the page data), then focuses the first control. */
	async reload(refresh: () => Promise<void>, formElement?: ControlContainer): Promise<void> {
		await refresh();
		await this.#loadInitialValues();
		this.state.reset();
		this.statusMessage = RELOADED_MESSAGE;
		focusFirstControl(formElement);
	}

	async #loadInitialValues(): Promise<void> {
		// Refreshed page data reaches the props of the form component on the next update only.
		await tick();
		this.values = structuredClone(this.#initialValues());
	}
}
