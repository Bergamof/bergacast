import { tick } from 'svelte';

/** Where a control is looked for: a form, or any element containing controls. */
export type ControlContainer = Pick<ParentNode, 'querySelector'>;

const INVALID_CONTROL_SELECTOR = '[aria-invalid="true"]';
const CONTROL_SELECTOR = 'input, select, textarea';

function focusFirst(container: ControlContainer | undefined, selector: string): void {
	container?.querySelector<HTMLElement>(selector)?.focus();
}

/** Focusing the control announces its label and error (`aria-describedby`). */
export function focusFirstInvalidControl(container: ControlContainer | undefined): void {
	focusFirst(container, INVALID_CONTROL_SELECTOR);
}

export function focusFirstControl(container: ControlContainer | undefined): void {
	focusFirst(container, CONTROL_SELECTOR);
}

/**
 * Moves the focus, then writes a status message: screen readers cut a polite announcement short
 * when the focus moves after it.
 */
export async function focusThenAnnounce(
	target: Pick<HTMLElement, 'focus'> | undefined,
	announce: () => void
): Promise<void> {
	target?.focus();
	await tick();
	announce();
}
