# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed

- Frontend tests run on Vitest 5 (`vitest` and `@vitest/coverage-v8` 5.0.5); Dependabot now groups `vitest` and
  `@vitest/*` updates, whose exact peer dependency made separate bumps fail `npm ci`.

### Added

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

[Unreleased]: https://github.com/Bergamof/bergacast/commits/main
