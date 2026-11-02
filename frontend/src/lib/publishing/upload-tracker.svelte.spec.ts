import { describe, expect, it } from 'vitest';
import { isAbortError } from '../api/upload-transport';
import { UploadTracker } from './upload-tracker.svelte';

const MIB = 1024 * 1024;

describe('UploadTracker', () => {
	it('is idle until an upload starts', () => {
		const tracker = new UploadTracker();

		expect([tracker.active, tracker.cancellable, tracker.percent]).toEqual([false, false, 0]);
	});

	it('tells the upload is starting until the browser reports its size', () => {
		const tracker = new UploadTracker();

		tracker.start();

		expect(tracker.label).toBe('Démarrage de l’envoi…');
	});

	it('follows the bytes sent', () => {
		const tracker = new UploadTracker();
		const transfer = tracker.start();

		transfer.onUploadProgress(120 * MIB, 480 * MIB);

		expect(tracker.percent).toBe(25);
		expect(tracker.label).toBe('120 Mio / 480 Mio (25 %)');
		expect(tracker.cancellable).toBe(true);
	});

	it('announces the progress at each 10 % step only', () => {
		const tracker = new UploadTracker();
		const transfer = tracker.start();
		const announcements: string[] = [];

		for (const loaded of [1, 5, 10, 15, 19, 20, 99]) {
			transfer.onUploadProgress(loaded, 100);
			announcements.push(tracker.announcement);
		}

		expect([...new Set(announcements)]).toEqual([
			'Envoi du fichier audio : 1 %',
			'Envoi du fichier audio : 10 %',
			'Envoi du fichier audio : 20 %',
			'Envoi du fichier audio : 99 %'
		]);
	});

	it('announces that the episode is being stored once every byte is sent, then no longer cancels', () => {
		const tracker = new UploadTracker();
		const transfer = tracker.start();

		transfer.onUploadProgress(100, 100);
		tracker.cancel();

		expect(tracker.announcement).toBe('Fichier envoyé, enregistrement de l’épisode…');
		expect(tracker.cancellable).toBe(false);
		expect(transfer.signal.aborted).toBe(false);
	});

	it('aborts the upload when cancelled while sending', () => {
		const tracker = new UploadTracker();
		const transfer = tracker.start();
		transfer.onUploadProgress(10, 100);

		tracker.cancel();

		expect(transfer.signal.aborted).toBe(true);
		expect(isAbortError(transfer.signal.reason)).toBe(true);
	});

	it('starts each upload afresh and stops following it once finished', () => {
		const tracker = new UploadTracker();
		tracker.start().onUploadProgress(100, 100);
		tracker.finish();

		expect([tracker.active, tracker.announcement]).toEqual([false, '']);
		tracker.start();
		expect([tracker.active, tracker.loaded, tracker.total]).toEqual([true, 0, 0]);
	});
});
