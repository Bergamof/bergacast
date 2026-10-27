#!/usr/bin/env bash
# One-shot export of the media stored in PostgreSQL before the move to the object storage (audio files in large
# objects, cover images in bytea) to the bucket, at the keys the backend now reads, then writes every feed through
# the API. Run it once the new backend version is deployed (migration V3 keeps the former content); safe to run again:
# objects are overwritten with the same content. Each file is checked against the SHA-256 recorded on upload.
#
# Usage: DATABASE_URL='postgresql://user:password@host:5432/bergacast?sslmode=require' \
#        BERGACAST_STORAGE_ENDPOINT=https://s3.fr-par.scw.cloud BERGACAST_STORAGE_REGION=fr-par \
#        BERGACAST_STORAGE_BUCKET=... BERGACAST_STORAGE_ACCESS_KEY=... BERGACAST_STORAGE_SECRET_KEY=... \
#        BASE_URL=https://api.example.com ADMIN_USERNAME=... ADMIN_PASSWORD=... \
#        backend/scripts/export-media-to-object-storage.sh
# Requires bash, psql, curl (with --aws-sigv4), jq, base64 and sha256sum.
set -euo pipefail

: "${DATABASE_URL:?DATABASE_URL is required (libpq connection URI of the Bergacast database)}"
: "${BERGACAST_STORAGE_ENDPOINT:?BERGACAST_STORAGE_ENDPOINT is required}"
: "${BERGACAST_STORAGE_REGION:?BERGACAST_STORAGE_REGION is required}"
: "${BERGACAST_STORAGE_BUCKET:?BERGACAST_STORAGE_BUCKET is required}"
: "${BERGACAST_STORAGE_ACCESS_KEY:?BERGACAST_STORAGE_ACCESS_KEY is required}"
: "${BERGACAST_STORAGE_SECRET_KEY:?BERGACAST_STORAGE_SECRET_KEY is required}"
: "${BASE_URL:?BASE_URL is required (public URL of the backend, to write the feeds)}"
: "${ADMIN_USERNAME:?ADMIN_USERNAME is required}"
: "${ADMIN_PASSWORD:?ADMIN_PASSWORD is required}"

for tool in psql curl jq base64 sha256sum; do
    command -v "$tool" >/dev/null || { echo "Missing required tool: $tool" >&2; exit 1; }
done

# Same headers as the backend writes (audio/AudioUploads, podcast/PodcastImageService).
AUDIO_CACHE_CONTROL="max-age=31536000, public, immutable"
IMAGE_CACHE_CONTROL="max-age=300, public"

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

step() { printf '\n==> %s\n' "$*"; }
fail() { printf 'FAILED: %s\n' "$*" >&2; exit 1; }
query() { psql "$DATABASE_URL" --no-psqlrc --quiet --tuples-only --no-align --field-separator=$'\t' -v ON_ERROR_STOP=1 -c "$1"; }

# verify FILE SHA256: fails when the content differs from what was recorded on upload.
verify() {
    [ "$(sha256sum "$1" | cut -d' ' -f1)" = "$2" ] || fail "$1 does not match its recorded SHA-256 $2"
}

# upload FILE KEY CONTENT_TYPE CACHE_CONTROL SHA256: PUT Object signed with AWS Signature V4 (path-style URL).
upload() {
    curl -fsS -o /dev/null -T "$1" \
        --aws-sigv4 "aws:amz:$BERGACAST_STORAGE_REGION:s3" \
        --user "$BERGACAST_STORAGE_ACCESS_KEY:$BERGACAST_STORAGE_SECRET_KEY" \
        -H "Content-Type: $3" -H "Cache-Control: $4" -H "x-amz-content-sha256: $5" \
        "${BERGACAST_STORAGE_ENDPOINT%/}/$BERGACAST_STORAGE_BUCKET/$2"
}

step "Audio files (large objects)"
audio_count=0
while IFS=$'\t' read -r slug episode_id oid extension mime_type sha256; do
    file="$WORK_DIR/audio"
    psql "$DATABASE_URL" --no-psqlrc --quiet -v ON_ERROR_STOP=1 -c "\\lo_export $oid '$file'" >/dev/null
    verify "$file" "$sha256"
    key="$slug/episodes/$episode_id/audio.$extension"
    upload "$file" "$key" "$mime_type" "$AUDIO_CACHE_CONTROL" "$sha256"
    echo "$key"
    audio_count=$((audio_count + 1))
done < <(query "SELECT p.slug, e.id, a.content_oid, a.file_extension, a.mime_type, a.sha256
                FROM episode e
                JOIN podcast p ON p.id = e.podcast_id
                JOIN audio_file a ON a.id = e.audio_file_id
                WHERE a.content_oid IS NOT NULL
                ORDER BY p.slug, e.added_at")
echo "$audio_count audio file(s) exported"

step "Cover images (bytea)"
image_count=0
while IFS=$'\t' read -r podcast_id slug mime_type sha256; do
    case "$mime_type" in
        image/jpeg) extension=jpg ;;
        image/png) extension=png ;;
        *) fail "unsupported image type $mime_type for $slug" ;;
    esac
    file="$WORK_DIR/image"
    query "SELECT encode(content, 'base64') FROM podcast_image WHERE podcast_id = '$podcast_id'" | base64 -d >"$file"
    verify "$file" "$sha256"
    key="$slug/image.$extension"
    upload "$file" "$key" "$mime_type" "$IMAGE_CACHE_CONTROL" "$sha256"
    echo "$key"
    image_count=$((image_count + 1))
done < <(query "SELECT p.id, p.slug, i.mime_type, i.sha256
                FROM podcast_image i
                JOIN podcast p ON p.id = i.podcast_id
                WHERE i.content IS NOT NULL
                ORDER BY p.slug")
echo "$image_count image(s) exported"

step "Feeds, written by the backend"
token=$(curl -fsS -H 'Content-Type: application/json' \
    -d "$(jq -n --arg u "$ADMIN_USERNAME" --arg p "$ADMIN_PASSWORD" '{username: $u, password: $p}')" \
    "${BASE_URL%/}/api/v1/access-tokens" | jq -er '.accessToken')
while read -r slug; do
    curl -fsS -X POST -H "Authorization: Bearer $token" \
        "${BASE_URL%/}/api/v1/podcasts/$slug/feed-publications" | jq -r '"\(.feedUrl) (\(.episodeCount) episode(s))"'
done < <(query "SELECT slug FROM podcast ORDER BY slug")

printf '\nExport done: check the feeds and audio files, then the former content can be dropped (next migration).\n'
