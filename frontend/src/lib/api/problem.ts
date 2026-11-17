import { isAbortError } from './abort';
import { translateDetail, translateTitle, translateViolation } from './problem-messages';

/** A single invalid field, as listed in the `errors` member of a validation problem. */
export interface FieldViolation {
	field: string;
	message: string;
}

/** RFC 9457 Problem Details, as returned by the Bergacast API (`application/problem+json`). */
export interface ProblemDetails {
	type?: string;
	title?: string;
	status?: number;
	detail?: string;
	instance?: string;
	errors?: FieldViolation[];
}

/** Status used when the server could not be reached at all (no HTTP response). */
export const NETWORK_ERROR_STATUS = 0;
/** Concurrent change: the `If-Match` version is no longer the stored one. */
export const PRECONDITION_FAILED = 412;
/** Also answered after a saved change whose feed could not be written (see `changeThenRefresh`). */
export const SERVICE_UNAVAILABLE = 503;

export class ApiError extends Error {
	readonly status: number;
	readonly problem: ProblemDetails;

	constructor(status: number, problem: ProblemDetails) {
		super(problem.detail ?? problem.title ?? `Erreur HTTP ${status}`);
		this.name = 'ApiError';
		this.status = status;
		this.problem = problem;
	}

	get isUnauthorized(): boolean {
		return this.status === 401;
	}

	get isPreconditionFailed(): boolean {
		return this.status === PRECONDITION_FAILED;
	}

	get isServiceUnavailable(): boolean {
		return this.status === SERVICE_UNAVAILABLE;
	}
}

const DEFAULT_TITLES: Record<number, string> = {
	[NETWORK_ERROR_STATUS]: 'Serveur injoignable',
	400: 'Requête invalide',
	401: 'Authentification requise',
	403: 'Accès refusé',
	404: 'Ressource introuvable',
	409: 'Conflit',
	412: 'Ressource modifiée entre-temps',
	413: 'Contenu trop volumineux',
	415: 'Type de contenu non supporté',
	422: 'Données invalides',
	429: 'Trop de tentatives, réessayez plus tard',
	500: 'Erreur du serveur',
	503: 'Service indisponible'
};

function isFieldViolation(value: unknown): value is FieldViolation {
	if (typeof value !== 'object' || value === null) {
		return false;
	}
	const candidate = value as Record<string, unknown>;
	return typeof candidate.field === 'string' && typeof candidate.message === 'string';
}

function translated(violation: FieldViolation): FieldViolation {
	return { field: violation.field, message: translateViolation(violation.message) };
}

function optionalString(value: unknown): string | undefined {
	return typeof value === 'string' && value.length > 0 ? value : undefined;
}

/** Builds a well-formed problem from an untrusted response body, in French (default title, known API messages). */
export function toProblemDetails(status: number, body: unknown): ProblemDetails {
	const source = typeof body === 'object' && body !== null ? (body as Record<string, unknown>) : {};
	const errors = Array.isArray(source.errors)
		? source.errors.filter(isFieldViolation).map(translated)
		: [];
	const title = optionalString(source.title);
	const detail = optionalString(source.detail);
	return {
		type: optionalString(source.type),
		title: title
			? translateTitle(status, title)
			: (DEFAULT_TITLES[status] ?? `Erreur HTTP ${status}`),
		status,
		detail: detail ? translateDetail(detail) : undefined,
		instance: optionalString(source.instance),
		errors: errors.length > 0 ? errors : undefined
	};
}

/** Tells how long to wait when the server answered `429` with a `Retry-After` in seconds. */
export function withRetryDelay(problem: ProblemDetails, retryAfter: string | null): ProblemDetails {
	const seconds = Number(retryAfter);
	if (retryAfter === null || !Number.isInteger(seconds) || seconds <= 0) {
		return problem;
	}
	return { ...problem, detail: `${problem.detail ?? ''} Réessayez dans ${seconds} s.`.trim() };
}

/** Converts anything thrown by an API call into a problem that forms can display. */
export function problemFromError(error: unknown): ProblemDetails {
	if (error instanceof ApiError && error.isPreconditionFailed) {
		return {
			...error.problem,
			status: error.status,
			title: 'Modification concurrente',
			detail:
				'Cette ressource a été modifiée ailleurs depuis l’ouverture de la page. ' +
				'Rechargez la dernière version, puis refaites vos changements.'
		};
	}
	if (error instanceof ApiError) {
		return error.problem;
	}
	// Only uploads can be cancelled from the interface (`UploadTracker`).
	if (isAbortError(error)) {
		return { title: 'Envoi annulé', detail: 'Rien n’a été publié.' };
	}
	return {
		title: 'Erreur inattendue',
		detail: error instanceof Error ? error.message : String(error)
	};
}
