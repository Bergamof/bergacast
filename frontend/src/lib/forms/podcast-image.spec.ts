import { describe, expect, it } from 'vitest';
import type { PodcastImage } from '../api/resources';
import {
	IMAGE_FILE_ACCEPT,
	MAX_IMAGE_FILE_SIZE_BYTES,
	formatImageDimensions,
	imagePreviewUrl,
	imageRecommendationWarnings,
	validateImageFile
} from './podcast-image';

function image(width: number, height: number): PodcastImage {
	return {
		mimeType: 'image/jpeg',
		width,
		height,
		sizeBytes: 1000,
		sha256: 'abc',
		url: 'https://api.example/demo/image.jpg'
	};
}

function fileOfSize(size: number, type: string): File {
	const file = new File(['x'], 'cover', { type });
	Object.defineProperty(file, 'size', { value: size });
	return file;
}

describe('validateImageFile', () => {
	it('requires a file', () => {
		expect(validateImageFile(undefined)).toBe('Choisissez une image.');
	});

	it.each(['image/jpeg', 'image/png'])('accepts %s', (type) => {
		expect(validateImageFile(new File(['x'], 'cover', { type }))).toBeNull();
	});

	it.each([
		['image/gif', 'image/gif'],
		['', 'inconnu']
	])('rejects the type %s', (type, shown) => {
		expect(validateImageFile(new File(['x'], 'cover', { type }))).toBe(
			`Type « ${shown} » refusé : une image JPEG ou PNG est attendue.`
		);
	});

	it('accepts a file at the size limit', () => {
		expect(validateImageFile(fileOfSize(MAX_IMAGE_FILE_SIZE_BYTES, 'image/png'))).toBeNull();
	});

	it('rejects a file over the size limit', () => {
		expect(validateImageFile(fileOfSize(MAX_IMAGE_FILE_SIZE_BYTES + 1, 'image/png'))).toBe(
			'Image trop volumineuse (20 Mio) : 20 Mio au maximum.'
		);
	});
});

describe('IMAGE_FILE_ACCEPT', () => {
	it('lists the accepted media types', () => {
		expect(IMAGE_FILE_ACCEPT).toBe('image/jpeg,image/png');
	});
});

describe('imageRecommendationWarnings', () => {
	it.each([
		[1400, 1400],
		[3000, 3000]
	])('has no warning for a %i × %i image', (width, height) => {
		expect(imageRecommendationWarnings(image(width, height))).toEqual([]);
	});

	it('warns about a non-square image', () => {
		const warnings = imageRecommendationWarnings(image(3000, 2000));

		expect(warnings).toHaveLength(1);
		expect(warnings[0]).toContain('pas carrée (3000 × 2000 px)');
	});

	it('warns about a small square image', () => {
		const warnings = imageRecommendationWarnings(image(1000, 1000));

		expect(warnings).toHaveLength(1);
		expect(warnings[0]).toContain('plus petite que 1400 × 1400 px');
	});

	it('gives both warnings for a small non-square image', () => {
		expect(imageRecommendationWarnings(image(1600, 900))).toHaveLength(2);
	});
});

describe('formatImageDimensions', () => {
	it('formats width and height in pixels', () => {
		expect(formatImageDimensions({ width: 1400, height: 1200 })).toBe('1400 × 1200 px');
	});
});

describe('imagePreviewUrl', () => {
	it('adds the content hash to bust the browser cache', () => {
		expect(imagePreviewUrl(image(1, 1))).toBe('https://api.example/demo/image.jpg?v=abc');
	});
});
