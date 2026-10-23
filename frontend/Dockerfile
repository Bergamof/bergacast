# syntax=docker/dockerfile:1

# Bergacast admin SPA image: static SvelteKit build served by static-web-server (a few MB of memory).
# Build context: frontend/. Tests are not run here: they run in CI.

ARG NODE_MAJOR=24
ARG STATIC_WEB_SERVER_VERSION=2.44.1

# ---------------------------------------------------------------------------
# Static build
# ---------------------------------------------------------------------------
FROM node:${NODE_MAJOR}-alpine AS build
ARG NPM_VERSION=12.2.1
WORKDIR /workspace

# package.json pins npm (packageManager) and .npmrc enables engine-strict.
RUN --mount=type=cache,target=/root/.npm \
    npm install --global "npm@${NPM_VERSION}"

COPY package.json package-lock.json .npmrc ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci --no-audit --no-fund

COPY . ./
RUN npm run build

# ---------------------------------------------------------------------------
# Runtime: static-web-server on Alpine (a shell is needed to write the runtime configuration)
# ---------------------------------------------------------------------------
FROM joseluisq/static-web-server:${STATIC_WEB_SERVER_VERSION}-alpine AS runtime
ENV SPA_ROOT=/srv/admin
USER root

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
