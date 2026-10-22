export const REQUIRED_MESSAGE = 'Ce champ est obligatoire.';

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const HTTP_PROTOCOLS = new Set(['http:', 'https:']);

export function isBlank(value: string): boolean {
	return value.trim() === '';
}

export function isEmail(value: string): boolean {
	return EMAIL_PATTERN.test(value.trim());
}

export function isHttpUrl(value: string): boolean {
	try {
		return HTTP_PROTOCOLS.has(new URL(value.trim()).protocol);
	} catch {
		return false;
	}
}

export function isIntegerAtLeast(value: number | null, minimum: number): value is number {
	return value !== null && Number.isInteger(value) && value >= minimum;
}

/** Trims a text input and maps an empty value to `null`, as the API expects for absent optional fields. */
export function blankToNull(value: string): string | null {
	const trimmed = value.trim();
	return trimmed === '' ? null : trimmed;
}
