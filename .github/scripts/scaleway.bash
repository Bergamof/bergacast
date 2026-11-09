#!/usr/bin/env bash
# Calls of the Scaleway API shared by the deployment scripts (curl and jq only: no binary to download and trust).
# Source it; needs SCW_SECRET_KEY. Environment: SCW_API_URL (https://api.scaleway.com), SCW_REGION (fr-par).

# shellcheck disable=SC2034 # the patterns are used by the scripts that source this file
: "${SCW_API_URL:=https://api.scaleway.com}" "${SCW_REGION:=fr-par}"

readonly SCW_UUID_PATTERN='^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
readonly SCW_IMAGE_PATTERN='^[a-z0-9._/-]+:[A-Za-z0-9._-]+$'
readonly SCW_REGION_PATTERN='^[a-z]{2}-[a-z]{3}$'

# require_match <name> <value> <pattern>: stops unless the value is what a path or a body may safely carry.
require_match() {
    [[ "$2" =~ $3 ]] || { echo "Invalid $1: '$2'" >&2; exit 2; }
}

# scw_api <method> <path> [json body]: prints the response body, fails on an HTTP error (the body goes to stderr).
# The key goes through a configuration read from a descriptor, so it never shows in a process list. Only reads are
# retried: replaying a write that went through (a job start) would do it twice.
scw_api() {
    local method="$1" path="$2" body="${3:-}"
    local -a data=() retry=()
    [ -z "$body" ] || data=(--data "$body")
    [ "$method" != GET ] || retry=(--retry 3)
    curl --silent --show-error --fail-with-body "${retry[@]}" --max-time 60 \
        --config <(printf 'header = "X-Auth-Token: %s"\n' "$SCW_SECRET_KEY") \
        --header 'Content-Type: application/json' \
        --request "$method" "${data[@]}" "$SCW_API_URL$path"
}

# error_message_of: the error message of the JSON read on the standard input, cut short (it comes from the platform and
# may quote arbitrary text).
error_message_of() {
    jq -r '(.error_message // "no error message")[0:300]'
}
