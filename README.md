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
- **Stockage de l’audio en base** (large objects PostgreSQL), upload en streaming (aucune mise en mémoire complète,
  taille et SHA-256 calculés au fil de l’eau).
- **Flux RSS 2.0 public** par podcast (`/{slug}.xml`) avec extensions iTunes/Apple Podcasts, généré à chaque requête,
  ETag fort et cache HTTP de 5 minutes.
- **Réglages du flux** par podcast : nombre maximal d’épisodes, critère d’éviction, tri, inclusion des épisodes
  programmés dans le futur.
- **Audio public** en streaming avec requêtes `Range` (lecture et avance rapide dans les lecteurs), ETag et cache
  immuable.
- **API REST** `/api/v1` : HAL, Problem Details (RFC 9457), pagination, `ETag`/`If-Match`, JSON Merge Patch.
- **Deux types d’authentification** : jetons d’API par podcast (`bgc_…`, limités à la publication et à la lecture des
  épisodes de *ce* podcast) et JWT administrateur (15 minutes).
- **Interface d’administration** (SPA SvelteKit sous `/admin/`) : podcasts, réglages du flux, épisodes, jetons d’API,
  administrateurs.
- **Documentation OpenAPI** publique (Swagger UI) pour brancher facilement des outils externes.

## Architecture

Monorepo :

| Dossier      | Contenu                                                                                                |
|--------------|--------------------------------------------------------------------------------------------------------|
| `backend/`   | Spring Boot 4.1 (Java 25, Gradle Kotlin DSL), package `io.github.bergamof.bergacast`, découpé par fonctionnalité : `podcast`, `episode`, `audio`, `feed`, `token`, `adminuser`, `security`, `web`, `config`. Migrations Flyway dans `src/main/resources/db/migration`. |
| `frontend/`  | SPA SvelteKit 3 / Svelte 5 (TypeScript, `adapter-static`), servie sous `/admin` (voir [`frontend/README.md`](frontend/README.md)). |
| racine       | `Dockerfile`, `compose.yaml`, `.env.example`, `.sdkmanrc`, `.nvmrc`, `.spectral.yaml`, `.github/` (CI, Dependabot). |

En production, une seule application Spring Boot sert tout : l’API (`/api/v1`), les flux (`/{slug}.xml`), l’audio
(`/{slug}/episodes/{id}/audio.{ext}`), la documentation OpenAPI et la SPA (intégrée au jar par le `Dockerfile`, sous
`/admin/` ; `/` redirige vers `/admin/`). PostgreSQL 18 est la seule dépendance externe.

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
| `BERGACAST_PUBLIC_BASE_URL` | `http://localhost:8080` | URL publique de base, utilisée pour les URL absolues (flux, audio, URL de publication). |
| `BERGACAST_ADMIN_USERNAME` / `BERGACAST_ADMIN_PASSWORD` | *(vides)* | Administrateur créé au démarrage s’il n’en existe aucun (mot de passe ≥ 12 caractères). Non renseignés : simple avertissement dans les logs. |
| `BERGACAST_MAX_UPLOAD_SIZE` | `500MB` | Taille maximale d’un upload (fichier et requête multipart). |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/bergacast` | URL JDBC de PostgreSQL 18. |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | `bergacast` / `bergacast` | Identifiants PostgreSQL. |

Avec Docker Compose s’ajoutent `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` (base du conteneur `db`) et
`BERGACAST_PORT` (port publié sur l’hôte, `8080` par défaut). Modèle : [`.env.example`](.env.example).

## Lancer en local (développement)

1. **Base de données** — un PostgreSQL 18 jetable, port 5432 publié sur l’hôte (le service `db` de `compose.yaml` ne
   publie pas de port) :

   ```sh
   docker run -d --name bergacast-dev-db \
     -e POSTGRES_DB=bergacast -e POSTGRES_USER=bergacast -e POSTGRES_PASSWORD=bergacast \
     -p 5432:5432 -v bergacast-dev-db:/var/lib/postgresql postgres:18
   ```

2. **Backend** (dans `backend/`, Java 25 actif) — Flyway crée le schéma au démarrage :

   ```sh
   BERGACAST_JWT_SECRET="$(openssl rand -base64 48)" \
   BERGACAST_ADMIN_USERNAME=admin \
   BERGACAST_ADMIN_PASSWORD=change-me-please-123 \
   ./gradlew bootRun
   ```

   L’API écoute sur <http://localhost:8080> (identifiants de base par défaut : `bergacast`/`bergacast`).

3. **Frontend** (dans `frontend/`, Node 24 et npm 12.2.1 actifs) :

   ```sh
   npm ci
   npm run dev
   ```

   L’interface est servie sur <http://localhost:5173/admin> ; Vite redirige `/api`, `/v3` et `/swagger-ui` vers
   `http://localhost:8080`.

Arrêt de la base : `docker rm -f bergacast-dev-db` (ajouter `docker volume rm bergacast-dev-db` pour effacer les
données).

## Tests, lint et build

### Backend (dans `backend/`, Java 25)

| Commande | Rôle |
|----------|------|
| `./gradlew spotlessApply` | Formate le code (palantir-java-format pour Java, ktlint pour `*.gradle.kts`). |
| `./gradlew spotlessCheck checkstyleMain checkstyleTest` | Lint (formatage + Checkstyle, zéro avertissement toléré). |
| `./gradlew test` | Tests unitaires et d’intégration (Testcontainers PostgreSQL 18 : **Docker requis**) + rapport JaCoCo. |
| `./gradlew test --tests 'io.github.bergamof.bergacast.feed.FeedEpisodeSelectorTest'` | Une seule classe de test. |
| `./gradlew check` | Lint + tests. |
| `./gradlew bootJar` | Jar exécutable dans `build/libs/` (sans la SPA, ajoutée par le `Dockerfile`). |
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

L’image est construite en trois étapes : `node:24-alpine` (build de la SPA) → `eclipse-temurin:25-jdk` (`bootJar`
avec la SPA intégrée sous `static/admin/`, puis extraction en couches) → `eclipse-temurin:25-jre-alpine` (exécution en
utilisateur non root, uid 10001, healthcheck sur `/actuator/health`). Les tests ne sont pas lancés dans le build
Docker : ils tournent en CI.

```sh
cp .env.example .env
# Éditer .env : BERGACAST_JWT_SECRET (openssl rand -base64 48), BERGACAST_ADMIN_PASSWORD, POSTGRES_PASSWORD,
# BERGACAST_PUBLIC_BASE_URL (URL publique réelle), éventuellement BERGACAST_PORT.
docker compose up -d --build
```

L’application est alors disponible sur <http://localhost:8080> (ou le port `BERGACAST_PORT`), l’administration sur
`/admin/`. Les données PostgreSQL sont dans le volume `db-data`.

```sh
docker compose logs -f app   # logs de l’application
docker compose down          # arrêt (données conservées)
docker compose down -v       # arrêt et suppression des données
```

En production, placer un reverse proxy HTTPS devant l’application (les en-têtes `X-Forwarded-*` sont pris en compte)
et renseigner `BERGACAST_PUBLIC_BASE_URL` avec l’URL publique.

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

| URL | Contenu |
|-----|---------|
| `/{slug}.xml` | Flux RSS 2.0 (+ iTunes) à déclarer dans Apple Podcasts, Spotify, etc. `Cache-Control: public, max-age=300`, `ETag`/`If-None-Match`. |
| `/{slug}/episodes/{episodeId}/audio.{ext}` | Fichier audio (`GET`/`HEAD`, une seule plage `Range`, cache immuable d’un an). |

Les URL absolues (dans le flux et dans les réponses de l’API : `feedUrl`, `publishUrl`, `audio.url`) sont construites
à partir de `BERGACAST_PUBLIC_BASE_URL`.

### 4. Réglages du flux

Chaque podcast a un objet `feed`, appliqué à chaque génération du flux, dans cet ordre :

1. `includeFutureEpisodes` (défaut `false`) : sans cette option, les épisodes dont la `publicationDate` est future
   sont exclus — on peut donc publier à l’avance un épisode programmé, il apparaîtra tout seul à sa date.
2. `maxEpisodes` (entier ≥ 1, `null` = tous) : ne garde que les N épisodes les plus récents selon `evictOldestBy`
   (`PUBLICATION_DATE` ou `ADDED_DATE` = date d’upload), les plus anciens sortent du flux (ils restent en base).
3. `sortBy` (`PUBLICATION_DATE` | `ADDED_DATE`) et `sortDirection` (`DESC` | `ASC`) : ordre des items (départage par
   l’autre date, puis l’identifiant).

### Limites connues

- Un JWT administrateur reste valide jusqu’à 15 minutes après la suppression de l’administrateur ou le changement de
  son mot de passe (jetons sans état).
- Une seule plage d’octets par requête `Range` (pas de `multipart/byteranges`).
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
jour, réouverture, sortie de brouillon) et sur chaque push sur `main`. Les versions de Java et Node sont lues dans
`.sdkmanrc` et `.nvmrc`, la version de npm dans `packageManager`.

| Job | Vérification |
|-----|--------------|
| `backend-lint` | Spotless + Checkstyle |
| `backend-test` | Tests JUnit (Testcontainers PostgreSQL) + couverture JaCoCo |
| `backend-build` | `bootJar` |
| `openapi-lint` | Génération du document OpenAPI puis lint Spectral (`.spectral.yaml`, échec dès un avertissement) |
| `frontend-lint` | Prettier, ESLint, svelte-check |
| `frontend-test` | Vitest + couverture |
| `frontend-build` | Build Vite |
| `docker-build` | Build de l’image (non publiée) |

Puis le job `report` (toujours exécuté sur une PR, même en cas d’échec) publie — ou met à jour — **un commentaire sur
la PR** avec un tableau des résultats (statut et synthèse : violations, tests passés/échoués, couverture, taille du jar
ou de l’image…) et le lien du run. Dependabot propose chaque semaine les mises à jour (GitHub Actions, Gradle, npm,
Docker).

**Protection de branche recommandée sur `main`** (*Settings → Rules → Rulesets* ou *Branches*) : exiger une pull
request, exiger que la branche soit à jour, et rendre obligatoires les 8 vérifications : *Backend lint*, *Backend
tests*, *Backend build*, *OpenAPI lint*, *Frontend lint*, *Frontend tests*, *Frontend build*, *Docker build* (le job
*PR report* ne doit pas être obligatoire : il rend compte, il ne juge pas).

## Licence

Bergacast est distribué sous licence [GNU Affero General Public License v3.0](LICENSE). Si vous exposez une version
modifiée sur un réseau, vous devez en publier le code source.
