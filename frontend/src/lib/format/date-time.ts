const LOCALE = 'fr-FR';

/** Formats an ISO-8601 instant for display, e.g. « 22 oct. 2026, 08:30 ». */
export function formatDateTime(iso: string | null | undefined, timeZone?: string): string {
	if (!iso) {
		return '—';
	}
	return new Intl.DateTimeFormat(LOCALE, {
		dateStyle: 'medium',
		timeStyle: 'short',
		timeZone
	}).format(new Date(iso));
}

export function isInFuture(iso: string, now: Date = new Date()): boolean {
	return new Date(iso).getTime() > now.getTime();
}

function padTwoDigits(value: number): string {
	return String(value).padStart(2, '0');
}

/** Converts an instant to the local-time value expected by `<input type="datetime-local">`. */
export function toDateTimeLocalValue(iso: string | null | undefined): string {
	if (!iso) {
		return '';
	}
	const date = new Date(iso);
	const datePart = `${date.getFullYear()}-${padTwoDigits(date.getMonth() + 1)}-${padTwoDigits(date.getDate())}`;
	const timePart = `${padTwoDigits(date.getHours())}:${padTwoDigits(date.getMinutes())}`;
	return `${datePart}T${timePart}`;
}

/** Converts a `datetime-local` value (browser local time) to an ISO-8601 instant, `null` if empty or invalid. */
export function fromDateTimeLocalValue(value: string): string | null {
	if (value.trim() === '') {
		return null;
	}
	const date = new Date(value);
	return Number.isNaN(date.getTime()) ? null : date.toISOString();
}
