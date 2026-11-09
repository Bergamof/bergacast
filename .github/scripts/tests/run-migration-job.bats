#!/usr/bin/env bats
# Tests of run-migration-job.sh against a stubbed Scaleway API (curl).

bats_require_minimum_version 1.5.0

load test_helper

readonly JOB_ID=11111111-2222-3333-4444-555555555555
readonly RUN_ID=66666666-7777-8888-9999-000000000000
readonly IMAGE=rg.fr-par.scw.cloud/bergacast/bergacast-migration:1.4.0

setup() {
    export SCW_SECRET_KEY=test-key SCW_JOB_DEFINITION_ID=$JOB_ID JOB_POLL_SECONDS=0 JOB_TIMEOUT_SECONDS=1000
}

migrate() {
    run --separate-stderr "$SCRIPTS_DIR/run-migration-job.sh" "$@"
}

none_running() { printf '{"job_runs":[{"id":"x","state":"succeeded"},{"id":"y","state":"failed"}]}'; }
started() { printf '{"job_runs":[{"id":"%s","state":"queued"}]}' "$RUN_ID"; }
state() { printf '{"id":"%s","state":"%s","error_message":%s}' "$RUN_ID" "$1" "${2:-null}"; }

@test "updates the job image, starts the job and waits for its success" {
    stub_command curl "$(none_running)" '{}' "$(started)" "$(state running)" "$(state succeeded)"

    migrate "$IMAGE"

    [ "$status" -eq 0 ]
    [[ "$output" == *"succeeded"* ]]
    [ "$(calls_of curl | wc -l)" -eq 5 ]
    calls_of curl | sed -n 1p | grep -F "/job-runs?job_definition_id=$JOB_ID"
    calls_of curl | sed -n 2p | grep -F -- "--request PATCH"
    calls_of curl | sed -n 2p | grep -F "/serverless-jobs/v1alpha2/regions/fr-par/job-definitions/$JOB_ID"
    calls_of curl | sed -n 2p | grep -F "\"image_uri\":\"$IMAGE\""
    calls_of curl | sed -n 3p | grep -F "/job-definitions/$JOB_ID/start"
    calls_of curl | sed -n 4p | grep -F "/job-runs/$RUN_ID"
}

@test "retries only the reads: a write replayed after it went through would run twice" {
    stub_command curl "$(none_running)" '{}' "$(started)" "$(state succeeded)"

    migrate "$IMAGE"

    calls_of curl | sed -n 1p | grep -F -- "--retry"
    ! calls_of curl | sed -n 2,3p | grep -F -- "--retry"
}

@test "refuses to start while a run of the job is still in progress" {
    stub_command curl "$(printf '{"job_runs":[{"id":"x","state":"running"}]}')"

    migrate "$IMAGE"

    [ "$status" -eq 1 ]
    [[ "$stderr" == *"1 run(s) of the migration job are still in progress"* ]]
    [ "$(calls_of curl | wc -l)" -eq 1 ]
}

@test "never puts the key in the arguments of curl" {
    stub_command curl "$(none_running)" '{}' "$(started)" "$(state succeeded)"

    migrate "$IMAGE"

    ! calls_of curl | grep -F test-key
}

@test "fails with the message of a failed run" {
    stub_command curl "$(none_running)" '{}' "$(started)" "$(state failed '"changeset 0003 failed"')"

    migrate "$IMAGE"

    [ "$status" -eq 1 ]
    [[ "$stderr" == *"failed: changeset 0003 failed"* ]]
}

@test "fails on a cancelled run" {
    stub_command curl "$(none_running)" '{}' "$(started)" "$(state canceled)"

    migrate "$IMAGE"

    [ "$status" -eq 1 ]
}

@test "cuts a long error message short" {
    stub_command curl "$(none_running)" '{}' "$(started)" "$(state failed "\"$(printf 'x%.0s' $(seq 1 500))\"")"

    migrate "$IMAGE"

    [ "$status" -eq 1 ]
    [ "${#stderr}" -lt 400 ]
}

@test "fails on an interrupted run" {
    stub_command curl "$(none_running)" '{}' "$(started)" "$(state interrupted)"

    migrate "$IMAGE"

    [ "$status" -eq 1 ]
    [[ "$stderr" == *"interrupted: no error message"* ]]
}

@test "fails when the run does not end in time" {
    export JOB_TIMEOUT_SECONDS=0
    stub_command curl "$(none_running)" '{}' "$(started)" "$(state running)"

    migrate "$IMAGE"

    [ "$status" -eq 1 ]
    [[ "$stderr" == *"still running after 0s"* ]]
}

@test "fails when the API refuses the update, without starting anything" {
    stub_command curl "$(none_running)" "FAIL HTTP 403"

    migrate "$IMAGE"

    [ "$status" -ne 0 ]
    [ "$(calls_of curl | wc -l)" -eq 2 ]
}

@test "fails when the start does not create exactly one run" {
    stub_command curl "$(none_running)" '{}' '{"job_runs":[]}'

    migrate "$IMAGE"

    [ "$status" -ne 0 ]
}

@test "rejects an invalid image reference or definition id before any call" {
    stub_command curl '{}'

    migrate 'image with spaces:1'
    [ "$status" -eq 2 ]

    SCW_JOB_DEFINITION_ID='../secrets' migrate "$IMAGE"
    [ "$status" -eq 2 ]

    [ ! -f "$BATS_TEST_TMPDIR/curl.calls" ]
}

@test "usage error without image" {
    migrate

    [ "$status" -eq 2 ]
}
