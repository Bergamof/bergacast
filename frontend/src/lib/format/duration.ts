const SECONDS_PER_MINUTE = 60;
const MINUTES_PER_HOUR = 60;
const SECONDS_PER_HOUR = SECONDS_PER_MINUTE * MINUTES_PER_HOUR;
/** Sexagesimal base shared by the minutes and seconds components of `h:mm:ss`. */
const DURATION_COMPONENT_BASE = 60;
const MAX_DURATION_COMPONENTS = 3;
const DIGITS_ONLY = /^\d+$/;

function padTwoDigits(value: number): string {
	return String(value).padStart(2, '0');
}

/** Formats seconds as `h:mm:ss` (or `m:ss` under one hour), the iTunes duration style. */
export function formatDuration(totalSeconds: number | null | undefined): string {
	if (totalSeconds === null || totalSeconds === undefined) {
		return '—';
	}
	const hours = Math.floor(totalSeconds / SECONDS_PER_HOUR);
	const minutes = Math.floor((totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE);
	const seconds = totalSeconds % SECONDS_PER_MINUTE;
	return hours > 0
		? `${hours}:${padTwoDigits(minutes)}:${padTwoDigits(seconds)}`
		: `${minutes}:${padTwoDigits(seconds)}`;
}

/**
 * Parses `h:mm:ss`, `m:ss` or a plain number of seconds.
 * Returns `null` for an empty value and `undefined` for an invalid one.
 */
export function parseDuration(text: string): number | null | undefined {
	const trimmed = text.trim();
	if (trimmed === '') {
		return null;
	}
	const components = trimmed.split(':');
	if (
		components.length > MAX_DURATION_COMPONENTS ||
		!components.every((component) => DIGITS_ONLY.test(component))
	) {
		return undefined;
	}
	const values = components.map(Number);
	// Only the leading component may overflow, e.g. « 90 » seconds or « 75:00 » minutes.
	if (values.slice(1).some((value) => value >= DURATION_COMPONENT_BASE)) {
		return undefined;
	}
	return values.reduce((total, value) => total * DURATION_COMPONENT_BASE + value, 0);
}
