# syntax=docker/dockerfile:1

# Bergacast admin SPA image: static SvelteKit build served by static-web-server (a few MB of memory).
# Build context: frontend/. Tests are not run here: they run in CI.

# Base images are pinned by digest (a tag can be rewritten); Dependabot updates tag and digest together.

# ---------------------------------------------------------------------------
# Static build
# ---------------------------------------------------------------------------
FROM node:26-alpine@sha256:6bd9ab43c0ec0190ca7b2d193906cb8b7edf6e7f4afa43ddeccd6452710bffb5 AS build
ARG NPM_VERSION=12.2.2
WORKDIR /workspace

# package.json pins npm (packageManager) and .npmrc enables engine-strict.
RUN --mount=type=cache,target=/root/.npm \
    npm install --global "npm@${NPM_VERSION}"

COPY package.json package-lock.json .npmrc ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci --no-audit --no-fund

COPY . ./
# Release version (vX.Y.Z without the v), set by the release workflow and shown by the SPA.
ARG VERSION=0.0.0-dev
RUN npm pkg set version="${VERSION}" && npm run build

# ---------------------------------------------------------------------------
# Runtime: static-web-server on Alpine (a shell is needed to write the runtime configuration)
# ---------------------------------------------------------------------------
FROM joseluisq/static-web-server:2.44.3-alpine@sha256:b3f7db31db9a68754205b3c0831fe5b066bd1daeb4f45aaa5007847a8c29ede2 AS runtime
ENV SPA_ROOT=/srv/admin
USER root
# The image is rebuilt upstream only at each static-web-server release: pick up the Alpine security fixes
# (OpenSSL, zlib, musl) published since.
RUN apk upgrade --no-cache

COPY docker/static-web-server.toml /etc/static-web-server.toml
COPY docker/docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
COPY --from=build /workspace/build/ ${SPA_ROOT}/
# Files stay owned by root and read-only, except config.js which the entrypoint rewrites at startup
# (its precompressed variants would serve the stale development value).
RUN rm -f "${SPA_ROOT}/config.js.br" "${SPA_ROOT}/config.js.gz" \
    && chown sws:sws "${SPA_ROOT}/config.js"

USER sws:sws
# Overridden by the platform through PORT (Scaleway Serverless Containers).
EXPOSE 8080
ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
