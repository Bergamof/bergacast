const BYTES_PER_UNIT = 1024;
const UNITS = ['o', 'Kio', 'Mio', 'Gio', 'Tio'];
const LOCALE = 'fr-FR';
const NON_BREAKING_SPACE = '\u00a0';

/** Formats a byte count with binary units, e.g. « 12,3 Mio ». */
export function formatFileSize(bytes: number): string {
	let value = bytes;
	let unitIndex = 0;
	while (value >= BYTES_PER_UNIT && unitIndex < UNITS.length - 1) {
		value /= BYTES_PER_UNIT;
		unitIndex += 1;
	}
	const formatted = new Intl.NumberFormat(LOCALE, {
		maximumFractionDigits: unitIndex === 0 ? 0 : 1
	}).format(value);
	return `${formatted}${NON_BREAKING_SPACE}${UNITS[unitIndex]}`;
}
