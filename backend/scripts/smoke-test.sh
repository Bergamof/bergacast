#!/usr/bin/env bash
# End-to-end smoke test of a running Bergacast backend, used on the native image where reflection or resources
# missing from the AOT hints only fail at runtime. Exercises every feature through HTTP: health, login, CORS,
# HAL/JSON, merge patch, ETag/If-Match, Problem Details, multipart upload into large objects, RSS feed, Range
# requests, cover image upload with downscaling (ImageIO/AWT native libraries), OpenAPI and Swagger UI.
#
# Usage: BASE_URL=http://localhost:8080 ADMIN_USERNAME=admin ADMIN_PASSWORD=... CORS_ORIGIN=https://admin.example \
#        backend/scripts/smoke-test.sh
# Requires bash, curl, jq, sha256sum and head.
set -euo pipefail

: "${BASE_URL:?BASE_URL is required}"
: "${ADMIN_USERNAME:?ADMIN_USERNAME is required}"
: "${ADMIN_PASSWORD:?ADMIN_PASSWORD is required}"
: "${CORS_ORIGIN:?CORS_ORIGIN is required (an origin listed in BERGACAST_CORS_ALLOWED_ORIGINS)}"
STARTUP_TIMEOUT_SECONDS="${STARTUP_TIMEOUT_SECONDS:-60}"

for tool in curl jq sha256sum head; do
    command -v "$tool" >/dev/null || { echo "Missing required tool: $tool" >&2; exit 1; }
done

FIXTURES_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/fixtures"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT
SLUG="smoke-$(date +%s)"
AUDIO_FILE="$WORK_DIR/episode.mp3"
AUDIO_SIZE=$((3 * 1024 * 1024 + 17))

step() { printf '\n==> %s\n' "$*"; }
fail() { printf 'FAILED: %s\n' "$*" >&2; exit 1; }

# request METHOD PATH EXPECTED_STATUS [curl options...]: prints the body, fails on any other status.
request() {
    local method="$1" path="$2" expected="$3"
    shift 3
    local status
    status=$(curl -sS -o "$WORK_DIR/body" -D "$WORK_DIR/headers" -w '%{http_code}' -X "$method" "$@" "$BASE_URL$path")
    if [ "$status" != "$expected" ]; then
        cat "$WORK_DIR/body" >&2
        fail "$method $path answered $status, expected $expected"
    fi
    cat "$WORK_DIR/body"
}

header() { grep -i "^$1:" "$WORK_DIR/headers" | head -1 | cut -d' ' -f2- | tr -d '\r'; }
path_of() { printf '%s' "$1" | sed -E 's#^https?://[^/]+##'; }

step "Waiting for the health endpoint"
deadline=$((SECONDS + STARTUP_TIMEOUT_SECONDS))
until curl -fsS "$BASE_URL/actuator/health" 2>/dev/null | jq -e '.status == "UP"' >/dev/null; do
    [ "$SECONDS" -lt "$deadline" ] || fail "not healthy after ${STARTUP_TIMEOUT_SECONDS}s"
    sleep 1
done

step "API root (HAL, server version from the build info)"
request GET /api/v1 200 | jq -e '._links.podcasts.href and (.version | test("^[0-9]+\\.[0-9]+\\.[0-9]+"))' >/dev/null

step "Admin login"
ADMIN_TOKEN=$(request POST /api/v1/access-tokens 200 -H 'Content-Type: application/json' \
    -d "$(jq -n --arg u "$ADMIN_USERNAME" --arg p "$ADMIN_PASSWORD" '{username: $u, password: $p}')" |
    jq -er '.accessToken')
ADMIN=(-H "Authorization: Bearer $ADMIN_TOKEN")

step "Unauthenticated API call is a Problem Details 401"
request GET /api/v1/podcasts 401 >/dev/null
[[ "$(header Content-Type)" == application/problem+json* ]] || fail "401 is not application/problem+json"

step "CORS preflight from the admin SPA origin"
request OPTIONS /api/v1/podcasts 200 -H "Origin: $CORS_ORIGIN" -H 'Access-Control-Request-Method: PATCH' \
    -H 'Access-Control-Request-Headers: authorization,content-type,if-match' >/dev/null
[ "$(header Access-Control-Allow-Origin)" = "$CORS_ORIGIN" ] || fail "CORS origin not allowed"
request OPTIONS "/api/v1/any-podcast/episodes" 200 -H "Origin: $CORS_ORIGIN" \
    -H 'Access-Control-Request-Method: POST' -H 'Access-Control-Request-Headers: authorization,idempotency-key' >/dev/null
request OPTIONS "/api/v1/podcasts/any-podcast/image" 200 -H "Origin: $CORS_ORIGIN" \
    -H 'Access-Control-Request-Method: PUT' -H 'Access-Control-Request-Headers: authorization,content-type,if-match' >/dev/null
request OPTIONS /api/v1/podcasts 403 -H 'Origin: https://foreign.example' \
    -H 'Access-Control-Request-Method: GET' >/dev/null
[[ "$(header Content-Type)" == application/problem+json* ]] || fail "CORS rejection is not application/problem+json"

step "Podcast creation"
request POST /api/v1/podcasts 201 "${ADMIN[@]}" -H 'Content-Type: application/json' \
    -d "{\"slug\":\"$SLUG\",\"title\":\"Smoke test\",\"description\":\"Native image check\",\"language\":\"fr\"}" |
    jq -e '._links.feed.href' >/dev/null
[ -n "$(header Location)" ] || fail "no Location header"

step "Validation error is a 422 with field errors"
request POST /api/v1/podcasts 422 "${ADMIN[@]}" -H 'Content-Type: application/json' -d '{"slug":"Not a slug"}' |
    jq -e '.errors | length > 0' >/dev/null

step "Podcast update (JSON Merge Patch, If-Match)"
request GET "/api/v1/podcasts/$SLUG" 200 "${ADMIN[@]}" >/dev/null
ETAG=$(header ETag)
request PATCH "/api/v1/podcasts/$SLUG" 200 "${ADMIN[@]}" -H 'Content-Type: application/merge-patch+json' \
    -H "If-Match: $ETAG" -d '{"author":"Bergacast CI"}' | jq -e '.author == "Bergacast CI"' >/dev/null
request PATCH "/api/v1/podcasts/$SLUG" 412 "${ADMIN[@]}" -H 'Content-Type: application/merge-patch+json' \
    -H "If-Match: $ETAG" -d '{"author":"stale"}' >/dev/null

step "Podcast API token"
PODCAST_TOKEN=$(request POST "/api/v1/podcasts/$SLUG/api-tokens" 201 "${ADMIN[@]}" \
    -H 'Content-Type: application/json' -d '{"name":"smoke"}' | jq -er '.token')

step "Episode upload with the podcast token (multipart, large object)"
head -c "$AUDIO_SIZE" /dev/urandom >"$AUDIO_FILE"
AUDIO_SHA256=$(sha256sum "$AUDIO_FILE" | cut -d' ' -f1)
EPISODE=$(request POST "/api/v1/$SLUG/episodes" 201 -H "Authorization: Bearer $PODCAST_TOKEN" \
    -H 'Idempotency-Key: smoke-episode-1' \
    -F 'metadata={"title":"First episode","durationSeconds":42};type=application/json' \
    -F "audio=@$AUDIO_FILE;type=audio/mpeg")
printf '%s' "$EPISODE" | jq -e --arg sha "$AUDIO_SHA256" --argjson size "$AUDIO_SIZE" \
    '.audio.sha256 == $sha and .audio.sizeBytes == $size' >/dev/null || fail "unexpected audio metadata"
AUDIO_PATH=$(path_of "$(printf '%s' "$EPISODE" | jq -er '.audio.url')")
EPISODE_PATH=$(path_of "$(printf '%s' "$EPISODE" | jq -er '._links.self.href')")

step "Idempotent retry returns the same episode"
RETRY_ID=$(request POST "/api/v1/$SLUG/episodes" 201 -H "Authorization: Bearer $PODCAST_TOKEN" \
    -H 'Idempotency-Key: smoke-episode-1' \
    -F 'metadata={"title":"First episode"};type=application/json' \
    -F "audio=@$AUDIO_FILE;type=audio/mpeg" | jq -er '.id')
[ "$RETRY_ID" = "$(printf '%s' "$EPISODE" | jq -er '.id')" ] || fail "idempotent retry created another episode"

step "Episode update by an admin"
request PATCH "$EPISODE_PATH" 200 "${ADMIN[@]}" -H 'Content-Type: application/merge-patch+json' \
    -d '{"episodeNumber":1}' | jq -e '.episodeNumber == 1' >/dev/null

step "Public RSS feed"
request GET "/$SLUG.xml" 200 | grep -q '<title>First episode</title>' || fail "episode missing from the feed"

step "Public audio, full and partial"
curl -fsS "$BASE_URL$AUDIO_PATH" -o "$WORK_DIR/downloaded"
[ "$(sha256sum "$WORK_DIR/downloaded" | cut -d' ' -f1)" = "$AUDIO_SHA256" ] || fail "downloaded audio differs"
RANGE_LENGTH=$(request GET "$AUDIO_PATH" 206 -H 'Range: bytes=1048576-' | wc -c)
[ "$RANGE_LENGTH" -eq $((AUDIO_SIZE - 1048576)) ] || fail "unexpected range length $RANGE_LENGTH"

step "Cover image upload: a 4000x2000 PNG is downscaled to 3000x1500 (AWT/ImageIO native code)"
IMAGE=$(request PUT "/api/v1/podcasts/$SLUG/image" 201 "${ADMIN[@]}" -H 'Content-Type: image/png' \
    --data-binary "@$FIXTURES_DIR/cover-4000x2000.png")
printf '%s' "$IMAGE" | jq -e '.mimeType == "image/png" and .width == 3000 and .height == 1500' >/dev/null ||
    fail "unexpected image metadata: $IMAGE"
IMAGE_ETAG=$(header ETag)
IMAGE_URL=$(printf '%s' "$IMAGE" | jq -er '.url')
IMAGE_PATH=$(path_of "$IMAGE_URL")
[ "$IMAGE_ETAG" = "\"$(printf '%s' "$IMAGE" | jq -er '.sha256')\"" ] || fail "image ETag is not its SHA-256"

step "Public cover image (GET, HEAD, If-None-Match)"
curl -fsS -D "$WORK_DIR/headers" "$BASE_URL$IMAGE_PATH" -o "$WORK_DIR/image.png"
[[ "$(header Content-Type)" == image/png* ]] || fail "image is not served as image/png"
[ "$(sha256sum "$WORK_DIR/image.png" | cut -d' ' -f1)" = "$(printf '%s' "$IMAGE" | jq -er '.sha256')" ] ||
    fail "served image differs from the stored one"
request HEAD "$IMAGE_PATH" 200 -I >/dev/null
request GET "$IMAGE_PATH" 304 -H "If-None-Match: $IMAGE_ETAG" >/dev/null

step "Feed and podcast advertise the uploaded image"
request GET "/$SLUG.xml" 200 | grep -qF "<itunes:image href=\"$IMAGE_URL\"" || fail "image missing from the feed"
request GET "/api/v1/podcasts/$SLUG" 200 "${ADMIN[@]}" |
    jq -e '.image.width == 3000 and ._links.image.href' >/dev/null || fail "image missing from the podcast"

step "Cover image replacement: a 3200x160 JPEG is downscaled to 3000x150 (If-Match)"
request PUT "/api/v1/podcasts/$SLUG/image" 422 "${ADMIN[@]}" -H 'Content-Type: image/jpeg' \
    --data-binary "@$FIXTURES_DIR/cover-4000x2000.png" | jq -e '.errors | length == 1' >/dev/null
request PUT "/api/v1/podcasts/$SLUG/image" 200 "${ADMIN[@]}" -H 'Content-Type: image/jpeg' \
    -H "If-Match: $IMAGE_ETAG" --data-binary "@$FIXTURES_DIR/cover-3200x160.jpg" |
    jq -e '.mimeType == "image/jpeg" and .width == 3000 and .height == 150' >/dev/null ||
    fail "unexpected JPEG image metadata"
request GET "/$SLUG/image.jpg" 200 >/dev/null
[[ "$(header Content-Type)" == image/jpeg* ]] || fail "image is not served as image/jpeg"

step "Cover image deletion"
request DELETE "/api/v1/podcasts/$SLUG/image" 204 "${ADMIN[@]}" >/dev/null
request GET "/$SLUG/image.jpg" 404 >/dev/null

step "Robots kept away (robots.txt, X-Robots-Tag)"
request GET /robots.txt 200 | grep -qx 'Disallow: /' || fail "robots.txt does not disallow crawling"
[ "$(header X-Robots-Tag)" = "noindex, nofollow" ] || fail "X-Robots-Tag missing"

step "OpenAPI document and Swagger UI"
request GET /v3/api-docs 200 | jq -e '.openapi and (.paths | length > 0)' >/dev/null
request GET /swagger-ui/index.html 200 >/dev/null

step "Administrator management (custom password constraint)"
request POST /api/v1/admin-users 422 "${ADMIN[@]}" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$SLUG\",\"password\":\"short\"}" | jq -e '.errors[0].field == "password"' >/dev/null
ADMIN_USER_PATH=$(path_of "$(request POST /api/v1/admin-users 201 "${ADMIN[@]}" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$SLUG\",\"password\":\"a-long-enough-password\"}" | jq -er '._links.self.href')")
request PATCH "$ADMIN_USER_PATH" 200 "${ADMIN[@]}" -H 'Content-Type: application/merge-patch+json' \
    -d '{"password":"another-long-enough-password"}' >/dev/null
request DELETE "$ADMIN_USER_PATH" 204 "${ADMIN[@]}" >/dev/null

step "Unknown route is a Problem Details 404"
request GET /no-such-podcast.xml 404 >/dev/null

step "Cleanup"
request DELETE "/api/v1/podcasts/$SLUG" 204 "${ADMIN[@]}" >/dev/null
request GET "/$SLUG.xml" 404 >/dev/null

printf '\nSmoke test passed against %s\n' "$BASE_URL"
