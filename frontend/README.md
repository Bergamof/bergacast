# Bergacast — interface d’administration

SPA SvelteKit (Svelte 5, TypeScript, `adapter-static`) servie par le backend sous `/admin/`.
Elle consomme l’API REST `/api/v1` (HAL + Problem Details, JWT).

## Prérequis

Node 24 (voir `../.nvmrc`) et npm 12.

## Commandes

```sh
npm ci            # installe les dépendances
npm run dev       # serveur de dev sur http://localhost:5173/admin (proxy /api, /v3, /swagger-ui → :8080)
npm run lint      # Prettier (vérification) + ESLint
npm run format    # formate le code avec Prettier
npm run check     # svelte-check (types)
npm run test      # tests unitaires Vitest + couverture (coverage/)
npm run build     # build statique dans build/ (copié par le Dockerfile dans le backend)
```

## Organisation

- `src/lib/api/` : client HTTP typé (jeton Bearer, parsing HAL, erreurs Problem Details) et cas d’usage de l’API.
- `src/lib/auth/` : session (JWT en `sessionStorage`) et redirections de connexion.
- `src/lib/forms/`, `src/lib/format/` : conversion et validation des formulaires, formatage des dates, durées et tailles.
- `src/lib/components/` : composants d’interface réutilisables.
- `src/routes/` : pages (`login`, puis groupe `(app)` réservé aux administrateurs connectés).
