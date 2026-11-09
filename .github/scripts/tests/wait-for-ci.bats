#!/usr/bin/env bats
# Tests of wait-for-ci.sh: the release goes ahead only with a finished, green CI of the commit.

bats_require_minimum_version 1.5.0

load test_helper

setup() {
    export WAIT_POLL_SECONDS=0 WAIT_TIMEOUT_SECONDS=1000 WAIT_NO_RUN_GRACE_SECONDS=0
}

wait_for_ci() {
    run --separate-stderr "$SCRIPTS_DIR/wait-for-ci.sh" "$@"
}

run_json() {
    printf '[{"databaseId":%s,"status":"%s","conclusion":%s,"url":"https://ci.example/runs/%s","createdAt":"%s"}]' \
        "$1" "$2" "$3" "$1" "$4"
}

@test "succeeds when the CI of the commit is green" {
    stub_command gh "$(run_json 1 completed '"success"' 2026-11-09T10:00:00Z)"

    wait_for_ci abc123

    [ "$status" -eq 0 ]
    [[ "$output" == *"CI of abc123 is green: https://ci.example/runs/1"* ]]
    calls_of gh | grep -F -- "--commit abc123"
    calls_of gh | grep -F -- "--event push"
}

@test "waits for a running CI, then succeeds" {
    stub_command gh \
        "$(run_json 1 queued null 2026-11-09T10:00:00Z)" \
        "$(run_json 1 in_progress null 2026-11-09T10:00:00Z)" \
        "$(run_json 1 completed '"success"' 2026-11-09T10:00:00Z)"

    wait_for_ci abc123

    [ "$status" -eq 0 ]
    [ "$(calls_of gh | wc -l)" -eq 3 ]
    [[ "$stderr" == *"is queued"* && "$stderr" == *"is in_progress"* ]]
}

@test "fails when the CI failed" {
    stub_command gh "$(run_json 1 completed '"failure"' 2026-11-09T10:00:00Z)"

    wait_for_ci abc123

    [ "$status" -eq 1 ]
    [[ "$stderr" == *"ended with 'failure': https://ci.example/runs/1"* ]]
}

@test "fails when the CI was cancelled" {
    stub_command gh "$(run_json 1 completed '"cancelled"' 2026-11-09T10:00:00Z)"

    wait_for_ci abc123

    [ "$status" -eq 1 ]
    [[ "$stderr" == *"ended with 'cancelled'"* ]]
}

@test "fails when a running CI then fails" {
    stub_command gh \
        "$(run_json 1 in_progress null 2026-11-09T10:00:00Z)" \
        "$(run_json 1 completed '"failure"' 2026-11-09T10:00:00Z)"

    wait_for_ci abc123

    [ "$status" -eq 1 ]
    [[ "$stderr" == *"ended with 'failure'"* ]]
}

@test "judges the most recent run when the commit has several" {
    stub_command gh "[$(run_json 1 completed '"failure"' 2026-11-09T10:00:00Z | tr -d '[]'),$(run_json 2 completed '"success"' 2026-11-09T11:00:00Z | tr -d '[]')]"

    wait_for_ci abc123

    [ "$status" -eq 0 ]
    [[ "$output" == *"runs/2"* ]]
}

@test "fails when the commit has no CI run" {
    stub_command gh "[]"

    wait_for_ci abc123

    [ "$status" -eq 1 ]
    [[ "$stderr" == *"No CI run for commit abc123"* ]]
}

@test "waits for the CI run of a fresh push to appear" {
    export WAIT_NO_RUN_GRACE_SECONDS=1000
    stub_command gh "[]" "$(run_json 1 completed '"success"' 2026-11-09T10:00:00Z)"

    wait_for_ci abc123

    [ "$status" -eq 0 ]
    [[ "$stderr" == *"Waiting for the CI run of abc123 to start"* ]]
}

@test "fails when the CI does not end in time" {
    export WAIT_TIMEOUT_SECONDS=0
    stub_command gh "$(run_json 1 in_progress null 2026-11-09T10:00:00Z)"

    wait_for_ci abc123

    [ "$status" -eq 1 ]
    [[ "$stderr" == *"Timed out waiting for the CI of abc123"* ]]
}

@test "retries when GitHub cannot be reached, then succeeds" {
    stub_command gh "FAIL HTTP 502" "$(run_json 1 completed '"success"' 2026-11-09T10:00:00Z)"

    wait_for_ci abc123

    [ "$status" -eq 0 ]
    [[ "$stderr" == *"Cannot list the CI runs, retrying"* ]]
}

@test "usage error without commit" {
    wait_for_ci

    [ "$status" -eq 2 ]
}
