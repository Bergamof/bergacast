<script lang="ts">
	import { invalidate } from '$app/navigation';
	import { api } from '#lib/app.ts';
	import type { AdminUser } from '#lib/api/resources.ts';
	import ConfirmButton from '#lib/components/ConfirmButton.svelte';
	import FormField from '#lib/components/FormField.svelte';
	import PasswordChangeForm from '#lib/components/PasswordChangeForm.svelte';
	import ProblemAlert from '#lib/components/ProblemAlert.svelte';
	import { MINIMUM_PASSWORD_LENGTH, validateAdminUserForm } from '#lib/forms/admin-user-form.ts';
	import { FormState } from '#lib/forms/form-state.svelte.ts';
	import { formatDateTime } from '#lib/format/date-time.ts';
	import { LoadDependency } from '#lib/load-dependencies.ts';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();

	let username = $state('');
	let password = $state('');
	const form = new FormState();

	async function handleCreate(event: SubmitEvent): Promise<void> {
		event.preventDefault();
		const created = await form.submit(
			() => validateAdminUserForm(username, password),
			async () => {
				await api.createAdminUser(username.trim(), password);
				await invalidate(LoadDependency.adminUsers);
			}
		);
		if (created) {
			username = '';
			password = '';
		}
	}

	async function changePassword(user: AdminUser, newPassword: string): Promise<void> {
		await api.changeAdminUserPassword(user, newPassword);
	}

	async function deleteUser(user: AdminUser): Promise<void> {
		await api.deleteAdminUser(user);
		await invalidate(LoadDependency.adminUsers);
	}
</script>

<svelte:head>
	<title>Administrateurs — Bergacast</title>
</svelte:head>

<h1>Administrateurs</h1>

<section class="card" aria-labelledby="admin-users-title">
	<h2 id="admin-users-title">Comptes existants</h2>
	<ul class="users">
		{#each data.adminUsers as user (user.id)}
			<li>
				<div class="toolbar">
					<div>
						<strong>{user.username}</strong>
						{#if user.username === data.session.username}
							<span class="badge">vous</span>
						{/if}
						<p class="muted">Créé le {formatDateTime(user.createdAt)}</p>
					</div>
					<ConfirmButton
						label="Supprimer"
						accessibleLabel="Supprimer le compte « {user.username} »"
						title="Supprimer le compte « {user.username} » ?"
						onConfirm={() => deleteUser(user)}
					>
						<p>
							Ce compte ne pourra plus se connecter. Il est impossible de supprimer son propre
							compte ou le dernier administrateur.
						</p>
					</ConfirmButton>
				</div>
				<details>
					<summary>Changer le mot de passe</summary>
					<PasswordChangeForm
						idPrefix="user-{user.id}"
						onSubmit={(newPassword) => changePassword(user, newPassword)}
					/>
				</details>
			</li>
		{/each}
	</ul>
</section>

<section class="card" aria-labelledby="new-admin-title">
	<h2 id="new-admin-title">Nouvel administrateur</h2>
	<form novalidate onsubmit={handleCreate}>
		<ProblemAlert problem={form.problem} inlineFields={['username', 'password']} />
		<div class="form-grid">
			<FormField id="new-username" label="Identifiant" required error={form.fieldErrors.username}>
				{#snippet control(attributes)}
					<input {...attributes} bind:value={username} autocomplete="off" />
				{/snippet}
			</FormField>
			<FormField
				id="new-user-password"
				label="Mot de passe"
				required
				error={form.fieldErrors.password}
				hint="{MINIMUM_PASSWORD_LENGTH} caractères minimum."
			>
				{#snippet control(attributes)}
					<input
						{...attributes}
						type="password"
						bind:value={password}
						autocomplete="new-password"
					/>
				{/snippet}
			</FormField>
		</div>
		<div class="actions">
			<button type="submit" disabled={form.pending}>
				{form.pending ? 'Création…' : 'Créer le compte'}
			</button>
		</div>
	</form>
</section>

<style>
	.users {
		margin: 0;
		padding: 0;
		list-style: none;
	}

	.users li {
		padding: var(--space-m) 0;
		border-bottom: 1px solid var(--color-border);
	}

	.users li:last-child {
		border-bottom: none;
	}

	.users .toolbar {
		margin-bottom: var(--space-s);
	}

	.users p {
		margin: 0;
	}

	summary {
		cursor: pointer;
		margin-bottom: var(--space-s);
	}
</style>
