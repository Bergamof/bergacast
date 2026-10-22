import type { Episode, EpisodeMetadata, EpisodeType, UploadMetadata } from '../api/resources';
import { fromDateTimeLocalValue, toDateTimeLocalValue } from '../format/date-time';
import { formatDuration, parseDuration } from '../format/duration';
import type { FieldErrors } from './field-errors';
import { REQUIRED_MESSAGE, blankToNull, isBlank, isIntegerAtLeast } from './validators';

/** `inherit` maps to `null`: the episode then follows the podcast's explicit flag. */
export type ExplicitChoice = 'inherit' | 'yes' | 'no';

const MINIMUM_SEASON = 1;
const MINIMUM_EPISODE_NUMBER = 1;
const AUDIO_MIME_PREFIX = 'audio/';

export interface EpisodeFormValues {
	title: string;
	description: string;
	/** `datetime-local` value, in the browser's time zone. */
	publicationDate: string;
	/** Free text accepted by {@link parseDuration}. */
	duration: string;
	season: number | null;
	episodeNumber: number | null;
	episodeType: EpisodeType;
	explicit: ExplicitChoice;
	/** Only used at upload time; the API generates a UUID when empty. */
	guid: string;
}

export interface EpisodeValidationOptions {
	/** Editing must not clear the date, whereas an upload without date is published now. */
	publicationDateRequired: boolean;
}

export function emptyEpisodeFormValues(): EpisodeFormValues {
	return {
		title: '',
		description: '',
		publicationDate: '',
		duration: '',
		season: null,
		episodeNumber: null,
		episodeType: 'FULL',
		explicit: 'inherit',
		guid: ''
	};
}

function toExplicitChoice(explicit: boolean | null): ExplicitChoice {
	if (explicit === null) {
		return 'inherit';
	}
	return explicit ? 'yes' : 'no';
}

function fromExplicitChoice(choice: ExplicitChoice): boolean | null {
	return choice === 'inherit' ? null : choice === 'yes';
}

export function episodeFormValues(episode: Episode): EpisodeFormValues {
	return {
		title: episode.title,
		description: episode.description ?? '',
		publicationDate: toDateTimeLocalValue(episode.publicationDate),
		duration: episode.durationSeconds === null ? '' : formatDuration(episode.durationSeconds),
		season: episode.season,
		episodeNumber: episode.episodeNumber,
		episodeType: episode.episodeType,
		explicit: toExplicitChoice(episode.explicit),
		guid: episode.guid
	};
}

export function validateEpisodeForm(
	values: EpisodeFormValues,
	options: EpisodeValidationOptions
): FieldErrors {
	const errors: FieldErrors = {};
	if (isBlank(values.title)) {
		errors.title = REQUIRED_MESSAGE;
	}
	if (isBlank(values.publicationDate)) {
		if (options.publicationDateRequired) {
			errors.publicationDate = REQUIRED_MESSAGE;
		}
	} else if (fromDateTimeLocalValue(values.publicationDate) === null) {
		errors.publicationDate = 'Date invalide.';
	}
	if (parseDuration(values.duration) === undefined) {
		errors.durationSeconds = 'Durée attendue au format h:mm:ss, m:ss ou en secondes.';
	}
	if (values.season !== null && !isIntegerAtLeast(values.season, MINIMUM_SEASON)) {
		errors.season = `Nombre entier supérieur ou égal à ${MINIMUM_SEASON} attendu.`;
	}
	if (
		values.episodeNumber !== null &&
		!isIntegerAtLeast(values.episodeNumber, MINIMUM_EPISODE_NUMBER)
	) {
		errors.episodeNumber = `Nombre entier supérieur ou égal à ${MINIMUM_EPISODE_NUMBER} attendu.`;
	}
	return errors;
}

/** Client-side check mirroring the API, which rejects non-audio parts with a 415. */
export function validateAudioFile(file: File | null | undefined): string | null {
	if (!file) {
		return 'Choisissez un fichier audio.';
	}
	if (!file.type.startsWith(AUDIO_MIME_PREFIX)) {
		return `Type « ${file.type || 'inconnu'} » refusé : un fichier audio (audio/*) est attendu.`;
	}
	return null;
}

/** Metadata for PATCH requests (the GUID is not editable once published). */
export function toEpisodeMetadata(values: EpisodeFormValues): EpisodeMetadata {
	return {
		title: values.title.trim(),
		description: blankToNull(values.description),
		publicationDate: fromDateTimeLocalValue(values.publicationDate),
		durationSeconds: parseDuration(values.duration) ?? null,
		season: values.season,
		episodeNumber: values.episodeNumber,
		episodeType: values.episodeType,
		explicit: fromExplicitChoice(values.explicit)
	};
}

/** Metadata for the upload `metadata` part; empty optional values are omitted to get server defaults. */
export function toUploadMetadata(values: EpisodeFormValues): UploadMetadata {
	const metadata = { ...toEpisodeMetadata(values), guid: blankToNull(values.guid) };
	const presentEntries = Object.entries(metadata).filter(([, value]) => value !== null);
	return Object.fromEntries(presentEntries) as UploadMetadata;
}
