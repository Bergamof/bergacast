import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { spaVersion } from '#lib/version.ts';

function packageVersion(): string {
	const packageJson = new URL('../../package.json', import.meta.url);
	return (JSON.parse(readFileSync(packageJson, 'utf8')) as { version: string }).version;
}

describe('spaVersion', () => {
	it('is the version of package.json', () => {
		expect(spaVersion).toBe(packageVersion());
	});
});
