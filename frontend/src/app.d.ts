// See https://svelte.dev/docs/kit/types#app.d.ts
// for information about these interfaces
declare global {
	namespace App {
		// interface Error {}
		// interface Locals {}
		// interface PageData {}
		// interface PageState {}
		// interface Platform {}
	}

	/** Version of the SPA, replaced at build time by Vite (`define` in vite.config.ts). */
	const __BERGACAST_VERSION__: string;

	/** Set by `/config.js`, loaded before the application. */
	var bergacastConfig: import('#lib/runtime-config.ts').RuntimeConfig | undefined;
}

export {};
