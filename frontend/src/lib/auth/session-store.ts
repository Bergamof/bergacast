const STORAGE_KEY = 'bergacast.session';
const MILLISECONDS_PER_SECOND = 1000;

export interface Session {
	accessToken: string;
	username: string;
	/** Epoch milliseconds after which the token must no longer be used. */
	expiresAt: number;
}

function isSession(value: unknown): value is Session {
	if (typeof value !== 'object' || value === null) {
		return false;
	}
	const candidate = value as Record<string, unknown>;
	return (
		typeof candidate.accessToken === 'string' &&
		typeof candidate.username === 'string' &&
		typeof candidate.expiresAt === 'number'
	);
}

/**
 * Keeps the admin JWT in `sessionStorage`: it survives page reloads but not closing the tab,
 * which limits exposure of a token that grants full admin rights.
 */
export class SessionStore {
	readonly #getStorage: () => Storage;
	readonly #now: () => number;

	constructor(getStorage: () => Storage, now: () => number = Date.now) {
		this.#getStorage = getStorage;
		this.#now = now;
	}

	save(accessToken: string, username: string, expiresInSeconds: number): Session {
		const session: Session = {
			accessToken,
			username,
			expiresAt: this.#now() + expiresInSeconds * MILLISECONDS_PER_SECOND
		};
		this.#getStorage().setItem(STORAGE_KEY, JSON.stringify(session));
		return session;
	}

	/** Returns the stored session, or `null` (and forgets it) when it is missing, corrupt or expired. */
	read(): Session | null {
		const serialized = this.#getStorage().getItem(STORAGE_KEY);
		if (serialized === null) {
			return null;
		}
		const session = this.#parse(serialized);
		if (session === null || session.expiresAt <= this.#now()) {
			this.clear();
			return null;
		}
		return session;
	}

	clear(): void {
		this.#getStorage().removeItem(STORAGE_KEY);
	}

	#parse(serialized: string): Session | null {
		try {
			const value: unknown = JSON.parse(serialized);
			return isSession(value) ? value : null;
		} catch {
			return null;
		}
	}
}
