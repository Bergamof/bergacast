import { describe, expect, it } from 'vitest';
import { formatFileSize } from './file-size';

/** French number formatting uses (narrow) no-break spaces; normalize them for readable assertions. */
function normalizeSpaces(text: string): string {
	return text.replace(/[\u00a0\u202f]/g, ' ');
}

describe('formatFileSize', () => {
	it.each([
		[0, '0 o'],
		[512, '512 o'],
		[1024, '1 Kio'],
		[1536, '1,5 Kio'],
		[12_900_000, '12,3 Mio'],
		[3 * 1024 ** 3, '3 Gio'],
		[2 * 1024 ** 5, '2 048 Tio']
	])('formats %i bytes as %s', (bytes, expected) => {
		expect(normalizeSpaces(formatFileSize(bytes))).toBe(expected);
	});
});
