export const TOKEN_PLACEHOLDER = 'bgc_VOTRE_JETON';

const EXAMPLE_METADATA = {
	title: 'Épisode 42',
	description: 'Description de l’épisode',
	publicationDate: '2026-11-01T06:00:00Z',
	durationSeconds: 1800
};

/** Shell single-quoting: the only character to escape inside '…' is the quote itself. */
function shellQuote(value: string): string {
	return `'${value.replaceAll("'", `'\\''`)}'`;
}

/** Example command showing automated tools how to publish an episode (multipart upload). */
export function buildPublishCurlExample(publishUrl: string): string {
	const metadataPart = `metadata=${JSON.stringify(EXAMPLE_METADATA)};type=application/json`;
	return [
		`curl -X POST ${shellQuote(publishUrl)}`,
		`  -H ${shellQuote(`Authorization: Bearer ${TOKEN_PLACEHOLDER}`)}`,
		`  -H ${shellQuote('Idempotency-Key: episode-42')}`,
		`  -F ${shellQuote(metadataPart)}`,
		`  -F ${shellQuote('audio=@episode-42.mp3;type=audio/mpeg')}`
	].join(' \\\n');
}
