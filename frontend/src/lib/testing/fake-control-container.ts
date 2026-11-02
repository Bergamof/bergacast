import { vi } from 'vitest';
import type { ControlContainer } from '../forms/focus';

/** A container whose `querySelector` finds one control, to check focus moves without a DOM. */
export function fakeControlContainer() {
	const control = { focus: vi.fn() };
	const querySelector = vi.fn(() => control);
	return {
		container: { querySelector } as ControlContainer & { querySelector: typeof querySelector },
		control
	};
}
