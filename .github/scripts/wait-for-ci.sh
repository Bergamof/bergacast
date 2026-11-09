#!/usr/bin/env bash
# Succeeds only once the CI of a commit of main is over and entirely green: waits for it when it is still running,
# fails when it failed, was cancelled, or never started.
#
# Usage: GH_TOKEN=… GH_REPO=<owner>/<repo> wait-for-ci.sh <commit>
# Environment (seconds): WAIT_POLL_SECONDS (30), WAIT_TIMEOUT_SECONDS (5400), WAIT_NO_RUN_GRACE_SECONDS (120: how long
# a commit may stay without any CI run, which GitHub creates a few seconds after a push).
set -euo pipefail

: "${WAIT_POLL_SECONDS:=30}" "${WAIT_TIMEOUT_SECONDS:=5400}" "${WAIT_NO_RUN_GRACE_SECONDS:=120}"

[ "$#" -eq 1 ] || { echo "Usage: $0 <commit>" >&2; exit 2; }
commit="$1"

fail() {
    echo "$1" >&2
    exit 1
}

# The most recent CI run of the push of this commit (a re-run keeps its run), as JSON, empty without any run.
latest_push_run() {
    local runs
    runs=$(gh run list --workflow ci.yml --commit "$commit" --event push --limit 20 \
        --json databaseId,status,conclusion,url,createdAt) || return 1
    jq -c 'sort_by(.createdAt) | last // empty' <<<"$runs"
}

started=$SECONDS
while true; do
    elapsed=$((SECONDS - started))
    if ! run=$(latest_push_run); then
        echo "Cannot list the CI runs, retrying" >&2
        [ "$elapsed" -lt "$WAIT_TIMEOUT_SECONDS" ] || fail "Timed out listing the CI runs of $commit"
    elif [ -z "$run" ]; then
        [ "$elapsed" -lt "$WAIT_NO_RUN_GRACE_SECONDS" ] || fail "No CI run for commit $commit"
        echo "Waiting for the CI run of $commit to start" >&2
    else
        status=$(jq -r .status <<<"$run")
        url=$(jq -r .url <<<"$run")
        if [ "$status" = completed ]; then
            conclusion=$(jq -r '.conclusion // "unknown"' <<<"$run")
            [ "$conclusion" = success ] || fail "CI of $commit ended with '$conclusion': $url"
            echo "CI of $commit is green: $url"
            exit 0
        fi
        echo "CI of $commit is $status: $url" >&2
        [ "$elapsed" -lt "$WAIT_TIMEOUT_SECONDS" ] || fail "Timed out waiting for the CI of $commit: $url"
    fi
    sleep "$WAIT_POLL_SECONDS"
done
