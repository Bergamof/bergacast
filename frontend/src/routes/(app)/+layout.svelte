<script lang="ts">
	import { resolve } from '$app/paths';
	import { page } from '$app/state';
	import { api, apiDocumentationHref, logOut } from '#lib/app.ts';
	import { spaVersion } from '#lib/version.ts';
	import type { LayoutProps } from './$types';

	let { data, children }: LayoutProps = $props();

	// Not awaited by the layout load: the footer must not delay or break the page.
	const serverVersion = api.getServerVersion().catch(() => null);

	const navigationLinks = [
		{ href: resolve('/(app)/podcasts'), label: 'Podcasts' },
		{ href: resolve('/(app)/admin-users'), label: 'Administrateurs' }
	];

	function isCurrentSection(href: string): boolean {
		return page.url.pathname === href || page.url.pathname.startsWith(`${href}/`);
	}

	$effect(() => {
		// The API would reject the expired JWT anyway; logging out on time avoids losing a form.
		const timeout = setTimeout(
			() => void logOut({ expired: true }),
			data.session.expiresAt - Date.now()
		);
		return () => clearTimeout(timeout);
	});
</script>

<a class="skip-link" href="#main-content">Aller au contenu</a>
<header>
	<div class="header-content">
		<a class="brand" href={resolve('/(app)/podcasts')}>Bergacast</a>
		<nav aria-label="Navigation principale">
			<ul>
				{#each navigationLinks as link (link.href)}
					<li>
						<a href={link.href} aria-current={isCurrentSection(link.href) ? 'page' : undefined}>
							{link.label}
						</a>
					</li>
				{/each}
				<li>
					<!-- eslint-disable-next-line svelte/no-navigation-without-resolve -- served by the backend, outside the SPA -->
					<a href={apiDocumentationHref} target="_blank" rel="noopener">Documentation API</a>
				</li>
			</ul>
		</nav>
		<div class="user">
			<span class="muted">{data.session.username}</span>
			<button type="button" class="secondary" onclick={() => logOut()}>Se déconnecter</button>
		</div>
	</div>
</header>

<main id="main-content">
	{@render children()}
</main>

<footer class="muted">
	Interface {spaVersion} · Serveur
	{#await serverVersion}
		…
	{:then version}
		{version ?? 'indisponible'}
	{/await}
</footer>

<style>
	header {
		background: var(--color-surface);
		border-bottom: 1px solid var(--color-border);
	}

	.header-content,
	main,
	footer {
		max-width: var(--content-width);
		margin: 0 auto;
		padding: var(--space-m);
	}

	.header-content {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-m);
		align-items: center;
		justify-content: space-between;
	}

	.brand {
		font-size: 1.25rem;
		font-weight: 700;
		text-decoration: none;
		color: var(--color-text);
	}

	nav ul {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-m);
		margin: 0;
		padding: 0;
		list-style: none;
	}

	nav a[aria-current='page'] {
		font-weight: 700;
		text-decoration-thickness: 3px;
	}

	.user {
		display: flex;
		gap: var(--space-s);
		align-items: center;
	}

	footer {
		font-size: 0.875rem;
	}

	.skip-link {
		position: absolute;
		left: var(--space-s);
		top: -3rem;
	}

	.skip-link:focus {
		top: var(--space-s);
	}
</style>
