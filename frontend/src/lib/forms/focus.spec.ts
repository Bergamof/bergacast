import { describe, expect, it, vi } from 'vitest';
import { fakeControlContainer } from '../testing/fake-control-container';
import { focusFirstControl, focusFirstInvalidControl, focusThenAnnounce } from './focus';

describe('focus helpers', () => {
	it('focuses the first invalid control', () => {
		const { container, control } = fakeControlContainer();

		focusFirstInvalidControl(container);

		expect(container.querySelector).toHaveBeenCalledWith('[aria-invalid="true"]');
		expect(control.focus).toHaveBeenCalledOnce();
	});

	it('focuses the first control', () => {
		const { container, control } = fakeControlContainer();

		focusFirstControl(container);

		expect(container.querySelector).toHaveBeenCalledWith('input, select, textarea');
		expect(control.focus).toHaveBeenCalledOnce();
	});

	it('does nothing without a container or a matching control', () => {
		const container = { querySelector: vi.fn(() => null) };

		expect(() => focusFirstInvalidControl(undefined)).not.toThrow();
		expect(() => focusFirstControl(container)).not.toThrow();
	});

	it('focuses the target before announcing', async () => {
		const calls: string[] = [];

		await focusThenAnnounce({ focus: () => void calls.push('focus') }, () =>
			calls.push('announce')
		);

		expect(calls).toEqual(['focus', 'announce']);
	});

	it('announces even without a target', async () => {
		const announce = vi.fn();

		await focusThenAnnounce(undefined, announce);

		expect(announce).toHaveBeenCalledOnce();
	});
});
