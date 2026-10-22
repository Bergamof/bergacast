# Bergacast
Service auto-hébergé de publication de podcasts : des outils automatisés publient des épisodes (audio + métadonnées) via une API authentifiée par podcast ; l’audio est stocké dans PostgreSQL (large objects) ; chaque podcast a un flux RSS public (`/{slug}.xml`) et des URL audio publiques ; une SPA d’administration gère le tout. Dépôt : github.com/Bergamof/bergacast, branche principale `main`.
## Stack
- Backend (`backend/`) : Java 25, Spring Boot 4.1.1 (WebMVC, Security + OAuth2 resource server/Nimbus, Data JPA, Validation, HATEOAS, Actuator, Flyway), springdoc-openapi 3.1.1, PostgreSQL 18, Gradle 9.8.2 (wrapper, Kotlin DSL).
- Qualité backend : JUnit 5, Testcontainers 2.0.5 (via BOM Boot, image `postgres:18`), JaCoCo 0.8.15, Spotless 8.10.4 (palantir-java-format 2.102.0 pour Java, ktlint pour `*.gradle.kts`), Checkstyle 14.3.0 (`config/checkstyle/checkstyle.xml`, `maxWarnings = 0`), javac `-Xlint:all -Werror`.
- Frontend (`frontend/`) : SvelteKit 3.1, Svelte 5.57 (runes), TypeScript 6, Vite 8, `adapter-static` (SPA, `paths.base = '/admin'`, fallback `index.html`), Vitest 4 (+ couverture v8), ESLint 10, Prettier 3, svelte-check.
- Livraison : Docker multi-étapes, Docker Compose (`db` postgres:18 + `app`), GitHub Actions, Dependabot hebdomadaire.
## Environnement
- Versions épinglées dans : `.sdkmanrc`, `.nvmrc` (+ `packageManager`/`engines` de frontend/package.json) (source de vérité, lue aussi par la CI)
- Activation (manuelle et auto) : voir section dédiée du `README.md`
- Toute montée de version d'outil se fait dans ces fichiers ET dans le `Dockerfile` (et le README si la démarche change), dans le même commit.
- Java : dernière LTS en cours par défaut ; une version différente n'est admise que si une dépendance l'impose, avec justification dans « Décisions techniques ».
- Valeurs actuelles : `java=25.0.4.1-tem`, Node `24`, `npm@12.2.1`, `engines.node ">=24 <25"`, `engine-strict=true` (`frontend/.npmrc`) ; `Dockerfile` : `NODE_MAJOR=24`, `JAVA_MAJOR=25`, `NPM_VERSION=12.2.1`.
- Docker requis en local : tests backend et `generateOpenApiSpec` (Testcontainers).
## Commandes
Backend (dans `backend/`, `JAVA_HOME` = JDK 25) :
- Formater : `./gradlew spotlessApply` — Lint : `./gradlew spotlessCheck checkstyleMain checkstyleTest`
- Tests : `./gradlew test` (Docker requis) ; un test : `./gradlew test --tests 'io.github.bergamof.bergacast.feed.FeedEpisodeSelectorTest'`
- Tout : `./gradlew check` — Jar : `./gradlew bootJar` — OpenAPI : `./gradlew generateOpenApiSpec` → `build/openapi.json` (Docker requis)
- Lancer : `BERGACAST_JWT_SECRET=$(openssl rand -base64 48) BERGACAST_ADMIN_USERNAME=admin BERGACAST_ADMIN_PASSWORD=… ./gradlew bootRun` (PostgreSQL sur `localhost:5432`, `bergacast`/`bergacast` par défaut ; autres variables : `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`, `BERGACAST_PUBLIC_BASE_URL`, `BERGACAST_MAX_UPLOAD_SIZE`).
- Base de dev : `docker run -d --name bergacast-dev-db -e POSTGRES_DB=bergacast -e POSTGRES_USER=bergacast -e POSTGRES_PASSWORD=bergacast -p 5432:5432 postgres:18` (le service `db` de compose ne publie pas de port).
Frontend (dans `frontend/`, Node 24, npm 12.2.1) :
- `npm ci`, `npm run dev` (http://localhost:5173/admin, proxy `/api`, `/v3`, `/swagger-ui` → :8080), `npm run format`, `npm run lint` (Prettier + ESLint), `npm run check` (svelte-check, warnings bloquants), `npm run test` (Vitest + couverture), `npm run build`.
Docker (racine) : `cp .env.example .env` (renseigner `BERGACAST_JWT_SECRET`, mots de passe), `docker compose up -d --build`, `BERGACAST_PORT` pour le port hôte, `docker compose down [-v]`.
## CI
- `.github/workflows/ci.yml` : sur chaque PR (création/mise à jour) → `backend-lint`, `backend-test`, `backend-build`, `openapi-lint` (Spectral 6.17.0, `.spectral.yaml`, `--fail-severity=warn`), `frontend-lint` (Prettier, ESLint, svelte-check), `frontend-test`, `frontend-build`, `docker-build` → job `report` (`if: always()`) qui commente la PR avec les résultats.
- Toute nouvelle vérification ajoutée au projet doit être ajoutée à la CI ET au commentaire de compte rendu.
- Aussi déclenché sur push `main` (sans `report`). Chaque job expose un output `summary` calculé dans une étape `if: always()` ; `report` les agrège (tableau `checks` du script) et met à jour un commentaire unique repéré par `<!-- bergacast-ci-report -->`.
- Versions lues depuis `.sdkmanrc` (`setup-java` `java-version-file`), `.nvmrc` et `packageManager`. Protection de `main` recommandée : les 8 jobs de vérification obligatoires.
## Méthode de travail
- Tâche non triviale : présenter d'abord un plan d'action (synthèse des principaux choix techniques et fonctionnels avec justification, étapes, livrables, découpage en sous-agents, risques) et attendre la validation d'Adrien avant de coder.
- Exécution : l'agent principal orchestre ; le travail est délégué à des sous-agents spécialisés à périmètre étroit (briefs autosuffisants, un propriétaire par fichier, parallèle seulement si indépendant), puis relu et vérifié. Agents du projet : `.claude/agents/` (api-rest-reviewer, test-writer, ci-fixer).
- Pas de sous-agent pour une micro-tâche.
## Définition de « done » (sauf indication contraire d'Adrien)
- Une tâche est terminée seulement quand toutes les CI déclenchées par les modifications sont vertes sur le dernier commit poussé.
- Après chaque push : surveiller les runs jusqu'à la fin ; en cas d'échec, lire les logs, corriger la cause racine, repousser, re-surveiller.
- Interdit pour obtenir le vert : skipper/désactiver un test, assouplir le lint, `continue-on-error`, supprimer un job, baisser un seuil.
- Échec non lié (flaky, infra) : relancer une fois, puis signaler. Après ~3 échecs sur le même problème : s'arrêter et exposer le diagnostic.
- CI inaccessible : annoncer « terminé localement, CI non vérifiée », jamais « done ».
- CI verte : annoncer la fin avec le lien du run, puis arrêter de surveiller la PR (pas d'attente des commentaires/reviews).
## API REST — obligatoirement RESTful
- Contrat : OpenAPI 3 généré par springdoc depuis le code (`/v3/api-docs`, `/swagger-ui.html`, publics), lintée par Spectral en CI ; versionnement : préfixe `/api/v1` ; hypermédia : HAL.
- URIs = ressources au pluriel, kebab-case, sans verbe ; opérations métier modélisées en ressources ou `PATCH`.
- Méthodes HTTP selon leur sémantique (`POST` → `201` + `Location`, `DELETE` → `204`…) ; codes de statut précis, jamais `200` pour une erreur.
- Erreurs en Problem Details (RFC 9457) ; API sans état ; liens hypermédia ; pagination/filtre/tri par query params ; `ETag`/`If-Match`.
- Toute entorse : justifiée et consignée dans « Décisions techniques ».
- Ressources : `GET /api/v1` (racine, public) ; `POST /api/v1/access-tokens` (public) ; `/api/v1/podcasts[/{slug}]` ; `/api/v1/podcasts/{slug}/api-tokens[/{id}]` ; `/api/v1/{slug}/episodes[/{id}]` (jeton du podcast ou admin pour GET/POST ; PATCH/DELETE admin) ; `/api/v1/admin-users[/{id}]`. Public hors API : `/{slug}.xml`, `GET|HEAD /{slug}/episodes/{id}/audio.{ext}`.
- Chemins centralisés dans `web/ApiPaths` (mappings, liens, règles de sécurité). PATCH = vrai JSON Merge Patch (`application/merge-patch+json`, `web/JsonMergePatch`). Validation → `422` avec `errors[{field, message}]`. `If-Match` optionnel, discordant → `412` ; `If-None-Match` → `304`.
## Architecture
- Backend package-by-feature `io.github.bergamof.bergacast` : `podcast`, `episode`, `audio` (large objects, `Range`), `feed` (`FeedEpisodeSelector` pur + `RssFeedWriter`), `token` (jetons d’API), `adminuser`, `security` (chaîne de filtres, JWT, handler Problem Details), `web` (Problem Details, ETags, liens, racine, `/error`), `config` (`BergacastProperties`, SPA, OpenAPI, `Clock`).
- Migrations Flyway : `backend/src/main/resources/db/migration` (`V1__create_schema.sql`, `ddl-auto: validate`).
- Tests : `backend/src/test/...` ; `support/IntegrationTest` + `TestcontainersConfiguration` (postgres:18) + `ApiTestClient` ; `config/OpenApiSpecExportTest` alimente `generateOpenApiSpec`.
- Frontend : `src/lib/api` (client typé, HAL, Problem Details), `src/lib/auth` (JWT en `sessionStorage`), `src/lib/forms`, `src/lib/format`, `src/lib/components`, `src/routes` (`login`, groupe `(app)` authentifié). Config SvelteKit dans `vite.config.ts`.
- Hébergement SPA : le `Dockerfile` copie `frontend/build` dans `backend/src/main/resources/static/admin/` avant `bootJar` (le build Gradle ne requiert pas Node) ; `config/AdminSpaConfiguration` sert `/admin/**` avec repli sur `index.html` ; `/` → `/admin/`.
- Image : `node:24-alpine` → `eclipse-temurin:25-jdk` (bootJar + extraction en couches) → `eclipse-temurin:25-jre-alpine`, uid 10001, healthcheck `/actuator/health`.
## Modèle de domaine
- **Podcast** : id uuid, slug (unique, immuable, `^[a-z0-9]+(-[a-z0-9]+)*$`, ≤ 64, réservés dans `podcast/Slugs`), title*, description*, author, ownerName, ownerEmail, language* (BCP-47), category, explicit, imageUrl, websiteUrl, réglages de flux (`maxEpisodes` ≥ 1 ou null, `evictOldestBy`, `sortBy` ∈ `PUBLICATION_DATE|ADDED_DATE`, `sortDirection` ∈ `DESC|ASC`, `includeFutureEpisodes`), createdAt, updatedAt, version.
- **Episode** : id, podcast, guid (unique par podcast, défaut UUID), title*, description, publicationDate (défaut maintenant), addedAt, durationSeconds, season, episodeNumber, episodeType (`FULL|TRAILER|BONUS`), explicit (nullable), audioFile (1:1), idempotencyKey (unique par podcast), version.
- **AudioFile** : id, `content_oid` (large object), mimeType, sizeBytes, sha256, fileExtension.
- **ApiToken** : id, podcast, name, tokenHash (SHA-256, unique), tokenPrefix (8 car.), createdAt, lastUsedAt ; format `bgc_` + 32 octets base64url, montré une seule fois.
- **AdminUser** : id, username unique, passwordHash (encodeur délégant), createdAt ; mot de passe ≥ 12 caractères.
- Flux : tous les épisodes → sans `includeFutureEpisodes`, `publicationDate <= now` → si `maxEpisodes`, garder les N plus récents selon `evictOldestBy` → tri `sortBy`/`sortDirection` (départage : autre date, puis id).
- Cascades : suppression podcast → épisodes et jetons (FK `ON DELETE CASCADE`) ; triggers : suppression épisode → `audio_file` → `lo_unlink`.
## Décisions techniques
- 2026-10-22 — Java 25 (LTS en cours, Temurin), Spring Boot 4.1.1, Gradle 9.8.2, Node 24 LTS, npm 12.2.1, PostgreSQL 18.
- 2026-10-22 — Audio en large objects PostgreSQL (une seule dépendance à sauvegarder) ; upload streamé par blocs de 64 Ko avec calcul taille + SHA-256 (`audio/LargeObjectStorage`), parts multipart écrites sur disque (`file-size-threshold: 0`) ; nettoyage par triggers SQL plutôt que par code applicatif (aucun orphelin, même via cascade). Téléchargement par blocs de 1 Mio, chacun lu dans une transaction courte commitée avant l’écriture au client : aucune connexion du pool tenue pendant l’envoi (`AudioStreamingIntegrationTest`).
- 2026-10-22 — Flux généré à chaque requête (pas de cache serveur) : toujours cohérent avec les réglages et les épisodes programmés qui deviennent publics à leur date ; coût faible, ETag = SHA-256 du document et `max-age=300`.
- 2026-10-22 — ETags forts : podcast `"version.episodeCount"`, épisode `"version"`, flux = SHA-256 du document, audio = sha256 du fichier.
- 2026-10-22 — Slug immuable (URL de flux et d’audio stables pour les agrégateurs) ; noms réservés pour ne pas masquer une route.
- 2026-10-22 — URL de publication `/api/v1/{slug}/episodes` (demande d’Adrien) ; ambiguïté avec `/api/v1/podcasts/...` levée en excluant `podcasts|admin-users|access-tokens` du `{slug}` de la route épisodes (`ApiPaths.EPISODES_MAPPING`) + slugs réservés.
- 2026-10-22 — Entorse REST : `POST /api/v1/access-tokens` → `200` (pas `201`) : le jeton n’est pas une ressource adressable, comme un endpoint token OAuth2.
- 2026-10-22 — Jetons d’API par podcast stockés hachés SHA-256 (aléa de 32 octets : pas besoin de bcrypt), autorité limitée aux épisodes de leur podcast.
- 2026-10-22 — JWT admin HS256 15 min via Spring Security resource server (Nimbus), secret ≥ 32 octets obligatoire (échec au démarrage). Sans état : pas de révocation (limite assumée).
- 2026-10-22 — OpenAPI et Swagger UI publics en production (demande d’Adrien, pour brancher des outils externes).
- 2026-10-22 — SPA embarquée dans le jar sous `/admin` (un seul déploiement) ; pas de CSP sur `/admin` car l’index SvelteKit contient un script inline.
- 2026-10-22 — Flux XML : caractères interdits en XML 1.0 retirés (`feed/XmlCharacters`) pour qu’une métadonnée ne casse jamais le flux. Limite de requête multipart = limite fichier + 1 Mio (`config/MultipartConfiguration`). `Idempotency-Key` concurrente : violation d’unicité rattrapée → rejeu (`episode/EpisodePublication`). Jeton Bearer lu uniquement sous `/api/` (un en-tête invalide n’affecte pas flux/audio/doc).
- 2026-10-22 — Problem Details partout, y compris erreurs de sécurité (`ProblemDetailsSecurityHandler`) et `/error` (`ProblemDetailsErrorController`) ; jamais de stack trace.
- 2026-10-22 — CI : `setup-java` lit `.sdkmanrc` `25.0.4.1-tem` comme `25.0.4` (suffisant, même ligne Temurin).
- 2026-10-22 — Limites acceptées : audio des épisodes futurs accessible si l’URL est connue ; une seule plage `Range`.
## Pièges et conventions spécifiques
- Maven Central renvoie parfois HTTP 429 : réessayer (retries configurés dans `backend/gradle.properties`).
- Testcontainers exige Docker, y compris pour `./gradlew generateOpenApiSpec` (démarre l’application).
- Partie `metadata` de l’upload : lue brute (`episode/EpisodeMetadataReader`), acceptée sans type, `text/plain`, `application/octet-stream`, `application/json` ou `*+json` (outils type n8n/Zapier) ; JSON invalide → 400, validation → 422.
- SvelteKit 3 : pas de `svelte.config.js` (config dans `vite.config.ts`) ; imports `#lib/*` (champ `imports` de package.json) avec extension explicite au lieu de `$lib` ; `resolve()` de `$app/paths` prend l’id de route avec groupe (`resolve('/(app)/podcasts')`).
- `engine-strict=true` : Node ≠ 24 ou npm ≠ 12.2.1 → `npm ci` échoue ; installer `npm install -g npm@12.2.1`.
- Spotless formate aussi les `*.gradle.kts` (ktlint) : lancer `spotlessApply` après toute modif du build.
- Checkstyle : `MagicNumber` actif en production, désactivé seulement dans les tests ; zéro avertissement toléré.
- javac `-Werror` avec `-Xlint:all` : tout warning de compilation casse le build.
- Ports : 8080 (backend), 5173 (Vite), 5432 (base de dev) ; conflit fréquent avec un autre PostgreSQL/une autre app → libérer le port ou changer `BERGACAST_PORT`/`SPRING_DATASOURCE_URL`.
- `docker compose` exige un `.env` (`POSTGRES_PASSWORD`, `BERGACAST_JWT_SECRET` en `:?`) même pour démarrer seulement `db`.
- Volume PostgreSQL 18 monté sur `/var/lib/postgresql` (et non `/var/lib/postgresql/data`).
- `/api/v1/**` non autorisé → `401` + `WWW-Authenticate: Bearer` ; jeton d’un autre podcast → `403`.
## État du projet
- 2026-10-22 : version initiale `0.1.0-SNAPSHOT` sur la branche `claude/podcast-publishing-initial-setup` : API complète, flux, audio, SPA d’administration, Docker, CI. Pas encore de release ni de déploiement.
## Licence
AGPL-3.0 — tout nouveau fichier et toute dépendance doivent rester compatibles avec elle.
## Règles de code
- Clean code systématique (nommage explicite, SRP, SOLID, DRY/KISS/YAGNI, gestion d'erreurs explicite, pas de code mort).
- Bonnes pratiques officielles de chaque techno ; rester idiomatique.
- En cas de conflit, la bonne pratique de la techno l'emporte sur la règle générique.
- Tout nouveau code est accompagné de tests.
## Git
- Conventional Commits (anglais, impératif) ; branches `feat/…`, `fix/…`, `chore/…` depuis `main`.
- Noms de branche parlants : kebab-case décrivant précisément le travail en cours (ex. `feat/user-signup-email-validation`) ; jamais `fix/bug`, `wip`, `test` ou identifiant aléatoire. Préfixe imposé par l'outil (ex. `claude/`) conservé, mais suite parlante. Renommer si le périmètre change, avant push/PR.
- Mettre à jour `CHANGELOG.md` (Keep a Changelog) pour tout changement notable.
- Avant chaque commit : lint + tests au vert.
## Maintenance de ce fichier (obligatoire)
- Lire ce fichier avant d'explorer le code ; s'y fier plutôt que tout re-parcourir.
- À la fin de chaque tâche, AVANT le commit, mettre à jour ce fichier si la tâche a changé : stack, versions d'outils ou dépendance majeure, commandes, CI, API, agents du projet, architecture ou emplacement de fichiers clés, modèle de domaine, décision technique ou fonctionnelle, piège découvert, état du projet.
- Inclure la mise à jour dans le même commit que le changement concerné.
- Rester concis (< ~200 lignes) : condenser, déplacer le détail dans `docs/`, supprimer l'obsolète.
