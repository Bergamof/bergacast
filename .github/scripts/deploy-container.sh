#!/usr/bin/env bash
# Points a serverless container at an image (which makes the platform roll out a new revision) and waits until the
# container serves that image: fails when it ends in error or never gets ready.
#
# Usage: SCW_SECRET_KEY=… deploy-container.sh <container uuid> <image reference>
# Environment: see scaleway.bash; DEPLOY_POLL_SECONDS (10), DEPLOY_TIMEOUT_SECONDS (900), DEPLOY_GRACE_SECONDS (30:
# how long a container may keep saying "ready" before the rollout shows, so that an update not yet visible is not
# taken for a finished one).
set -euo pipefail

# shellcheck source=SCRIPTDIR/scaleway.bash
source "$(dirname "${BASH_SOURCE[0]}")/scaleway.bash"

: "${DEPLOY_POLL_SECONDS:=10}" "${DEPLOY_TIMEOUT_SECONDS:=900}" "${DEPLOY_GRACE_SECONDS:=30}"
: "${SCW_SECRET_KEY:?SCW_SECRET_KEY is required}"

[ "$#" -eq 2 ] || { echo "Usage: $0 <container uuid> <image reference>" >&2; exit 2; }
container_id="$1" image="$2"
require_match "container id" "$container_id" "$SCW_UUID_PATTERN"
require_match "image reference" "$image" "$SCW_IMAGE_PATTERN"
require_match "region" "$SCW_REGION" "$SCW_REGION_PATTERN"

container_path="/containers/v1/regions/$SCW_REGION/containers/$container_id"

echo "Container $container_id: image $image"
container=$(scw_api PATCH "$container_path" "$(jq -nc --arg image "$image" '{image: $image}')")

started=$SECONDS
rolling_out=false
while true; do
    status=$(jq -r .status <<<"$container")
    case "$status" in
        error | locked)
            echo "Container $container_id is $status: $(jq -r '.error_message // "no error message"' <<<"$container")" >&2
            exit 1
            ;;
        ready) ;;
        *) rolling_out=true ;;
    esac
    elapsed=$((SECONDS - started))
    if [ "$status" = ready ] && [ "$(jq -r .image <<<"$container")" = "$image" ] \
        && { [ "$rolling_out" = true ] || [ "$elapsed" -ge "$DEPLOY_GRACE_SECONDS" ]; }; then
        echo "Container $container_id is ready on $image"
        exit 0
    fi
    [ "$elapsed" -lt "$DEPLOY_TIMEOUT_SECONDS" ] \
        || { echo "Container $container_id still $status after ${DEPLOY_TIMEOUT_SECONDS}s" >&2; exit 1; }
    echo "Container $container_id: $status"
    sleep "$DEPLOY_POLL_SECONDS"
    container=$(scw_api GET "$container_path")
done
