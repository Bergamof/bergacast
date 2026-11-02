<script lang="ts">
	import { tick } from 'svelte';
	import { invalidate } from '$app/navigation';
	import { api } from '#lib/app.ts';
	import type { ApiToken, CreatedApiToken } from '#lib/api/resources.ts';
	import ConfirmButton from '#lib/components/ConfirmButton.svelte';
	import CopyableText from '#lib/components/CopyableText.svelte';
	import FormField from '#lib/components/FormField.svelte';
	import PendingButton from '#lib/components/PendingButton.svelte';
	import ProblemAlert from '#lib/components/ProblemAlert.svelte';
	import RequiredFieldsLegend from '#lib/components/RequiredFieldsLegend.svelte';
	import StatusMessage from '#lib/components/StatusMessage.svelte';
	import { singleFieldError } from '#lib/forms/field-errors.ts';
	import { FormState } from '#lib/forms/form-state.svelte.ts';
	import { REQUIRED_MESSAGE, isBlank } from '#lib/forms/validators.ts';
	import { formatDateTime } from '#lib/format/date-time.ts';
	import { focusThenAnnounce } from '#lib/forms/focus.ts';
	import { LoadDependency } from '#lib/load-dependencies.ts';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();

	const podcast = $derived(data.podcast.resource);
	let tokenName = $state('');
	let createdToken = $state<CreatedApiToken | null>(null);
	let createdTokenNotice: HTMLElement | undefined = $state();
	let tokenNameInput: HTMLInputElement | undefined = $state();
	let tokensHeading: HTMLElement | undefined = $state();
	let revocationMessage = $state('');
	const form = new FormState();

	async function handleCreate(
		event: SubmitEvent & { currentTarget: EventTarget & HTMLFormElement }
	): Promise<void> {
		event.preventDefault();
		const created = await form.submit(
			() => singleFieldError('name', isBlank(tokenName) ? REQUIRED_MESSAGE : null),
			async () => {
				createdToken = await api.createApiToken(podcast, tokenName.trim());
				tokenName = '';
				await invalidate(LoadDependency.apiTokens);
			},
			event.currentTarget
		);
		if (created) {
			// Read in full once focused, instead of a live region holding a button and the token.
			await tick();
			createdTokenNotice?.focus();
		}
	}

	function dismissCreatedToken(): void {
		createdToken = null;
		tokenNameInput?.focus();
	}

	async function revokeToken(token: ApiToken): Promise<void> {
		revocationMessage = '';
		await api.revokeApiToken(token);
		if (createdToken?.id === token.id) {
			createdToken = null;
		}
		await invalidate(LoadDependency.apiTokens);
	}
</script>

<svelte:head>
	<title>Jetons d’API — {podcast.title} — Bergacast</title>
</svelte:head>

<section class="card" aria-labelledby="new-token-title">
	<h2 id="new-token-title">Nouveau jeton d’API</h2>
	<p class="muted">
		Un jeton permet à un outil automatisé de publier et lister les épisodes de ce podcast
		uniquement. Créez un jeton par outil pour pouvoir les révoquer séparément.
	</p>

	{#if createdToken}
		<div
			class="notice warning"
			role="group"
			aria-labelledby="created-token-title"
			aria-describedby="created-token-warning"
			tabindex="-1"
			bind:this={createdTokenNotice}
		>
			<p>
				<strong id="created-token-title">Jeton « {createdToken.name} » créé.</strong>
				<span id="created-token-warning">
					Copiez-le maintenant : il ne sera plus jamais affiché. En cas de perte, révoquez-le et
					créez-en un autre.
				</span>
			</p>
			<CopyableText value={createdToken.token} label="Jeton d’API en clair" />
			<button type="button" class="secondary" onclick={dismissCreatedToken}>
				J’ai copié le jeton
			</button>
		</div>
	{/if}

	<form novalidate onsubmit={handleCreate}>
		<RequiredFieldsLegend />
		<FormField
			id="token-name"
			label="Nom"
			required
			error={form.fieldErrors.name}
			hint="Par exemple le nom de l’outil qui l’utilisera."
		>
			{#snippet control(attributes)}
				<input
					{...attributes}
					bind:value={tokenName}
					bind:this={tokenNameInput}
					autocomplete="off"
				/>
			{/snippet}
		</FormField>
		<ProblemAlert problem={form.problem} inlineFields={['name']} />
		<div class="actions">
			<PendingButton type="submit" pending={form.pending} pendingLabel="Création…">
				Créer le jeton
			</PendingButton>
		</div>
	</form>
</section>

<section class="card" aria-labelledby="tokens-title">
	<!-- Receives the focus after a revocation, the button that started it being gone. -->
	<h2 id="tokens-title" tabindex="-1" bind:this={tokensHeading}>Jetons existants</h2>
	<StatusMessage message={revocationMessage} />
	{#if data.apiTokens.length === 0}
		<p>Aucun jeton : aucun outil automatisé ne peut publier sur ce podcast.</p>
	{:else}
		<div class="table-wrapper">
			<table aria-labelledby="tokens-title">
				<thead>
					<tr>
						<th scope="col">Nom</th>
						<th scope="col">Préfixe</th>
						<th scope="col">Créé le</th>
						<th scope="col">Dernière utilisation</th>
						<th scope="col"><span class="visually-hidden">Actions</span></th>
					</tr>
				</thead>
				<tbody>
					{#each data.apiTokens as token (token.id)}
						<tr>
							<td>{token.name}</td>
							<td><code>{token.tokenPrefix}…</code></td>
							<td>{formatDateTime(token.createdAt)}</td>
							<td>{token.lastUsedAt ? formatDateTime(token.lastUsedAt) : 'Jamais'}</td>
							<td>
								<ConfirmButton
									label="Révoquer"
									accessibleLabel="Révoquer le jeton « {token.name} »"
									title="Révoquer le jeton « {token.name} » ?"
									confirmLabel="Révoquer"
									onConfirm={() => revokeToken(token)}
									afterConfirm={() =>
										focusThenAnnounce(
											tokensHeading,
											() => (revocationMessage = `Jeton « ${token.name} » révoqué.`)
										)}
								>
									<p>Les outils qui l’utilisent ne pourront plus publier d’épisodes.</p>
								</ConfirmButton>
							</td>
						</tr>
					{/each}
				</tbody>
			</table>
		</div>
	{/if}
</section>
