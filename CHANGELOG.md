# Changelog

All notable changes up to the first release are documented in this file. From `v1.0.0` on, each release is
described by its [GitHub release](https://github.com/Bergamof/bergacast/releases), generated from the Conventional
Commits of the merged pull requests.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed

- Frontend tests run on Vitest 5 (`vitest` and `@vitest/coverage-v8` 5.0.5); Dependabot now groups `vitest` and
  `@vitest/*` updates, whose exact peer dependency made separate bumps fail `npm ci`.

### Added

- Production images, one per module, for Scaleway Serverless Containers (`linux/amd64`, port from `PORT`): backend
  compiled into a GraalVM native executable on distroless (`backend/Dockerfile`), admin SPA served by
  static-web-server (`frontend/Dockerfile`), with the API URL given at startup (`BERGACAST_API_URL`).
- Automated releases (`.github/workflows/release.yml`): every green commit of `main` changing a module gets the next
  semantic version computed from the Conventional Commits (first release `1.0.0`); the images of the changed modules
  are published to the Scaleway Container Registry (`rg.fr-par.scw.cloud/bergacast`, tags `X.Y.Z`, `X.Y`, `X`,
  `latest`, `sha-…`), then the commit is tagged `vX.Y.Z` with a GitHub release. From then on, the GitHub releases are
  the changelog.
- Server version in the API root (`GET /api/v1` → `version`) and in the OpenAPI `info.version`; interface and server
  versions in the SPA footer.
- CI jobs checking the PR title (Conventional Commits) and the release scripts (ShellCheck, bats).
- CORS on the API for the admin SPA origins (`BERGACAST_CORS_ALLOWED_ORIGINS`).
- CI jobs building both images, with an end-to-end smoke test of the native backend (`backend/scripts/smoke-test.sh`).

- Podcast management through the REST API `/api/v1` (HAL, Problem Details RFC 9457, pagination, strong `ETag` /
  `If-Match`, JSON Merge Patch), with immutable slugs and reserved names.
- Episode publishing by `multipart/form-data` upload (`metadata` JSON part + `audio` file) on
  `POST /api/v1/{slug}/episodes`, with `Idempotency-Key` support and duplicate `guid` detection.
- Audio storage in PostgreSQL large objects, streamed upload with on-the-fly size and SHA-256 computation, automatic
  cleanup by database triggers.
- Public RSS 2.0 feed per podcast (`/{slug}.xml`) with iTunes extensions, strong ETag and 5-minute HTTP cache.
- Per-podcast feed settings: maximum number of episodes, eviction criterion, sort order, future episodes inclusion.
- Public audio streaming (`/{slug}/episodes/{episodeId}/audio.{ext}`) with single byte-range support and immutable
  caching.
- Per-podcast API tokens (`bgc_…`, stored as SHA-256 hashes) and admin JWT authentication (HS256, 15 minutes) through
  `POST /api/v1/access-tokens`.
- Admin user management, with an optional initial administrator created at startup.
- Admin single-page application (SvelteKit 3, Svelte 5) served under `/admin/`.
- Public OpenAPI documentation (`/v3/api-docs`, `/swagger-ui.html`).
- Multi-stage Docker image (non-root, layered jar, health check) and Docker Compose stack with PostgreSQL 18.
- GitHub Actions CI on pull requests (backend lint/tests/build, OpenAPI lint with Spectral, frontend lint/tests/build,
  Docker build) with a results report posted as a PR comment; weekly Dependabot updates.
- Tool versions pinned in `.sdkmanrc` (Java 25) and `.nvmrc` (Node 24), npm 12.2.1 via `packageManager`.

### Changed

- The admin SPA is no longer embedded in the backend jar nor served under `/admin/`: it has its own image and is
  served at the root of its own origin. Docker Compose runs `db`, `backend` and `frontend`.

[Unreleased]: https://github.com/Bergamof/bergacast/commits/main
