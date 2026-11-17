/**
 * French wording of the English messages of the Bergacast API (Problem Details titles and details, validation
 * messages): the interface is in French while the API, made for automated tools, answers in English. Anything not
 * listed here is shown as received.
 */

const STATUS_PHRASES: Record<number, string> = {
	400: 'Bad Request',
	401: 'Unauthorized',
	403: 'Forbidden',
	404: 'Not Found',
	405: 'Method Not Allowed',
	406: 'Not Acceptable',
	409: 'Conflict',
	412: 'Precondition Failed',
	413: 'Content Too Large',
	415: 'Unsupported Media Type',
	422: 'Unprocessable Content',
	429: 'Too Many Requests',
	500: 'Internal Server Error',
	503: 'Service Unavailable'
};

const FRENCH_TITLES: Record<number, string> = {
	400: 'Requête invalide',
	401: 'Authentification requise',
	403: 'Accès refusé',
	404: 'Ressource introuvable',
	405: 'Méthode non autorisée',
	406: 'Format non disponible',
	409: 'Conflit',
	412: 'Ressource modifiée entre-temps',
	413: 'Contenu trop volumineux',
	415: 'Type de contenu non supporté',
	422: 'Données invalides',
	429: 'Trop de tentatives, réessayez plus tard',
	500: 'Erreur du serveur',
	503: 'Service indisponible'
};

type Translation = readonly [RegExp, (match: RegExpMatchArray) => string];

const DETAILS: readonly Translation[] = [
	[/^Authentication failed$/, () => 'Identifiant ou mot de passe incorrect.'],
	[/^Access denied$/, () => 'Vous n’avez pas le droit d’effectuer cette action.'],
	[/^An unexpected error occurred$/, () => 'Une erreur inattendue est survenue côté serveur.'],
	[
		/^The request conflicts with an existing resource$/,
		() => 'La demande entre en conflit avec une ressource existante.'
	],
	[/^The request content is invalid$/, () => 'Le contenu envoyé est invalide.'],
	[
		/^The resource has been modified concurrently, fetch it again$/,
		() => 'La ressource a été modifiée entre-temps : rechargez-la.'
	],
	[
		/^The resource has been modified: If-Match does not match/,
		() => 'La ressource a été modifiée entre-temps : rechargez-la.'
	],
	[
		/^The object storage is unavailable: the feed of the podcast/,
		() =>
			'La modification est enregistrée, mais le flux n’a pas pu être réécrit dans le stockage. ' +
			'Ne la rejouez pas : réécrivez simplement le flux.'
	],
	[
		/^The object storage is unavailable, nothing was changed/,
		() => 'Le stockage est indisponible, rien n’a été modifié : réessayez plus tard.'
	],
	[/^Too many failed logins/, () => 'Trop d’échecs de connexion pour cet identifiant.'],
	[/^No podcast with slug '(.+)'$/, ([, slug]) => `Aucun podcast n’a l’identifiant « ${slug} ».`],
	[/^No administrator /, () => 'Cet administrateur n’existe pas.'],
	[
		/^The slug '(.+)' is already used by another podcast$/,
		([, slug]) => `L’identifiant « ${slug} » est déjà utilisé par un autre podcast.`
	],
	[
		/^The username '(.+)' is already taken$/,
		([, username]) => `Le nom d’utilisateur « ${username} » est déjà pris.`
	],
	[
		/^An administrator cannot delete their own account$/,
		() => 'Vous ne pouvez pas supprimer votre propre compte.'
	],
	[
		/^The last administrator cannot be deleted$/,
		() => 'Le dernier administrateur ne peut pas être supprimé.'
	],
	[/^Unknown sort property/, () => 'Tri demandé inconnu.'],
	[/^This collection can only be sorted by/, () => 'Tri demandé non disponible pour cette liste.'],
	[
		/^The request content exceeds the maximum size/,
		() => 'Le contenu envoyé dépasse la taille maximale.'
	],
	[/^The JSON request content exceeds/, () => 'Le contenu envoyé dépasse la taille maximale.'],
	[
		/must have an image\/jpeg or image\/png content type$/,
		() => 'L’image doit être au format JPEG ou PNG.'
	],
	[
		/^The audio part must have an audio\/\* content type/,
		() => 'Le fichier doit être un fichier audio.'
	]
];

const VIOLATIONS: readonly Translation[] = [
	[/^must not be blank$/, () => 'ne peut pas être vide'],
	[/^must not be null$/, () => 'est obligatoire'],
	[/^must not be empty$/, () => 'ne peut pas être vide'],
	[/^must be a well-formed email address$/, () => 'doit être une adresse e-mail valide'],
	[/^must be a valid URL$/, () => 'doit être une URL valide'],
	[
		/^size must be between (\d+) and (\d+)$/,
		([, min, max]) => `doit faire entre ${min} et ${max} caractères`
	],
	[/^must be greater than or equal to (\d+)$/, ([, min]) => `doit être supérieur ou égal à ${min}`],
	[/^is reserved by the application$/, () => 'est réservé par l’application'],
	[
		/^cannot be changed once the podcast is created$/,
		() => 'ne peut plus être modifié après la création'
	],
	[/^Invalid value$/, () => 'valeur invalide'],
	[/^must match "/, () => 'a un format invalide']
];

function translate(table: readonly Translation[], text: string): string {
	for (const [pattern, toFrench] of table) {
		const match = text.match(pattern);
		if (match) {
			return toFrench(match);
		}
	}
	return text;
}

/** The default title of Spring (the English reason phrase) becomes the French title of the status. */
export function translateTitle(status: number, title: string): string {
	return title === STATUS_PHRASES[status] ? (FRENCH_TITLES[status] ?? title) : title;
}

export function translateDetail(detail: string): string {
	return translate(DETAILS, detail);
}

export function translateViolation(message: string): string {
	return translate(VIOLATIONS, message);
}
