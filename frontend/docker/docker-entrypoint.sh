#!/bin/sh
# Writes the runtime configuration of the SPA from the environment, then serves the static files.
set -eu

: "${BERGACAST_API_URL:?BERGACAST_API_URL is required (public URL of the Bergacast backend)}"

# The value ends up in a JavaScript string: only accept a plain http(s) origin. grep matches line by line,
# so characters outside the URL alphabet (a newline above all) are rejected first.
reject_api_url() {
    echo "BERGACAST_API_URL must be an http(s) origin such as https://api.example.com, got: $BERGACAST_API_URL" >&2
    exit 1
}
case "$BERGACAST_API_URL" in
    *[!A-Za-z0-9.:/-]*) reject_api_url ;;
esac
if ! printf '%s' "$BERGACAST_API_URL" | grep -Eq '^https?://[A-Za-z0-9.-]+(:[0-9]+)?/?$'; then
    reject_api_url
fi

printf 'globalThis.bergacastConfig = { apiUrl: "%s" };\n' "$BERGACAST_API_URL" > "$SPA_ROOT/config.js"

# Serverless platforms (Scaleway Serverless Containers) give the port to listen on in PORT.
export SERVER_PORT="${PORT:-8080}"
exec static-web-server --config-file /etc/static-web-server.toml --root "$SPA_ROOT" \
    --page-fallback "$SPA_ROOT/index.html" "$@"
