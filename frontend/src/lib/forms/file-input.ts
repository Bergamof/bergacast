/** Empties a file input, which Svelte's `bind:files` cannot do by itself once a file was chosen. */
export function clearFileInput(input: HTMLInputElement | undefined): void {
	if (input) {
		input.value = '';
	}
}
