/**
 * Decides what a 401 does. Normally it ends the session, but while the renewal dialog is open the
 * user is about to log in again in place: leaving for the login page would drop the page and the
 * work it holds (WCAG 2.2.5), so the logout waits for the dialog to close.
 */
export class UnauthorizedGate {
	readonly #endSession: () => void;
	#held = false;
	#unauthorizedWhileHeld = false;

	constructor(endSession: () => void) {
		this.#endSession = endSession;
	}

	/** Called for each 401 of an authenticated request. */
	handle(): void {
		if (this.#held) {
			this.#unauthorizedWhileHeld = true;
			return;
		}
		this.#endSession();
	}

	/** Defers the logout the 401s would cause, until `release`. */
	hold(): void {
		this.#held = true;
	}

	/** Lifts the deferral. Returns whether a 401 came in the meantime (the data may be stale). */
	release(): boolean {
		const unauthorized = this.#unauthorizedWhileHeld;
		this.#held = false;
		this.#unauthorizedWhileHeld = false;
		return unauthorized;
	}
}
