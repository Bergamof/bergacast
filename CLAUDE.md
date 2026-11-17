# Bergacast
Service auto-hébergé de publication de podcasts : des outils automatisés publient des épisodes (audio + métadonnées) via une API authentifiée par podcast ; métadonnées dans PostgreSQL ; audio, image de couverture (réduite à 3000 px) et flux RSS sont des objets publics d’un stockage objet S3 (Scaleway Object Storage), clé = chemin de l’URL publique (`{slug}.xml`, `{slug}/image.{ext}`, `{slug}/episodes/{id}/audio.{ext}`, `{slug}/episodes/{id}/image.{ext}`), retrouvables sans la base ; le flux est réécrit à chaque modification ; une SPA d’administration gère le tout. Production : deux images (backend natif GraalVM, frontend statique) sur Scaleway Serverless Containers. Dépôt : github.com/Bergamof/bergacast, branche principale `main`.

## Stack
- Backend (`backend/`) : Java 25, Spring Boot 4.1.2 (WebMVC, Security + OAuth2 resource server/Nimbus, Data JPA, Validation, HATEOAS, Actuator ; ni migration ni Flyway), springdoc-openapi 3.2.0 (Jackson 2 de swagger-core imposé par `jackson-bom`, voir « Décisions techniques »), PostgreSQL 18, AWS SDK for Java v2 2.59.2 (`s3` + `url-connection-client`, sans Netty/Apache client), Gradle 9.9.1 (wrapper, Kotlin DSL), GraalVM Native Build Tools 1.1.18 (`org.graalvm.buildtools.native`, image `bergacast`, `-march=compatibility`, `-H:+StaticExecutableWithDynamicLibC`).
- Migration (`migration/`) : Liquibase 4.33.0 (ligne 4.x, Apache-2.0 : la 5.x est sous FSL, incompatible avec l’AGPL ; Dependabot ignore ses majeures), pilote PostgreSQL, `spring-security-crypto` 7.1.2 + `spring-core` 7.0.10 (même encodeur de mots de passe que le backend ; versions de Boot 4.1.2), Java 25, Gradle (wrapper propre), Spotless + Checkstyle (config du backend) + JaCoCo, Testcontainers (`postgres:18`) ; JVM (pas de natif : job rare, réflexion Liquibase).
- Qualité backend : JUnit 5, Testcontainers 2.0.5 (via BOM Boot, images `postgres:18` et `adobe/s3mock:5.2.3`), JaCoCo 0.8.15, Spotless 8.11.0 (palantir-java-format 2.102.0 pour Java, ktlint pour `*.gradle.kts`), Checkstyle 14.3.0 (`config/checkstyle/checkstyle.xml`, `maxWarnings = 0`, `MagicNumber` désactivé dans les seuls tests), javac `-Xlint:all -Werror`.
- Frontend (`frontend/`) : SvelteKit 3.1, Svelte 5.57 (runes), TypeScript 6, Vite 8, `adapter-static` (SPA à la racine, fallback `index.html`, `precompress`), Vitest 5 (+ couverture v8), Playwright 1.65 + `@axe-core/playwright` 4.14 (parcours d’accessibilité, Chrome installé), ESLint 10, Prettier 3, svelte-check ; Lighthouse CI (`@lhci/cli` 0.15.1, lancé en CI par `npx`, hors dépendances : son arbre porte des CVE hautes sans correctif, il n’audite que nos pages locales).
- Sécurité (CI) : Trivy 0.76.1, gitleaks 8.30.1, CodeQL (`github/codeql-action` v4), `npm audit`.
- Livraison (images de base épinglées par empreinte `tag@sha256:…`, dans les Dockerfile, `compose.yaml` et les `services` de la CI) : `migration/Dockerfile` (`eclipse-temurin:25-jdk` + `jlink` → `gcr.io/distroless/base-debian13:nonroot`), `backend/Dockerfile` (`ghcr.io/graalvm/native-image-community:25` → `gcr.io/distroless/base-debian13:nonroot`), `frontend/Dockerfile` (`node:24-alpine` → `joseluisq/static-web-server:2.44.3-alpine`), Docker Compose (`db` + `storage` (S3Mock) + `backend` + `frontend`), GitHub Actions (CI + publication), registry Scaleway `rg.fr-par.scw.cloud/bergacast/bergacast-{backend,frontend}`, Dependabot hebdomadaire.

## Environnement
- Versions épinglées dans `.sdkmanrc`, `.nvmrc`, `packageManager`/`engines` de `frontend/package.json` (source de vérité, lue aussi par la CI) ; activation manuelle et automatique : section dédiée du `README.md`.
- Toute montée de version d'outil se fait dans ces fichiers ET dans `backend/Dockerfile` / `frontend/Dockerfile` (et le README si la démarche change), dans le même commit.
- Java : dernière LTS en cours, vérifiée à date ; autre version seulement si une dépendance l'impose, justifiée dans « Décisions techniques ».
- Valeurs actuelles : `java=25.0.5-tem`, Node `24`, `npm@12.2.1`, `engines.node ">=24 <25"`, `engine-strict=true` (`frontend/.npmrc`) ; images de base des Dockerfile : `native-image-community:25`, `eclipse-temurin:25-jdk`, `node:24-alpine`, `static-web-server:2.44.3-alpine` (tag lisible + empreinte dans le `FROM`) ; `frontend/Dockerfile` : `NPM_VERSION=12.2.1`.
- Docker requis en local pour les tests backend, migration et `generateOpenApiSpec` (Testcontainers : PostgreSQL + S3Mock).

## Commandes
Backend (dans `backend/`, `JAVA_HOME` = JDK 25) :
- Formater : `./gradlew spotlessApply` — Lint : `./gradlew spotlessCheck checkstyleMain checkstyleTest`
- Tests : `./gradlew test` ; un test : `./gradlew test --tests 'fr.bergadev.bergacast.feed.FeedEpisodeSelectorTest'`
- Tout : `./gradlew check` — Jar : `./gradlew bootJar` — OpenAPI : `./gradlew generateOpenApiSpec` → `build/openapi.json`, puis `npx --yes @stoplight/spectral-cli@6.17.0 lint build/openapi.json --ruleset ../.spectral.yaml --fail-severity=warn`
- Natif : `docker build -t bergacast-backend backend` (~5 min, ~8 Go RAM) ; `./gradlew nativeCompile` seulement avec GraalVM 25 en `JAVA_HOME`. Smoke test : `BASE_URL=… MEDIA_URL=… ADMIN_USERNAME=… ADMIN_PASSWORD=… CORS_ORIGIN=… backend/scripts/smoke-test.sh` (curl, jq ; `MEDIA_URL` = URL publique du bucket).
- Version : `-PreleaseVersion=X.Y.Z` (défaut `0.0.0-dev`, `BuildProperties` via `springBoot.buildInfo`).
- Lancer : `BERGACAST_JWT_SECRET=$(openssl rand -base64 48) BERGACAST_ADMIN_USERNAME=admin BERGACAST_ADMIN_PASSWORD=… BERGACAST_STORAGE_ENDPOINT=http://localhost:9090 BERGACAST_STORAGE_BUCKET=bergacast BERGACAST_STORAGE_ACCESS_KEY=local BERGACAST_STORAGE_SECRET_KEY=local BERGACAST_MEDIA_BASE_URL=http://localhost:9090/bergacast ./gradlew bootRun` (PostgreSQL sur `localhost:5432`, `bergacast`/`bergacast` par défaut ; autres variables : `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`, `BERGACAST_PUBLIC_BASE_URL`, `BERGACAST_STORAGE_REGION` (défaut `fr-par`), `BERGACAST_CORS_ALLOWED_ORIGINS`, `BERGACAST_MAX_UPLOAD_SIZE`, `BERGACAST_MAX_IMAGE_UPLOAD_SIZE` (défaut `20MB`), `PORT`).
- Base de dev : `docker run -d --name bergacast-dev-db -e POSTGRES_DB=bergacast -e POSTGRES_USER=bergacast -e POSTGRES_PASSWORD=bergacast -p 5432:5432 postgres:18` (le service `db` de compose ne publie pas de port) ; stockage de dev : `docker run -d --name bergacast-dev-s3 -e COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS=bergacast -p 9090:9090 adobe/s3mock:5.2.3`.
Frontend (dans `frontend/`) :
- `npm ci`, `npm run dev` (http://localhost:5173, proxy `/api`, `/v3`, `/swagger-ui` → :8080), `npm run format`, `npm run lint` (Prettier + ESLint), `npm run check` (svelte-check, warnings bloquants), `npm run test` (Vitest + couverture), `npm run build`, `npm run test:e2e` (après `build` ; parcours Playwright + axe sur l’API simulée, ports 4273/4274 ; sans Chrome : `E2E_CHROMIUM_PATH=<chromium>`).
Scripts de release (racine ; bats et shellcheck non installés, Ubuntu 24.04 comme la CI) : `docker run --rm -v "$PWD":/code:ro -w /code ubuntu:24.04 bash -c 'apt-get update -qq && apt-get install -y bats git shellcheck && shellcheck --external-sources .github/scripts/*.sh .github/scripts/*.bash .github/scripts/tests/*.bash && bats .github/scripts/tests'`.
Migration (dans `migration/`, `JAVA_HOME` = JDK 25) : `./gradlew check` (lint + tests, Docker requis) ; migrer une base : `BERGACAST_DATASOURCE_URL=jdbc:postgresql://… BERGACAST_DATASOURCE_USERNAME=… BERGACAST_DATASOURCE_PASSWORD=… ./gradlew run` (à lancer avant le backend).
Docker (racine) : `cp .env.example .env` (renseigner `BERGACAST_JWT_SECRET`, mots de passe), `docker compose up -d --build` (SPA :8081, backend :8080, bucket :9090/bergacast), `BERGACAST_PORT`/`BERGACAST_ADMIN_PORT`/`BERGACAST_MEDIA_PORT` pour les ports hôte, `docker compose down [-v]`.

## CI
- `.github/workflows/ci.yml` : sur chaque PR → `pr-title` (Conventional Commits), `release-scripts` (ShellCheck + bats), `backend-lint|test|build`, `openapi-lint` (Spectral), `frontend-lint|test|build`, `backend-dependencies`/`frontend-dependencies`/`migration-dependencies` (Trivy, `npm audit`), `secrets` (gitleaks), `codeql`, `frontend-lighthouse` (≥ 0,9 A11y et Bonnes pratiques + parcours Playwright/axe), `backend-image`, `frontend-image`, `migration-check`, `migration-image` → `report` (`if: always()`, commentaire unique de PR). Aussi sur push `main` et chaque nuit (jobs de CVE, secrets, image frontend).
- Toute nouvelle vérification : ajoutée à la CI, aux `needs` de `report` ET à son tableau `checks`, et aux vérifications obligatoires de `main`. Chaque job expose un output `summary`.
- `main` protégé : 19 vérifications obligatoires, branche à jour exigée, administrateurs inclus → merger les PR **une par une** (mettre à jour la branche, attendre la CI).
- Actions épinglées par SHA, images Docker par empreinte, Trivy/gitleaks par SHA-256 ; CodeQL : un faux positif est écarté dans GitHub, jamais exclu dans le dépôt.
- `.github/workflows/release.yml` : `workflow_dispatch` depuis `main` → CI verte exigée → version SemVer calculée (Conventional Commits) → publication des seuls modules modifiés → job de migration → déploiement backend puis frontend → tag. Détail, secrets et pièges : `docs/ci-cd.md`.

## Équipe d'agents (`.claude/agents/`)
| Agent | Rôle | Modèle | Écrit | Périmètre |
|---|---|---|---|---|
| Chef Bouillon (session principale) | développeur unique, expert de chaque techno du projet et de la CI/CD ; plan, arbitrage | celui de la session | oui | tout le dépôt |
| `gardien-du-phare` | UX & accessibilité de la SPA (Lighthouse ≥ 90 A11y & Bonnes pratiques) | `sonnet` | non | rapports |
| `turbo-escargot` | performances (requêtes, pool, stockage, natif, bundle) | `sonnet` | non | rapports |
| `cerbere-le-paranoiaque` | sécurité & CVE | `opus` | non | rapports |
| `rest-en-paix` | conformité RESTful & contrat OpenAPI | `sonnet` | non | rapports |
| `capitaine-assertion` | stratégie et qualité des tests | `sonnet` | non | rapports |
| `maniaque-du-plumeau` | clean code & architecture | `sonnet` | non | rapports |
| `madame-irma-des-versions` | versions stables/LTS & compatibilités | `haiku` | non | rapports |

## Méthode de travail
- Tâche non triviale : d'abord un plan d'action (choix techniques et fonctionnels justifiés, étapes, livrables, spécialistes mobilisés, risques), puis attendre le « go » d'Adrien avant de coder. Écart significatif en cours de route → resoumettre.
- Seul le chef écrit, sur tout le dépôt ; il travaille en expert de chaque techno du projet et de la CI/CD, en vérifiant la documentation officielle. Aucun agent d'écriture n'est créé, même pour une nouvelle techno. Pas de spécialiste pour une micro-tâche.
- Boucle : le chef développe → rapports des seuls spécialistes concernés (en parallèle, briefs autosuffisants) → le chef suit leurs recommandations et corrige → nouvelle passe ciblée, jusqu'à zéro bloquant/majeur ; mineurs non traités listés à Adrien. Recommandation contestable : le chef tranche et justifie ; enjeu significatif → Adrien.
- Réutiliser les spécialistes existants ; jamais de suppression d'agent sans accord d'Adrien. Piège appris → enrichir la définition du spécialiste dans le même commit.
- Chaque spécialiste a un `model:` explicite (jamais `inherit`/`fable`), au meilleur rapport qualité/coût ; changement d'un cran seulement sur preuve (rapport superficiel → monter, constats triviaux → descendre), consigné dans « Décisions techniques ».

## Définition de « done » (sauf indication contraire d'Adrien)
- Terminé seulement quand toutes les CI déclenchées par les modifications sont vertes sur le dernier commit poussé. Des tests locaux verts ne suffisent pas.
- Après chaque push / ouverture de PR : suivre les runs jusqu'à la fin. Échec : lire les logs, corriger la cause racine (le chef), repousser, re-surveiller.
- Interdit pour obtenir le vert, sauf accord explicite d'Adrien : skipper/désactiver un test, assouplir le lint, `continue-on-error`, supprimer un job, baisser un seuil (couverture, Lighthouse, sévérité CVE).
- Échec non lié (flaky, infra, service externe) : relancer une fois, puis signaler avec le diagnostic. Après ~3 échecs sur le même problème : s'arrêter et exposer diagnostic et pistes.
- CI inaccessible : annoncer « terminé localement, CI non vérifiée », jamais « done ».
- CI verte : annoncer la fin avec le lien du run, puis arrêter de surveiller la PR (pas d'attente des commentaires/reviews, sauf demande).

## API REST — obligatoirement RESTful
- Règles complètes, ressources et conventions du projet : `.claude/rules/api-rest.md` (à lire avant tout travail sur l'API).
- Contrat : OpenAPI 3 généré par springdoc depuis le code (`/v3/api-docs`, `/swagger-ui.html`, publics), lintée par Spectral en CI ; versionnement : préfixe `/api/v1` ; hypermédia : HAL ; chemins centralisés dans `web/ApiPaths`.
- Toute entorse : justifiée et consignée dans « Décisions techniques ».

## Architecture
- Backend package-by-feature `fr.bergadev.bergacast` : `podcast`, `episode`, `audio`, `token`, `adminuser`, `storage` (S3, clés = chemins publics), `image`, `feed` (RSS réécrit après commit), `security`, `web` (Problem Details, ETags, `ApiPaths`), `requestlog`, `config`.
- `migration/` : Liquibase, lancé comme job Scaleway avant le backend (le backend ne migre pas, ne valide pas le schéma) ; il crée aussi l'administrateur initial.
- `frontend/` : SvelteKit SPA (`src/lib` : `api`, `auth`, `forms`, `components`, `publishing`…, `src/routes`), audit Lighthouse/e2e sur API simulée (`frontend/lighthouse/`, `frontend/e2e/`).
- Tests backend : `src/test/...` en package miroir, `support/IntegrationTest` (PostgreSQL + S3Mock via Testcontainers).
- Détail des packages, du frontend, des images Docker et du modèle de domaine : `docs/architecture.md`.

## Documentation détaillée (à lire à la demande, pas avant)
| Fichier | À lire avant de… |
|---|---|
| `docs/architecture.md` | modifier le code (packages, flux, images Docker) ou le modèle de domaine |
| `docs/decisions.md` | remettre en cause un choix de stack, sécurité, stockage, release (journal daté) |
| `docs/pitfalls.md` | toucher au natif GraalVM, à la migration, aux tests d'intégration, au stockage, à l'accessibilité de la SPA, aux Dockerfile |
| `docs/ci-cd.md` | modifier la CI, les scripts `.github/scripts/` ou la release |
| `.claude/rules/api-rest.md` | tout travail sur l'API |

## État du projet
- Fait : API complète, flux RSS, SPA d'administration, images de production (natif GraalVM + static-web-server), médias et flux sur S3 (Scaleway), migrations Liquibase en job serverless, release manuelle avec déploiement, CI complète (sécurité, Lighthouse, e2e), équipe d'agents, protection de `main`.
- Audit complet du 2026-11-17 (7 spécialistes) : corrections appliquées par PR ; points d'arbitrage remontés à Adrien (mémoire du conteneur backend, textes d'erreur anglais côté SPA, EXIF des images, durcissements Scaleway, Node 26 LTS).
- Aucune issue de code ouverte.

## Licence
AGPL-3.0 — tout nouveau fichier et toute dépendance restent compatibles avec elle.

## Règles de code
- Clean code systématique : nommage explicite, fonctions courtes à responsabilité unique, SOLID, DRY/KISS/YAGNI, pas de nombres magiques, erreurs gérées explicitement, pas de code mort ni commenté, commentaires pour le « pourquoi ».
- Bonnes pratiques officielles de chaque techno ; rester idiomatique. En cas de conflit, la bonne pratique de la techno l'emporte sur la règle générique.
- Tout nouveau code est accompagné de tests.

## Git
- Conventional Commits (anglais, impératif) ; branches `feat/…`, `fix/…`, `chore/…` depuis `main`, noms parlants en kebab-case anglais (ex. `feat/user-signup-email-validation`) ; jamais `fix/bug`, `wip`, `test` ou identifiant aléatoire ; renommer si le périmètre change, avant push/PR.
- Autorisation permanente d'Adrien : quand l'outil impose une branche au nom non parlant (ex. `claude/intelligent-planck-nfgb33`), la renommer en `claude/<suite-parlante>` et pousser/ouvrir la PR sur ce nom sans demander ; idem pour une branche `claude/<suite-parlante>` dédiée à une PR séparée. Jamais pousser sur `main` ni sur une branche d'autrui.
- Titre de PR = futur message du commit squash sur `main`, source de la version (voir « Décisions techniques », releases) : Conventional Commits obligatoires.
- Avant chaque commit : lint + tests au vert.

## Maintenance de ce fichier (obligatoire)
- Lire ce fichier avant d'explorer le code ; s'y fier plutôt que tout re-parcourir.
- À la fin de chaque tâche, AVANT le commit, le mettre à jour si la tâche a changé : stack, versions, dépendance majeure, commandes, CI, API, équipe d'agents, architecture ou emplacement de fichiers clés, modèle de domaine, décision, piège découvert, état du projet. Même commit que le changement.
- Rester concis : ce fichier (< ~200 lignes, < ~20 Ko) ne garde que l'indispensable à chaque session ; le détail va dans `docs/` (voir la carte ci-dessus : architecture/domaine → `architecture.md`, nouvelle décision → entrée datée dans `decisions.md`, piège → `pitfalls.md`, CI/release → `ci-cd.md`) ou `.claude/rules/`. Supprimer l'obsolète.
