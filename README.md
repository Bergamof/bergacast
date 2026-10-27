# Bergacast

Bergacast est un service **auto-hébergé de publication de podcasts**. Des outils automatisés (scripts, chaînes de
production audio, CI…) publient un épisode — fichier audio + métadonnées — sur une API authentifiée propre à chaque
podcast ; Bergacast stocke l’audio **dans la base PostgreSQL** et sert, pour chaque podcast, un **flux RSS public**
compatible Apple Podcasts ainsi que les URL publiques des fichiers audio. Une interface d’administration web permet de
tout gérer.

Dépôt : <https://github.com/Bergamof/bergacast> — licence [AGPL-3.0](LICENSE).

## Sommaire

- [Fonctionnalités](#fonctionnalités)
- [Architecture](#architecture)
- [Prérequis](#prérequis)
- [Activation de l’auto-configuration de l’environnement](#activation-de-lauto-configuration-de-lenvironnement)
- [Configuration](#configuration)
- [Lancer en local (développement)](#lancer-en-local-développement)
- [Tests, lint et build](#tests-lint-et-build)
- [Docker](#docker)
- [Utilisation par des outils automatisés](#utilisation-par-des-outils-automatisés)
- [Documentation de l’API](#documentation-de-lapi)
- [Intégration continue](#intégration-continue)
- [Licence](#licence)

## Fonctionnalités

- **Podcasts multiples**, identifiés par un *slug* immuable (`^[a-z0-9]+(-[a-z0-9]+)*$`, 64 caractères max., hors
  noms réservés : `api`, `admin`, `swagger-ui`, `v3`, `actuator`, `podcasts`, `admin-users`, `access-tokens`,
  `assets`, `favicon.ico`).
- **Publication d’épisodes** par upload `multipart/form-data` (audio + métadonnées JSON), avec en-tête
  `Idempotency-Key` pour rejouer une requête sans créer de doublon.
- **Médias sur stockage objet** (S3, Scaleway Object Storage) : audio, images de couverture et flux sont des objets
  publics dont la clé est le chemin de l’URL (`{slug}.xml`, `{slug}/image.jpg`,
  `{slug}/episodes/{id}/audio.mp3`), retrouvables sans la base ; ni le backend ni la base ne sont sollicités par les
  applications d’écoute.
- **Flux RSS 2.0 public** par podcast avec extensions iTunes/Apple Podcasts, écrit sur le stockage à chaque
  modification (podcast, image, épisodes), cache HTTP de 5 minutes.
- **Réglages du flux** par podcast : nombre maximal d’épisodes, critère d’éviction, tri, inclusion des épisodes
  programmés dans le futur.
- **Audio public** servi par le stockage objet (requêtes `Range`, ETag, cache immuable d’un an).
- **API REST** `/api/v1` : HAL, Problem Details (RFC 9457), pagination, `ETag`/`If-Match`, JSON Merge Patch.
- **Deux types d’authentification** : jetons d’API par podcast (`bgc_…`, limités à la publication et à la lecture des
  épisodes de *ce* podcast) et JWT administrateur (15 minutes).
- **Interface d’administration** (SPA SvelteKit, image dédiée) : podcasts, réglages du flux, épisodes, jetons d’API,
  administrateurs.
- **Documentation OpenAPI** publique (Swagger UI) pour brancher facilement des outils externes.

## Architecture

Monorepo :

| Dossier      | Contenu                                                                                                |
|--------------|--------------------------------------------------------------------------------------------------------|
| `backend/`   | Spring Boot 4.1 (Java 25, Gradle Kotlin DSL), package `fr.bergadev.bergacast`, découpé par fonctionnalité : `podcast`, `episode`, `audio`, `image`, `feed`, `storage`, `token`, `adminuser`, `security`, `web`, `requestlog`, `config`. Migrations Flyway dans `src/main/resources/db/migration`. |
| `frontend/`  | SPA SvelteKit 3 / Svelte 5 (TypeScript, `adapter-static`), servie par static-web-server (voir [`frontend/README.md`](frontend/README.md)). |
| racine       | `compose.yaml`, `.env.example`, `.sdkmanrc`, `.nvmrc`, `.spectral.yaml`, `.github/` (CI, Dependabot). |

Chaque module produit sa propre image, déployée comme un conteneur indépendant (Scaleway Serverless Containers) :

- **backend** (`backend/Dockerfile`) : exécutable natif GraalVM sur distroless ; sert l’API (`/api/v1`) et la
  documentation OpenAPI, écrit les flux, l’audio et les images sur le stockage objet, et redirige (`301`) leurs
  anciennes URL (`/{slug}.xml`, `/{slug}/image.{ext}`, `/{slug}/episodes/{id}/audio.{ext}`) vers le stockage ;
- **frontend** (`frontend/Dockerfile`) : SPA statique servie par static-web-server à la racine de son domaine ; elle
  appelle l’API du backend (autre origine, CORS) dont l’URL est donnée au démarrage du conteneur.

Dépendances externes : PostgreSQL (métadonnées) et un stockage objet compatible S3 (médias et flux).

## Prérequis

| Outil | Version | Installation |
|-------|---------|--------------|
| [SDKMAN!](https://sdkman.io/) | récent | <https://sdkman.io/install> — fournit Java 25 (Temurin `25.0.4.1-tem`, épinglé dans `.sdkmanrc`) |
| [nvm](https://github.com/nvm-sh/nvm) | récent | <https://github.com/nvm-sh/nvm#installing-and-updating> — fournit Node 24 (épinglé dans `.nvmrc`) |
| npm | 12.2.1 | épinglé par `packageManager` dans `frontend/package.json` (voir ci-dessous) |
| [Docker](https://www.docker.com/) avec Docker Compose v2 | récent | <https://docs.docker.com/get-started/get-docker/> — requis pour la base locale, les tests backend (Testcontainers) et l’image |

Gradle n’a pas besoin d’être installé : le wrapper `backend/gradlew` (Gradle 9.8.2) est fourni.

## Activation de l’auto-configuration de l’environnement

Les versions des outils sont épinglées dans des fichiers versionnés, lus aussi par la CI : `.sdkmanrc` (Java) et
`.nvmrc` (Node), plus `packageManager`/`engines` dans `frontend/package.json` (npm, Node). Seuls SDKMAN! et nvm sont
pris en charge.

### Java avec SDKMAN!

Depuis la racine du dépôt :

```sh
sdk env install   # installe la version de .sdkmanrc (25.0.4.1-tem) si elle manque
sdk env           # active cette version dans le shell courant
```

Activation automatique à chaque `cd` dans le projet : dans `~/.sdkman/etc/config`, mettre

```properties
sdkman_auto_env=true
```

puis ouvrir un nouveau terminal. SDKMAN! active alors la version de `.sdkmanrc` en entrant dans le dossier et revient
à la version par défaut en le quittant.

Vérification :

```sh
java -version   # doit afficher : openjdk version "25.0.4" … (Temurin)
```

### Node avec nvm

Depuis la racine du dépôt :

```sh
nvm install   # installe la version de .nvmrc (24) si elle manque
nvm use       # active cette version dans le shell courant
```

Activation automatique à chaque `cd` : ajouter le hook officiel de nvm **après** les lignes d’initialisation de nvm.

**zsh** (`~/.zshrc`) :

```zsh
# place this after nvm initialization!
autoload -U add-zsh-hook

load-nvmrc() {
  local nvmrc_path
  nvmrc_path="$(nvm_find_nvmrc)"

  if [ -n "$nvmrc_path" ]; then
    local nvmrc_node_version
    nvmrc_node_version=$(nvm version "$(cat "${nvmrc_path}")")

    if [ "$nvmrc_node_version" = "N/A" ]; then
      nvm install
    elif [ "$nvmrc_node_version" != "$(nvm version)" ]; then
      nvm use
    fi
  elif [ -n "$(PWD=$OLDPWD nvm_find_nvmrc)" ] && [ "$(nvm version)" != "$(nvm version default)" ]; then
    echo "Reverting to nvm default version"
    nvm use default
  fi
}

add-zsh-hook chpwd load-nvmrc
load-nvmrc
```

**bash** (`~/.bashrc`) :

```bash
cdnvm() {
    command cd "$@" || return $?
    nvm_path="$(nvm_find_up .nvmrc | command tr -d '\n')"

    # If there are no .nvmrc file, use the default nvm version
    if [[ ! $nvm_path = *[^[:space:]]* ]]; then

        declare default_version
        default_version="$(nvm version default)"

        # If there is no default version, set it to `node`
        # This will use the latest version on your machine
        if [ $default_version = 'N/A' ]; then
            nvm alias default node
            default_version=$(nvm version default)
        fi

        # If the current version is not the default version, set it to use the default version
        if [ "$(nvm current)" != "${default_version}" ]; then
            nvm use default
        fi
    elif [[ -s "${nvm_path}/.nvmrc" && -r "${nvm_path}/.nvmrc" ]]; then
        declare nvm_version
        nvm_version=$(<"${nvm_path}"/.nvmrc)

        declare locally_resolved_nvm_version
        # `nvm ls` will check all locally-available versions
        # If there are multiple matching versions, take the latest one
        # Remove the `->` and `*` characters and spaces
        # `locally_resolved_nvm_version` will be `N/A` if no local versions are found
        locally_resolved_nvm_version=$(nvm ls --no-colors "${nvm_version}" | command tail -1 | command tr -d '\->*' | command tr -d '[:space:]')

        # If it is not already installed, install it
        # `nvm install` will implicitly use the newly-installed version
        if [ "${locally_resolved_nvm_version}" = 'N/A' ]; then
            nvm install "${nvm_version}";
        elif [ "$(nvm current)" != "${locally_resolved_nvm_version}" ]; then
            nvm use "${nvm_version}";
        fi
    fi
}

alias cd='cdnvm'
cdnvm "$PWD" || exit
```

Vérification :

```sh
node -v   # doit afficher v24.x
```

### npm 12.2.1

`frontend/package.json` déclare `"packageManager": "npm@12.2.1"` et `frontend/.npmrc` active `engine-strict=true` :
une autre version de Node que 24 fait échouer l’installation. Après avoir activé Node 24 :

```sh
npm install -g npm@12.2.1
npm -v   # 12.2.1
```

Alternative avec Corepack (fourni avec Node 24), qui respecte le champ `packageManager` : `corepack enable npm`.

## Configuration

L’application se configure par variables d’environnement (liées aux propriétés `spring.*` et `bergacast.*` de
`backend/src/main/resources/application.yaml`) :

| Variable | Défaut | Rôle |
|----------|--------|------|
| `BERGACAST_JWT_SECRET` | *(aucun, obligatoire)* | Secret HMAC des JWT administrateur, **au moins 32 octets** ; l’application refuse de démarrer sinon. Générer avec `openssl rand -base64 48`. |
| `BERGACAST_PUBLIC_BASE_URL` | `http://localhost:8080` | URL publique du backend, utilisée pour les URL absolues de l’API (URL de publication). |
| `BERGACAST_MEDIA_BASE_URL` | *(aucun, obligatoire)* | URL publique du bucket (ou d’un CDN devant lui), base des URL des flux, de l’audio et des images, par ex. `https://s3.fr-par.scw.cloud/mon-bucket`. Doit différer de `BERGACAST_PUBLIC_BASE_URL`. |
| `BERGACAST_STORAGE_ENDPOINT` | `https://s3.fr-par.scw.cloud` | Point d’accès de l’API S3 (adresses en *path-style*). |
| `BERGACAST_STORAGE_REGION` | `fr-par` | Région du bucket. |
| `BERGACAST_STORAGE_BUCKET` | *(aucun, obligatoire)* | Nom du bucket. |
| `BERGACAST_STORAGE_ACCESS_KEY` / `BERGACAST_STORAGE_SECRET_KEY` | *(aucun, obligatoires)* | Clé d’API autorisée à écrire et supprimer dans le bucket. |
| `BERGACAST_CORS_ALLOWED_ORIGINS` | *(vide : aucune)* | Origines (séparées par des virgules) autorisées à appeler l’API depuis un navigateur : l’URL publique de la SPA d’administration, par ex. `https://admin.example.com`. |
| `PORT` | `8080` | Port d’écoute (fourni par la plateforme serverless). |
| `BERGACAST_ADMIN_USERNAME` / `BERGACAST_ADMIN_PASSWORD` | *(vides)* | Administrateur créé au démarrage s’il n’en existe aucun (mot de passe ≥ 12 caractères). Non renseignés : simple avertissement dans les logs. |
| `BERGACAST_MAX_UPLOAD_SIZE` | `500MB` | Taille maximale d’un upload (fichier et requête multipart). |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/bergacast` | URL JDBC de PostgreSQL 18. |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | `bergacast` / `bergacast` | Identifiants PostgreSQL. |

L’image frontend ne prend que `BERGACAST_API_URL` (obligatoire : origine publique du backend, par ex.
`https://api.example.com`, écrite au démarrage dans `/config.js`) et `PORT` (`8080` par défaut).

Avec Docker Compose s’ajoutent `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` (base du conteneur `db`),
`BERGACAST_ADMIN_URL` (URL de la SPA, autorisée en CORS), `BERGACAST_PORT`, `BERGACAST_ADMIN_PORT` et
`BERGACAST_MEDIA_PORT` (ports publiés sur l’hôte, `8080`, `8081` et `9090` par défaut) ; le stockage objet y est un
S3Mock local (bucket `bergacast`, sans authentification). Modèle : [`.env.example`](.env.example).

## Lancer en local (développement)

1. **Base de données** — un PostgreSQL 18 jetable, port 5432 publié sur l’hôte (le service `db` de `compose.yaml` ne
   publie pas de port) :

   ```sh
   docker run -d --name bergacast-dev-db \
     -e POSTGRES_DB=bergacast -e POSTGRES_USER=bergacast -e POSTGRES_PASSWORD=bergacast \
     -p 5432:5432 -v bergacast-dev-db:/var/lib/postgresql postgres:18
   ```

2. **Stockage objet** — un S3Mock jetable (compatible S3, sans authentification), bucket `bergacast` lisible sur
   <http://localhost:9090/bergacast> :

   ```sh
   docker run -d --name bergacast-dev-s3 -e COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS=bergacast \
     -p 9090:9090 adobe/s3mock:5.2.3
   ```

3. **Backend** (dans `backend/`, Java 25 actif) — Flyway crée le schéma au démarrage :

   ```sh
   BERGACAST_JWT_SECRET="$(openssl rand -base64 48)" \
   BERGACAST_ADMIN_USERNAME=admin \
   BERGACAST_ADMIN_PASSWORD=change-me-please-123 \
   BERGACAST_STORAGE_ENDPOINT=http://localhost:9090 BERGACAST_STORAGE_BUCKET=bergacast \
   BERGACAST_STORAGE_ACCESS_KEY=local BERGACAST_STORAGE_SECRET_KEY=local \
   BERGACAST_MEDIA_BASE_URL=http://localhost:9090/bergacast \
   ./gradlew bootRun
   ```

   L’API écoute sur <http://localhost:8080> (identifiants de base par défaut : `bergacast`/`bergacast`).

4. **Frontend** (dans `frontend/`, Node 24 et npm 12.2.1 actifs) :

   ```sh
   npm ci
   npm run dev
   ```

   L’interface est servie sur <http://localhost:5173> ; Vite redirige `/api`, `/v3` et `/swagger-ui` vers
   `http://localhost:8080`.

Arrêt : `docker rm -f bergacast-dev-db bergacast-dev-s3` (ajouter `docker volume rm bergacast-dev-db` pour effacer
les données de la base).

## Tests, lint et build

### Backend (dans `backend/`, Java 25)

| Commande | Rôle |
|----------|------|
| `./gradlew spotlessApply` | Formate le code (palantir-java-format pour Java, ktlint pour `*.gradle.kts`). |
| `./gradlew spotlessCheck checkstyleMain checkstyleTest` | Lint (formatage + Checkstyle, zéro avertissement toléré). |
| `./gradlew test` | Tests unitaires et d’intégration (Testcontainers PostgreSQL 18 : **Docker requis**) + rapport JaCoCo. |
| `./gradlew test --tests 'fr.bergadev.bergacast.feed.FeedEpisodeSelectorTest'` | Une seule classe de test. |
| `./gradlew check` | Lint + tests. |
| `./gradlew bootJar` | Jar exécutable (JVM) dans `build/libs/`. |
| `./gradlew nativeCompile` | Exécutable natif `build/native/nativeCompile/bergacast` (**GraalVM 25 requis** comme `JAVA_HOME`, ~8 Go de RAM, plusieurs minutes ; sinon passer par l’image Docker). |
| `scripts/smoke-test.sh` | Test de bout en bout d’un backend lancé (`BASE_URL`, `ADMIN_USERNAME`, `ADMIN_PASSWORD`, `CORS_ORIGIN` ; requiert curl et jq), utilisé en CI sur l’image native. |
| `./gradlew generateOpenApiSpec` | Écrit le document OpenAPI dans `build/openapi.json` (démarre l’application sur Testcontainers : **Docker requis**). |

Rapports : `build/reports/tests/test/`, `build/reports/jacoco/test/`, `build/reports/checkstyle/`.

### Frontend (dans `frontend/`, Node 24, npm 12.2.1)

| Commande | Rôle |
|----------|------|
| `npm ci` | Installe les dépendances (versions de `package-lock.json`). |
| `npm run dev` | Serveur de développement. |
| `npm run format` | Formate avec Prettier. |
| `npm run lint` | Prettier (vérification) + ESLint. |
| `npm run check` | svelte-check (types, avertissements bloquants). |
| `npm run test` | Vitest + couverture (`coverage/`). |
| `npm run build` | Build statique dans `build/`. |

## Docker

Deux images, une par module, construites chacune depuis son dossier (contexte `backend/` ou `frontend/`) ; les tests
ne sont pas lancés dans les builds Docker : ils tournent en CI.

| Image | Étapes | Taille / mémoire |
|-------|--------|------------------|
| `bergacast-backend` | `ghcr.io/graalvm/native-image-community:25` (AOT Spring + `nativeCompile`) → `gcr.io/distroless/base-debian13:nonroot` (glibc seule, pas de shell, utilisateur `nonroot`) | ~ 190 Mo, ~ 110–120 Mo de RAM (tient dans 256 Mo, upload de 200 Mo compris), démarrage ~ 1 s |
| `bergacast-frontend` | `node:24-alpine` (build statique, précompressé brotli/gzip) → `joseluisq/static-web-server:2.44.1-alpine` (utilisateur `sws`) | ~ 18 Mo, ~ 10 Mo de RAM |

```sh
cp .env.example .env
# Éditer .env : BERGACAST_JWT_SECRET (openssl rand -base64 48), BERGACAST_ADMIN_PASSWORD, POSTGRES_PASSWORD,
# éventuellement BERGACAST_PORT / BERGACAST_ADMIN_PORT et les URL publiques.
docker compose up -d --build
```

L’administration est alors sur <http://localhost:8081>, l’API, les flux et l’audio sur <http://localhost:8080>. Les
données PostgreSQL sont dans le volume `db-data`. La compilation native du backend prend plusieurs minutes.

```sh
docker compose logs -f backend   # logs du backend
docker compose down              # arrêt (données conservées)
docker compose down -v           # arrêt et suppression des données
```

## Production (Scaleway)

### Versions et publication

Chaque merge sur `main` dont la CI est verte et qui modifie `backend/` ou `frontend/` produit une version, sans
intervention ([`.github/workflows/release.yml`](.github/workflows/release.yml)) :

1. **Plan** ([`.github/scripts/release-plan.sh`](.github/scripts/release-plan.sh)) : modules modifiés depuis le
   dernier tag `vX.Y.Z` (aucun → rien n’est publié) et prochaine version, déduite des
   [Conventional Commits](https://www.conventionalcommits.org/fr/) qui touchent ces modules : `type!:` ou pied
   `BREAKING CHANGE:` → majeure, `feat` → mineure, tout autre type → patch. Première version : `1.0.0`.
2. **Publication** de l’image de chaque module modifié, construite avec cette version (argument Docker `VERSION`),
   dans le Container Registry Scaleway, namespace `bergacast` (région `fr-par`), pour `linux/amd64` (seule
   architecture des Serverless Containers), avec les tags `X.Y.Z`, `X.Y`, `X`, `latest` et `sha-<sha court>` :
   - `rg.fr-par.scw.cloud/bergacast/bergacast-backend`
   - `rg.fr-par.scw.cloud/bergacast/bergacast-frontend`
3. **Tag `vX.Y.Z`** sur le commit et **release GitHub** (images publiées, changements groupés par type) : les releases
   GitHub tiennent lieu de journal des modifications.

Une seule version pour le dépôt, appliquée aux seuls modules modifiés : un module inchangé garde sa version
précédente. Les versions sont visibles dans le pied de page de la SPA (interface et serveur), à la racine de l’API
(`GET /api/v1` → `version`) et dans `info.version` de l’OpenAPI. Dans le dépôt, `build.gradle.kts` et `package.json`
restent en `0.0.0-dev` : la version n’existe que dans les tags Git et les images.

Le titre de chaque PR doit donc suivre les Conventional Commits (job `pr-title`) : avec le merge squash (seul mode
autorisé, titre du commit = titre de la PR), il devient le message du commit sur `main`. Dependabot préfixe ses PR
par `build(deps)`, `build(deps-dev)` ou `ci(deps)`.

Configuration GitHub requise : secret `SCW_SECRET_KEY` (clé d’API IAM ayant le droit d’écrire dans le registry). En
cas d’échec, relancer le run : la version est recalculée à l’identique tant que le tag n’est pas posé.

### Déploiement (conteneurs serverless)

Configurer chaque conteneur sur un tag `X.Y.Z` (déploiement explicite, retour arrière possible) ou sur `latest`
(dernière version du module, prise en compte au prochain redéploiement).


- **backend** : port `8080` (la plateforme fournit `PORT`), variables de la section [Configuration](#configuration) ;
  `BERGACAST_PUBLIC_BASE_URL` = URL publique du conteneur backend, `BERGACAST_CORS_ALLOWED_ORIGINS` = URL publique du
  conteneur frontend, `SPRING_DATASOURCE_*` = base PostgreSQL, `BERGACAST_STORAGE_*` et `BERGACAST_MEDIA_BASE_URL` =
  bucket (voir ci-dessous) ; secrets (`BERGACAST_JWT_SECRET`, `BERGACAST_STORAGE_SECRET_KEY`, mots de passe) en
  variables secrètes. Sonde de santé : `/actuator/health/liveness` (n’interroge pas la base). Le pool de
  connexions ne garde aucune connexion inactive (fermée après 30 s) : une base serverless peut se mettre en pause.
  **Une seule instance** (`max-scale` = 1) : l’écriture des flux est sérialisée dans le processus.
- **frontend** : `BERGACAST_API_URL` = URL publique du backend ; 128 Mo de mémoire suffisent. Sonde : `/health`.

### Stockage objet (Scaleway Object Storage)

1. Créer un bucket (région `fr-par`) et une application IAM avec une clé d’API ayant le droit d’écrire dans Object
   Storage (`ObjectStorageFullAccess`, idéalement limitée au projet) : `BERGACAST_STORAGE_ACCESS_KEY` /
   `BERGACAST_STORAGE_SECRET_KEY`.
2. Rendre les objets lisibles par tous, par une *bucket policy*. Chez Scaleway, une policy ne laisse passer **que**
   les principaux qu’elle liste : y inclure l’application du backend (et son propre utilisateur, pour garder la main
   depuis la console) :

   ```json
   {
     "Version": "2023-04-17",
     "Id": "bergacast",
     "Statement": [
       {"Sid": "PublicRead", "Effect": "Allow", "Principal": "*", "Action": ["s3:GetObject"],
        "Resource": ["MON-BUCKET/*"]},
       {"Sid": "Backend", "Effect": "Allow", "Principal": {"SCW": "application_id:ID-DE-L-APPLICATION"},
        "Action": ["s3:*"], "Resource": ["MON-BUCKET", "MON-BUCKET/*"]}
     ]
   }
   ```

3. `BERGACAST_MEDIA_BASE_URL` = `https://s3.fr-par.scw.cloud/MON-BUCKET`, ou l’URL d’un domaine dédié : domaine
   Edge Services (CDN) placé devant le bucket, ou `https://MON-BUCKET.s3.fr-par.scw.cloud`. Les robots ne lisent
   `robots.txt` qu’à la racine d’un domaine : avec un domaine dédié, y déposer une fois un `robots.txt`
   (`User-agent: *` / `Disallow: /`, type `text/plain`) ; avec l’URL path-style, la racine appartient à Scaleway et
   aucun `robots.txt` n’est possible (les flux portent de toute façon `itunes:block`/`googleplay:block`, et l’audio
   n’est lié que depuis eux).

### Migration des médias stockés en base

Les versions précédentes gardaient l’audio (large objects) et les images (`bytea`) dans PostgreSQL. La migration
`V3` conserve ce contenu ; une fois la nouvelle version déployée, le script
[`backend/scripts/export-media-to-object-storage.sh`](backend/scripts/export-media-to-object-storage.sh) le copie
dans le bucket (contrôle du SHA-256 de chaque fichier), puis fait réécrire chaque flux par le backend. Il se relance
sans risque. Depuis un poste ayant `psql`, `curl` et `jq` :

```sh
DATABASE_URL='postgresql://UTILISATEUR:MOT-DE-PASSE@HOTE:5432/BASE?sslmode=require' \
BERGACAST_STORAGE_ENDPOINT=https://s3.fr-par.scw.cloud BERGACAST_STORAGE_REGION=fr-par \
BERGACAST_STORAGE_BUCKET=MON-BUCKET BERGACAST_STORAGE_ACCESS_KEY=… BERGACAST_STORAGE_SECRET_KEY=… \
BASE_URL=https://api.example.com ADMIN_USERNAME=admin ADMIN_PASSWORD=… \
backend/scripts/export-media-to-object-storage.sh
```

Pour une base Serverless SQL, l’URI de connexion est donnée dans la console (onglet *Connect*). Une fois les flux et
l’audio vérifiés, une prochaine version supprimera l’ancien contenu de la base.

## Utilisation par des outils automatisés

### 1. Obtenir un jeton d’API

Dans l’administration : *Podcasts* → podcast → *Jetons d’API* → créer un jeton. Le jeton (`bgc_…`) n’est affiché **qu’une
seule fois** ; seule son empreinte SHA-256 est conservée. Il n’autorise que la publication et la lecture des épisodes de
ce podcast.

### 2. Publier un épisode

`POST /api/v1/{slug}/episodes` en `multipart/form-data` avec deux parties :

- `metadata` (JSON ; type de partie `application/json`, `text/plain`, `application/octet-stream` ou absent) : `title` (requis), `description`, `publicationDate`
  (ISO 8601 avec fuseau, défaut : maintenant), `guid` (défaut : UUID aléatoire, unique par podcast), `durationSeconds`,
  `season`, `episodeNumber`, `episodeType` (`FULL` par défaut, `TRAILER`, `BONUS`), `explicit` ;
- `audio` : le fichier, de type `audio/*` (sinon `415`).

```sh
curl -X POST 'https://podcasts.example.com/api/v1/mon-podcast/episodes' \
  -H 'Authorization: Bearer bgc_VOTRE_JETON' \
  -H 'Idempotency-Key: episode-42' \
  -F 'metadata={"title":"Épisode 42","description":"Description de l’épisode","publicationDate":"2026-11-01T06:00:00Z","durationSeconds":1800};type=application/json' \
  -F 'audio=@episode-42.mp3;type=audio/mpeg'
```

Réponses : `201 Created` + `Location` + représentation HAL de l’épisode ; avec un `Idempotency-Key` déjà utilisé pour
ce podcast, le même épisode est renvoyé (`201`, même `Location`, aucune création) — idéal pour réessayer sans risque ;
`409` si le `guid` existe déjà ; `413` si le fichier dépasse `BERGACAST_MAX_UPLOAD_SIZE` ; `422` si les métadonnées
sont invalides (Problem Details avec un tableau `errors[{field, message}]`) ; `401`/`403` pour un jeton absent, invalide
ou d’un autre podcast. L’administration affiche la commande prête à copier pour chaque podcast.

Lister les épisodes : `GET /api/v1/{slug}/episodes?page=0&size=20&sort=publicationDate,desc` (même jeton).

### 3. URL publiques

Sur le stockage objet, relatives à `BERGACAST_MEDIA_BASE_URL` (la clé de l’objet est le chemin de l’URL) :

| URL | Contenu |
|-----|---------|
| `/{slug}.xml` | Flux RSS 2.0 (+ iTunes) à ajouter par son URL dans une application d’écoute ; il demande à Apple et Google de ne pas référencer le podcast dans leurs annuaires (`itunes:block`, `googleplay:block`). `Cache-Control: public, max-age=300`. |
| `/{slug}/image.{jpg\|png}` | Image de couverture envoyée (`Cache-Control: public, max-age=300`). |
| `/{slug}/episodes/{episodeId}/audio.{ext}` | Fichier audio (cache immuable d’un an). |

Sur le backend (`BERGACAST_PUBLIC_BASE_URL`) : `/robots.txt` (fichier statique, `Disallow: /`, toutes les réponses portent
`X-Robots-Tag: noindex, nofollow`, la SPA aussi) ; les anciennes URL des flux, images et audios sont redirigées
(`301`) vers le même chemin sur le stockage, sans interroger la base.

Le flux est écrit sur le stockage à chaque modification du podcast, de son image ou de ses épisodes. Un épisode
programmé dans le futur n’y apparaît qu’à l’écriture suivante après sa date : `POST
/api/v1/podcasts/{slug}/feed-publications` (administrateur, bouton de l’administration) réécrit le flux à la demande,
comme après un échec d’écriture (`503`).

Les URL absolues des réponses de l’API (`feedUrl`, `audio.url`, `image.url`) pointent vers le stockage ;
`publishUrl` vers le backend.

### 4. Réglages du flux

Chaque podcast a un objet `feed`, appliqué à chaque génération du flux, dans cet ordre :

1. `includeFutureEpisodes` (défaut `false`) : sans cette option, les épisodes dont la `publicationDate` est future
   au moment où le flux est écrit sont exclus ; un épisode programmé apparaît à la prochaine écriture du flux après
   sa date (nouvelle publication, modification, ou réécriture à la demande).
2. `maxEpisodes` (entier ≥ 1, `null` = tous) : ne garde que les N épisodes les plus récents selon `evictOldestBy`
   (`PUBLICATION_DATE` ou `ADDED_DATE` = date d’upload), les plus anciens sortent du flux (ils restent en base).
3. `sortBy` (`PUBLICATION_DATE` | `ADDED_DATE`) et `sortDirection` (`DESC` | `ASC`) : ordre des items (départage par
   l’autre date, puis l’identifiant).

### Limites connues

- Un JWT administrateur reste valide jusqu’à 15 minutes après la suppression de l’administrateur ou le changement de
  son mot de passe (jetons sans état).
- Le flux est un instantané écrit à chaque modification : un épisode programmé n’apparaît pas tout seul à sa date.
- L’audio d’un épisode programmé dans le futur est accessible à qui connaît son URL.

## Documentation de l’API

- Swagger UI : `/swagger-ui.html`
- Document OpenAPI (JSON) : `/v3/api-docs`
- Point d’entrée hypermédia : `GET /api/v1` (liens vers `podcasts`, `adminUsers`, `accessTokens`)

Ces pages sont **publiques, y compris en production**, volontairement, pour faciliter l’intégration d’outils externes.

Authentification administrateur : `POST /api/v1/access-tokens` avec `{"username": "…", "password": "…"}` renvoie
`200 {accessToken, tokenType: "Bearer", expiresIn: 900}`, à passer dans `Authorization: Bearer …`.

## Intégration continue

Le workflow [`.github/workflows/ci.yml`](.github/workflows/ci.yml) s’exécute sur chaque pull request (création, mise à
jour, réouverture, sortie de brouillon, modification du titre ou de la description) et sur chaque push sur `main`. Les versions de Java et Node sont lues dans
`.sdkmanrc` et `.nvmrc`, la version de npm dans `packageManager`.

| Job | Vérification |
|-----|--------------|
| `pr-title` | Titre de la PR au format Conventional Commits (`.github/scripts/check-pr-title.sh`, PR uniquement) |
| `release-scripts` | ShellCheck et tests bats des scripts de release (`.github/scripts/tests`) |
| `backend-lint` | Spotless + Checkstyle |
| `backend-test` | Tests JUnit (Testcontainers PostgreSQL) + couverture JaCoCo |
| `backend-build` | `bootJar` |
| `openapi-lint` | Génération du document OpenAPI puis lint Spectral (`.spectral.yaml`, échec dès un avertissement) |
| `frontend-lint` | Prettier, ESLint, svelte-check |
| `frontend-test` | Vitest + couverture |
| `frontend-build` | Build Vite |
| `backend-image` | Build de l’image native (non publiée), démarrage contre PostgreSQL puis `backend/scripts/smoke-test.sh` |
| `frontend-image` | Build de l’image (non publiée), vérification du service (`config.js`, repli SPA, cache, refus d’une URL d’API invalide) |

Puis le job `report` (toujours exécuté sur une PR, même en cas d’échec) publie — ou met à jour — **un commentaire sur
la PR** avec un tableau des résultats (statut et synthèse : violations, tests passés/échoués, couverture, taille du jar
ou des images, mémoire…) et le lien du run. Dependabot propose chaque semaine les mises à jour (GitHub Actions, Gradle, npm,
Docker).

**Protection de branche recommandée sur `main`** (*Settings → Rules → Rulesets* ou *Branches*) : exiger une pull
request, exiger que la branche soit à jour, et rendre obligatoires les 11 vérifications : *PR title*, *Release scripts*, *Backend lint*, *Backend
tests*, *Backend build*, *OpenAPI lint*, *Frontend lint*, *Frontend tests*, *Frontend build*, *Backend native image*, *Frontend image* (le job
*PR report* ne doit pas être obligatoire : il rend compte, il ne juge pas).

## Licence

Bergacast est distribué sous licence [GNU Affero General Public License v3.0](LICENSE). Si vous exposez une version
modifiée sur un réseau, vous devez en publier le code source.
