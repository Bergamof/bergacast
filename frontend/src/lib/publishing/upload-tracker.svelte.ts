import type { UploadTransfer } from '../api/client';
import { formatFileSize } from '../format/file-size';

const PERCENT = 100;
/** Screen readers hear the progress every 10 %, not at each of the many browser events. */
const ANNOUNCEMENT_STEP_PERCENT = 10;

/**
 * Progress of an audio upload, which may last minutes (hundreds of MB): bytes sent, a polite
 * announcement every 10 %, and cancellation while the file is being sent. Once every byte is
 * sent, the server still stores the episode: cancelling then would not stop it, so it is no
 * longer offered.
 */
export class UploadTracker {
	loaded = $state(0);
	total = $state(0);
	/** Text of a live region: changes only at each 10 % step. */
	announcement = $state('');
	#controller = $state<AbortController | null>(null);
	#announcedStep = -1;

	get active(): boolean {
		return this.#controller !== null;
	}

	get percent(): number {
		return this.total > 0 ? Math.floor((this.loaded / this.total) * PERCENT) : 0;
	}

	get sent(): boolean {
		return this.total > 0 && this.loaded >= this.total;
	}

	get cancellable(): boolean {
		return this.active && !this.sent;
	}

	/** « 120 Mio / 480 Mio (25 %) », once the browser has reported the size to send. */
	get label(): string {
		if (this.total === 0) {
			return 'Démarrage de l’envoi…';
		}
		return `${formatFileSize(this.loaded)} / ${formatFileSize(this.total)} (${this.percent} %)`;
	}

	/** Starts following a new upload: pass the returned transfer to the request. */
	start(): Required<UploadTransfer> {
		this.#controller = new AbortController();
		this.loaded = 0;
		this.total = 0;
		this.announcement = '';
		this.#announcedStep = -1;
		return {
			signal: this.#controller.signal,
			onUploadProgress: (loaded, total) => this.#progress(loaded, total)
		};
	}

	cancel(): void {
		if (this.cancellable) {
			this.#controller?.abort();
		}
	}

	finish(): void {
		this.#controller = null;
		this.announcement = '';
	}

	#progress(loaded: number, total: number): void {
		this.loaded = loaded;
		this.total = total;
		const step = Math.floor(this.percent / ANNOUNCEMENT_STEP_PERCENT);
		if (step > this.#announcedStep) {
			this.#announcedStep = step;
			this.announcement = this.sent
				? 'Fichier envoyé, enregistrement de l’épisode…'
				: `Envoi du fichier audio : ${this.percent} %`;
		}
	}
}
