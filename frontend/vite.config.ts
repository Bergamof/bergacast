import { defineConfig } from 'vitest/config';
import adapter from '@sveltejs/adapter-static';
import { sveltekit } from '@sveltejs/kit/vite';

const BACKEND_URL = 'http://localhost:8080';
const BACKEND_PROXIED_PATHS = ['/api', '/v3', '/swagger-ui'];

export default defineConfig({
	plugins: [
		sveltekit({
			compilerOptions: {
				// Force runes mode for the project, except for libraries. Can be removed in svelte 6.
				runes: ({ filename }) =>
					filename.split(/[/\\]/).includes('node_modules') ? undefined : true
			},
			// Served at the root of its own origin; unknown paths fall back to index.html (client routing).
			// Brotli/gzip variants are written next to each file and served as is by the image.
			adapter: adapter({ fallback: 'index.html', precompress: true })
		})
	],
	server: {
		proxy: Object.fromEntries(BACKEND_PROXIED_PATHS.map((path) => [path, BACKEND_URL]))
	},
	test: {
		expect: { requireAssertions: true },
		include: ['src/**/*.{test,spec}.{js,ts}'],
		environment: 'node',
		coverage: {
			provider: 'v8',
			include: ['src/lib/**/*.ts'],
			exclude: ['src/lib/**/*.{test,spec}.ts', 'src/lib/testing/**']
		}
	}
});
