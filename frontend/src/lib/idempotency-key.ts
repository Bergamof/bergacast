const KEY_BYTES = 16;
const HEX_RADIX = 16;
const HEX_BYTE_WIDTH = 2;

/**
 * Random key for the `Idempotency-Key` header. Uses `getRandomValues` rather than
 * `crypto.randomUUID`, which browsers only expose on HTTPS or localhost.
 */
export function newIdempotencyKey(): string {
	const bytes = crypto.getRandomValues(new Uint8Array(KEY_BYTES));
	return Array.from(bytes, (byte) => byte.toString(HEX_RADIX).padStart(HEX_BYTE_WIDTH, '0')).join(
		''
	);
}
