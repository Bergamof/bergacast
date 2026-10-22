<script lang="ts">
	import { invalidate } from '$app/navigation';
	import { api } from '#lib/app.ts';
	import type { ApiToken, CreatedApiToken } from '#lib/api/resources.ts';
	import ConfirmButton from '#lib/components/ConfirmButton.svelte';
	import CopyableText from '#lib/components/CopyableText.svelte';
	import FormField from '#lib/components/FormField.svelte';
	import ProblemAlert from '#lib/components/ProblemAlert.svelte';
	import { singleFieldError } from '#lib/forms/field-errors.ts';
	import { FormState } from '#lib/forms/form-state.svelte.ts';
	import { REQUIRED_MESSAGE, isBlank } from '#lib/forms/validators.ts';
	import { formatDateTime } from '#lib/format/date-time.ts';
	import { LoadDependency } from '#lib/load-dependencies.ts';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();

	const podcast = $derived(data.podcast.resource);
	let tokenName = $state('');
	let createdToken = $state<CreatedApiToken | null>(null);
	const form = new FormState();

	async function handleCreate(event: SubmitEvent): Promise<void> {
		event.preventDefault();
		await form.submit(
			() => singleFieldError('name', isBlank(tokenName) ? REQUIRED_MESSAGE : null),
			async () => {
				createdToken = await api.createApiToken(podcast, tokenName.trim());
				tokenName = '';
				await invalidate(LoadDependency.apiTokens);
			}
		);
	}

	async function revokeToken(token: ApiToken): Promise<void> {
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
		<div class="notice warning" role="status">
			<p>
				<strong>Jeton « {createdToken.name} » créé.</strong> Copiez-le maintenant : il ne sera plus jamais
				affiché. En cas de perte, révoquez-le et créez-en un autre.
			</p>
			<CopyableText value={createdToken.token} label="Jeton d’API en clair" />
			<button type="button" class="secondary" onclick={() => (createdToken = null)}>
				J’ai copié le jeton
			</button>
		</div>
	{/if}

	<form novalidate onsubmit={handleCreate}>
		<ProblemAlert problem={form.problem} inlineFields={['name']} />
		<FormField
			id="token-name"
			label="Nom"
			required
			error={form.fieldErrors.name}
			hint="Par exemple le nom de l’outil qui l’utilisera."
		>
			{#snippet control(attributes)}
				<input {...attributes} bind:value={tokenName} autocomplete="off" />
			{/snippet}
		</FormField>
		<div class="actions">
			<button type="submit" disabled={form.pending}>
				{form.pending ? 'Création…' : 'Créer le jeton'}
			</button>
		</div>
	</form>
</section>

<section class="card" aria-labelledby="tokens-title">
	<h2 id="tokens-title">Jetons existants</h2>
	{#if data.apiTokens.length === 0}
		<p>Aucun jeton : aucun outil automatisé ne peut publier sur ce podcast.</p>
	{:else}
		<div class="table-wrapper">
			<table>
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
