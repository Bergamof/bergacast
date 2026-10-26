import { withQuery } from '../api/client';
import type { PodcastImage } from '../api/resources';
import { formatFileSize } from '../format/file-size';

/** Media types accepted by the server for a podcast cover image. */
export const ACCEPTED_IMAGE_TYPES: readonly string[] = ['image/jpeg', 'image/png'];
/** Value of the `accept` attribute of the file input. */
export const IMAGE_FILE_ACCEPT = ACCEPTED_IMAGE_TYPES.join(',');
/** Default upload limit of the server (`20MB`, binary megabytes as in Spring's `DataSize`). */
export const MAX_IMAGE_FILE_SIZE_BYTES = 20 * 1024 * 1024;
/** Smallest side recommended by Apple Podcasts (square artwork, 1400 to 3000 px). */
export const RECOMMENDED_MIN_IMAGE_SIDE = 1400;

/** Checks a chosen cover file before sending it, mirroring the server rules that can be checked locally. */
export function validateImageFile(file: File | null | undefined): string | null {
	if (!file) {
		return 'Choisissez une image.';
	}
	if (!ACCEPTED_IMAGE_TYPES.includes(file.type)) {
		return `Type « ${file.type || 'inconnu'} » refusé : une image JPEG ou PNG est attendue.`;
	}
	if (file.size > MAX_IMAGE_FILE_SIZE_BYTES) {
		return `Image trop volumineuse (${formatFileSize(file.size)}) : ${formatFileSize(MAX_IMAGE_FILE_SIZE_BYTES)} au maximum.`;
	}
	return null;
}

/** Non-blocking advice when the stored image does not follow the podcast directories' recommendations. */
export function imageRecommendationWarnings(image: PodcastImage): string[] {
	const warnings: string[] = [];
	if (image.width !== image.height) {
		warnings.push(
			`L’image n’est pas carrée (${formatImageDimensions(image)}) : les applications d’écoute la recadreront ou la déformeront.`
		);
	}
	if (Math.min(image.width, image.height) < RECOMMENDED_MIN_IMAGE_SIDE) {
		warnings.push(
			`L’image est plus petite que ${RECOMMENDED_MIN_IMAGE_SIDE} × ${RECOMMENDED_MIN_IMAGE_SIDE} px : Apple Podcasts recommande une image carrée de ${RECOMMENDED_MIN_IMAGE_SIDE} à 3000 px de côté.`
		);
	}
	return warnings;
}

export function formatImageDimensions({ width, height }: Pick<PodcastImage, 'width' | 'height'>) {
	return `${width} × ${height} px`;
}

/**
 * The public image URL stays the same when the image is replaced and is cacheable: the content
 * hash is added so that the browser fetches the new image right away.
 */
export function imagePreviewUrl(image: Pick<PodcastImage, 'url' | 'sha256'>): string {
	return withQuery(image.url, { v: image.sha256 });
}
