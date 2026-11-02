<script lang="ts">
	import { invalidate } from '$app/navigation';
	import { api } from '#lib/app.ts';
	import type { AdminUser } from '#lib/api/resources.ts';
	import ConfirmButton from '#lib/components/ConfirmButton.svelte';
	import FormField from '#lib/components/FormField.svelte';
	import PasswordChangeForm from '#lib/components/PasswordChangeForm.svelte';
	import PendingButton from '#lib/components/PendingButton.svelte';
	import ProblemAlert from '#lib/components/ProblemAlert.svelte';
	import RequiredFieldsLegend from '#lib/components/RequiredFieldsLegend.svelte';
	import StatusMessage from '#lib/components/StatusMessage.svelte';
	import { MINIMUM_PASSWORD_LENGTH, validateAdminUserForm } from '#lib/forms/admin-user-form.ts';
	import { FormState } from '#lib/forms/form-state.svelte.ts';
	import { formatDateTime } from '#lib/format/date-time.ts';
	import { focusThenAnnounce } from '#lib/forms/focus.ts';
	import { LoadDependency } from '#lib/load-dependencies.ts';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();

	let username = $state('');
	let password = $state('');
	let creationMessage = $state('');
	let deletionMessage = $state('');
	let usersHeading: HTMLElement | undefined = $state();
	const form = new FormState();

	async function handleCreate(
		event: SubmitEvent & { currentTarget: EventTarget & HTMLFormElement }
	): Promise<void> {
		event.preventDefault();
		creationMessage = '';
		const newUsername = username.trim();
		const created = await form.submit(
			() => validateAdminUserForm(username, password),
			async () => {
				await api.createAdminUser(newUsername, password);
				await invalidate(LoadDependency.adminUsers);
			},
			event.currentTarget
		);
		if (created) {
			username = '';
			password = '';
			creationMessage = `Compte « ${newUsername} » créé.`;
		}
	}

	async function changePassword(user: AdminUser, newPassword: string): Promise<void> {
		await api.changeAdminUserPassword(user, newPassword);
	}

	async function deleteUser(user: AdminUser): Promise<void> {
		deletionMessage = '';
		await api.deleteAdminUser(user);
		await invalidate(LoadDependency.adminUsers);
	}
</script>

<svelte:head>
	<title>Administrateurs — Bergacast</title>
</svelte:head>

<h1>Administrateurs</h1>

<section class="card" aria-labelledby="admin-users-title">
	<!-- Receives the focus after a deletion, the button that started it being gone. -->
	<h2 id="admin-users-title" tabindex="-1" bind:this={usersHeading}>Comptes existants</h2>
	<StatusMessage message={deletionMessage} />
	<ul class="users" aria-labelledby="admin-users-title">
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
						afterConfirm={() =>
							focusThenAnnounce(
								usersHeading,
								() => (deletionMessage = `Compte « ${user.username} » supprimé.`)
							)}
					>
						<p>
							Ce compte ne pourra plus se connecter. Il est impossible de supprimer son propre
							compte ou le dernier administrateur.
						</p>
					</ConfirmButton>
				</div>
				<details>
					<summary>
						Changer le mot de passe <span class="visually-hidden">de « {user.username} »</span>
					</summary>
					<PasswordChangeForm
						idPrefix="user-{user.id}"
						username={user.username}
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
		<RequiredFieldsLegend />
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
		<ProblemAlert problem={form.problem} inlineFields={['username', 'password']} />
		<div class="actions">
			<PendingButton type="submit" pending={form.pending} pendingLabel="Création…">
				Créer le compte
			</PendingButton>
			<StatusMessage message={creationMessage} />
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
