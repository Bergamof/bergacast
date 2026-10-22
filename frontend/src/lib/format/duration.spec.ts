import { describe, expect, it } from 'vitest';
import { formatDuration, parseDuration } from './duration';

describe('formatDuration', () => {
	it.each([
		[0, '0:00'],
		[59, '0:59'],
		[61, '1:01'],
		[3600, '1:00:00'],
		[3723, '1:02:03'],
		[36000, '10:00:00']
	])('formats %i seconds as %s', (seconds, expected) => {
		expect(formatDuration(seconds)).toBe(expected);
	});

	it('shows a dash for unknown durations', () => {
		expect(formatDuration(null)).toBe('—');
		expect(formatDuration(undefined)).toBe('—');
	});
});

describe('parseDuration', () => {
	it.each([
		['90', 90],
		['1:30', 90],
		['75:00', 4500],
		['1:02:03', 3723],
		[' 0:05 ', 5]
	])('parses %s as %i seconds', (text, expected) => {
		expect(parseDuration(text)).toBe(expected);
	});

	it('returns null for an empty value', () => {
		expect(parseDuration('   ')).toBeNull();
	});

	it.each(['abc', '1:60', '1:00:60', '1:2:3:4', '-5', '1.5', '1::2'])('rejects %s', (text) => {
		expect(parseDuration(text)).toBeUndefined();
	});

	it('round-trips with formatDuration', () => {
		expect(parseDuration(formatDuration(5025))).toBe(5025);
	});
});
