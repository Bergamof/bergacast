# Bergacast — interface d’administration

SPA SvelteKit (Svelte 5, TypeScript, `adapter-static`) servie à la racine de son propre domaine par
[static-web-server](https://static-web-server.net) (image `Dockerfile`). Elle consomme l’API REST `/api/v1` du backend
(HAL + Problem Details, JWT), éventuellement sur une autre origine (CORS).

## Prérequis

Node 24 (voir `../.nvmrc`) et npm 12.

## Commandes

```sh
npm ci            # installe les dépendances
npm run dev       # serveur de dev sur http://localhost:5173 (proxy /api, /v3, /swagger-ui → :8080)
npm run lint      # Prettier (vérification) + ESLint
npm run format    # formate le code avec Prettier
npm run check     # svelte-check (types)
npm run test      # tests unitaires Vitest + couverture (coverage/)
npm run build     # build statique (précompressé) dans build/
```

## Organisation

- `src/lib/api/` : client HTTP typé (jeton Bearer, parsing HAL, erreurs Problem Details) et cas d’usage de l’API.
- `src/lib/auth/` : session (JWT en `sessionStorage`) et redirections de connexion.
- `src/lib/forms/`, `src/lib/format/` : conversion et validation des formulaires, formatage des dates, durées et tailles.
- `src/lib/components/` : composants d’interface réutilisables.
- `src/routes/` : pages (`login`, puis groupe `(app)` réservé aux administrateurs connectés).

## Configuration à l’exécution

`/config.js`, chargé par `src/app.html` avant l’application, définit `globalThis.bergacastConfig.apiUrl` : l’URL du
backend (`src/lib/runtime-config.ts`). En développement (`static/config.js`), elle est vide : l’API est sur la même
origine grâce au proxy Vite. Dans l’image, `docker/docker-entrypoint.sh` réécrit ce fichier au démarrage à partir de
`BERGACAST_API_URL` (obligatoire, origine http(s) seulement) : une seule image sert tous les environnements. Réglages
du serveur (cache, en-têtes de sécurité, fichiers précompressés) : `docker/static-web-server.toml`.
