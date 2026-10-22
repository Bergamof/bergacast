import { describe, expect, it } from 'vitest';
import { TOKEN_PLACEHOLDER, buildPublishCurlExample } from './curl-example';

describe('buildPublishCurlExample', () => {
	const example = buildPublishCurlExample('https://podcasts.example.com/api/v1/demo/episodes');

	it('posts to the publish URL with a podcast token', () => {
		expect(example).toContain("curl -X POST 'https://podcasts.example.com/api/v1/demo/episodes'");
		expect(example).toContain(`-H 'Authorization: Bearer ${TOKEN_PLACEHOLDER}'`);
		expect(example).toContain("-H 'Idempotency-Key: episode-42'");
	});

	it('sends the metadata as a JSON part and the audio as a file part', () => {
		expect(example).toMatch(/-F 'metadata=\{.*"title":"Épisode 42".*\};type=application\/json'/);
		expect(example).toContain("-F 'audio=@episode-42.mp3;type=audio/mpeg'");
	});

	it('continues lines with backslashes', () => {
		expect(example.split(' \\\n')).toHaveLength(5);
	});

	it('escapes single quotes in the URL', () => {
		expect(buildPublishCurlExample("http://x/it's")).toContain("'http://x/it'\\''s'");
	});
});
