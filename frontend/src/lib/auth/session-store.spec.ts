import { beforeEach, describe, expect, it } from 'vitest';
import { SessionStore } from './session-store';

class MemoryStorage implements Storage {
	readonly #items = new Map<string, string>();

	get length(): number {
		return this.#items.size;
	}

	clear(): void {
		this.#items.clear();
	}

	getItem(key: string): string | null {
		return this.#items.get(key) ?? null;
	}

	key(index: number): string | null {
		return [...this.#items.keys()][index] ?? null;
	}

	removeItem(key: string): void {
		this.#items.delete(key);
	}

	setItem(key: string, value: string): void {
		this.#items.set(key, value);
	}
}

const NOW = 1_000_000;
const FIFTEEN_MINUTES_IN_SECONDS = 900;

describe('SessionStore', () => {
	let storage: MemoryStorage;
	let now: number;
	let store: SessionStore;

	beforeEach(() => {
		storage = new MemoryStorage();
		now = NOW;
		store = new SessionStore(
			() => storage,
			() => now
		);
	});

	it('saves and reads a session with its expiry date', () => {
		store.save('jwt', 'alice', FIFTEEN_MINUTES_IN_SECONDS);

		expect(store.read()).toEqual({
			accessToken: 'jwt',
			username: 'alice',
			expiresAt: NOW + FIFTEEN_MINUTES_IN_SECONDS * 1000
		});
	});

	it('returns null without session', () => {
		expect(store.read()).toBeNull();
	});

	it('forgets an expired session', () => {
		store.save('jwt', 'alice', FIFTEEN_MINUTES_IN_SECONDS);
		now += FIFTEEN_MINUTES_IN_SECONDS * 1000;

		expect(store.read()).toBeNull();
		expect(storage.length).toBe(0);
	});

	it('forgets corrupt or malformed sessions', () => {
		storage.setItem('bergacast.session', '{oops');
		expect(store.read()).toBeNull();

		storage.setItem('bergacast.session', JSON.stringify({ accessToken: 'jwt' }));
		expect(store.read()).toBeNull();
		expect(storage.length).toBe(0);
	});

	it('clears the session', () => {
		store.save('jwt', 'alice', FIFTEEN_MINUTES_IN_SECONDS);
		store.clear();

		expect(store.read()).toBeNull();
	});
});
