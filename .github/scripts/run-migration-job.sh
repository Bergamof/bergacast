#!/usr/bin/env bash
# Points the migration job definition at an image, runs it and waits for the run to end: fails unless it succeeded.
#
# Usage: SCW_SECRET_KEY=… SCW_JOB_DEFINITION_ID=<uuid> run-migration-job.sh <image reference>
# Environment: see scaleway.bash; JOB_POLL_SECONDS (10), JOB_TIMEOUT_SECONDS (1800).
set -euo pipefail

# shellcheck source=SCRIPTDIR/scaleway.bash
source "$(dirname "${BASH_SOURCE[0]}")/scaleway.bash"

: "${JOB_POLL_SECONDS:=10}" "${JOB_TIMEOUT_SECONDS:=1800}"
: "${SCW_SECRET_KEY:?SCW_SECRET_KEY is required}" "${SCW_JOB_DEFINITION_ID:?SCW_JOB_DEFINITION_ID is required}"

[ "$#" -eq 1 ] || { echo "Usage: $0 <image reference>" >&2; exit 2; }
image="$1"
require_match "image reference" "$image" "$SCW_IMAGE_PATTERN"
require_match "job definition id" "$SCW_JOB_DEFINITION_ID" "$SCW_UUID_PATTERN"
require_match "region" "$SCW_REGION" "$SCW_REGION_PATTERN"

jobs_path="/serverless-jobs/v1alpha2/regions/$SCW_REGION"
definition_path="$jobs_path/job-definitions/$SCW_JOB_DEFINITION_ID"

echo "Migration job: image $image"
scw_api PATCH "$definition_path" "$(jq -nc --arg image "$image" '{image_uri: $image}')" >/dev/null
run_id=$(scw_api POST "$definition_path/start" '{}' \
    | jq -er '.job_runs | if length == 1 then .[0].id else error("expected one job run") end')
require_match "job run id" "$run_id" "$SCW_UUID_PATTERN"
echo "Job run $run_id started"

started=$SECONDS
while true; do
    run=$(scw_api GET "$jobs_path/job-runs/$run_id")
    state=$(jq -r .state <<<"$run")
    case "$state" in
        succeeded)
            echo "Migration job run $run_id succeeded"
            exit 0
            ;;
        failed | interrupted)
            echo "Migration job run $run_id $state: $(jq -r '.error_message // "no error message"' <<<"$run")" >&2
            exit 1
            ;;
        *) echo "Migration job run $run_id: $state" ;;
    esac
    [ $((SECONDS - started)) -lt "$JOB_TIMEOUT_SECONDS" ] \
        || { echo "Migration job run $run_id still $state after ${JOB_TIMEOUT_SECONDS}s" >&2; exit 1; }
    sleep "$JOB_POLL_SECONDS"
done
