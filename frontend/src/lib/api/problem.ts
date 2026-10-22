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
		return this.status === 412;
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
	413: 'Fichier trop volumineux',
	415: 'Type de contenu non supporté',
	422: 'Données invalides'
};

function isFieldViolation(value: unknown): value is FieldViolation {
	if (typeof value !== 'object' || value === null) {
		return false;
	}
	const candidate = value as Record<string, unknown>;
	return typeof candidate.field === 'string' && typeof candidate.message === 'string';
}

function optionalString(value: unknown): string | undefined {
	return typeof value === 'string' && value.length > 0 ? value : undefined;
}

/** Builds a well-formed problem from an untrusted response body, filling in a French default title. */
export function toProblemDetails(status: number, body: unknown): ProblemDetails {
	const source = typeof body === 'object' && body !== null ? (body as Record<string, unknown>) : {};
	const errors = Array.isArray(source.errors) ? source.errors.filter(isFieldViolation) : [];
	return {
		type: optionalString(source.type),
		title: optionalString(source.title) ?? DEFAULT_TITLES[status] ?? `Erreur HTTP ${status}`,
		status,
		detail: optionalString(source.detail),
		instance: optionalString(source.instance),
		errors: errors.length > 0 ? errors : undefined
	};
}

/** Converts anything thrown by an API call into a problem that forms can display. */
export function problemFromError(error: unknown): ProblemDetails {
	if (error instanceof ApiError && error.isPreconditionFailed) {
		return {
			...error.problem,
			title: 'Modification concurrente',
			detail:
				'Cette ressource a été modifiée ailleurs depuis l’ouverture de la page. ' +
				'Rechargez-la pour partir de la dernière version, puis refaites vos changements.'
		};
	}
	if (error instanceof ApiError) {
		return error.problem;
	}
	return {
		title: 'Erreur inattendue',
		detail: error instanceof Error ? error.message : String(error)
	};
}
