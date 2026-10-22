import { describe, expect, it } from 'vitest';
import {
	formatDateTime,
	fromDateTimeLocalValue,
	isInFuture,
	toDateTimeLocalValue
} from './date-time';

describe('formatDateTime', () => {
	it('formats an instant in French', () => {
		expect(formatDateTime('2026-10-22T08:30:00Z', 'UTC')).toBe('22 oct. 2026, 08:30');
	});

	it('shows a dash for missing dates', () => {
		expect(formatDateTime(null)).toBe('—');
		expect(formatDateTime(undefined)).toBe('—');
	});
});

describe('isInFuture', () => {
	const now = new Date('2026-10-22T12:00:00Z');

	it('detects scheduled dates', () => {
		expect(isInFuture('2026-10-22T12:00:01Z', now)).toBe(true);
	});

	it('treats past and present dates as published', () => {
		expect(isInFuture('2026-10-22T12:00:00Z', now)).toBe(false);
		expect(isInFuture('2020-01-01T00:00:00Z', now)).toBe(false);
	});
});

describe('datetime-local conversion', () => {
	it('converts an instant to local date and time', () => {
		const localDate = new Date(2026, 0, 2, 3, 4, 59);

		expect(toDateTimeLocalValue(localDate.toISOString())).toBe('2026-01-02T03:04');
	});

	it('converts a local value back to an ISO instant', () => {
		expect(fromDateTimeLocalValue('2026-01-02T03:04')).toBe(
			new Date(2026, 0, 2, 3, 4).toISOString()
		);
	});

	it('maps empty and invalid values', () => {
		expect(toDateTimeLocalValue(null)).toBe('');
		expect(fromDateTimeLocalValue('  ')).toBeNull();
		expect(fromDateTimeLocalValue('not a date')).toBeNull();
	});
});
