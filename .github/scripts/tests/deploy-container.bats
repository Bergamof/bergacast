#!/usr/bin/env bats
# Tests of deploy-container.sh against a stubbed Scaleway API (curl).

bats_require_minimum_version 1.5.0

load test_helper

readonly CONTAINER_ID=11111111-2222-3333-4444-555555555555
readonly IMAGE=rg.fr-par.scw.cloud/bergacast/bergacast-backend:1.4.0
readonly OLD_IMAGE=rg.fr-par.scw.cloud/bergacast/bergacast-backend:1.3.0

setup() {
    export SCW_SECRET_KEY=test-key DEPLOY_POLL_SECONDS=0 DEPLOY_TIMEOUT_SECONDS=1000 DEPLOY_GRACE_SECONDS=1000
}

deploy() {
    run --separate-stderr "$SCRIPTS_DIR/deploy-container.sh" "$@"
}

container() {
    printf '{"id":"%s","status":"%s","image":"%s","error_message":%s}' "$CONTAINER_ID" "$1" "$2" "${3:-null}"
}

@test "updates the image and waits until the container is ready on it" {
    stub_command curl "$(container ready "$OLD_IMAGE")" "$(container updating "$IMAGE")" "$(container updating "$IMAGE")" "$(container ready "$IMAGE")"

    deploy "$CONTAINER_ID" "$IMAGE"

    [ "$status" -eq 0 ]
    [[ "$output" == *"is ready on $IMAGE"* ]]
    calls_of curl | sed -n 1p | grep -F -- "--request GET"
    calls_of curl | sed -n 2p | grep -F -- "--request PATCH"
    calls_of curl | sed -n 2p | grep -F "/containers/v1/regions/fr-par/containers/$CONTAINER_ID"
    calls_of curl | sed -n 2p | grep -F "\"image\":\"$IMAGE\""
    calls_of curl | sed -n 3,4p | grep -F -- "--request GET"
}

@test "redeploys a container already on the image reference instead of updating it" {
    stub_command curl "$(container ready "$IMAGE")" "$(container upgrading "$IMAGE")" "$(container ready "$IMAGE")"

    deploy "$CONTAINER_ID" "$IMAGE"

    [ "$status" -eq 0 ]
    calls_of curl | sed -n 2p | grep -F -- "--request POST"
    calls_of curl | sed -n 2p | grep -F "/containers/$CONTAINER_ID/redeploy"
    ! calls_of curl | grep -F -- "--request PATCH"
}

@test "does not take the ready container of the old revision for a finished rollout" {
    stub_command curl \
        "$(container ready "$OLD_IMAGE")" \
        "$(container ready "$OLD_IMAGE")" \
        "$(container ready "$OLD_IMAGE")" \
        "$(container upgrading "$IMAGE")" \
        "$(container ready "$IMAGE")"

    deploy "$CONTAINER_ID" "$IMAGE"

    [ "$status" -eq 0 ]
    [ "$(calls_of curl | wc -l)" -eq 5 ]
}

@test "accepts a ready container on the new image once the grace period is over" {
    export DEPLOY_GRACE_SECONDS=0
    stub_command curl "$(container ready "$OLD_IMAGE")" "$(container ready "$IMAGE")"

    deploy "$CONTAINER_ID" "$IMAGE"

    [ "$status" -eq 0 ]
}

@test "waits while the container still runs another image" {
    export DEPLOY_GRACE_SECONDS=0 DEPLOY_TIMEOUT_SECONDS=0
    stub_command curl "$(container ready "$OLD_IMAGE")"

    deploy "$CONTAINER_ID" "$IMAGE"

    [ "$status" -eq 1 ]
    [[ "$stderr" == *"still ready after 0s"* ]]
}

@test "fails with the message of a container in error" {
    stub_command curl "$(container ready "$OLD_IMAGE")" "$(container updating "$IMAGE")" "$(container error "$IMAGE" '"image not found"')"

    deploy "$CONTAINER_ID" "$IMAGE"

    [ "$status" -eq 1 ]
    [[ "$stderr" == *"is error: image not found"* ]]
}

@test "fails on a container being deleted" {
    stub_command curl "$(container ready "$OLD_IMAGE")" "$(container deleting "$IMAGE")"

    deploy "$CONTAINER_ID" "$IMAGE"

    [ "$status" -eq 1 ]
    [[ "$stderr" == *"is deleting"* ]]
}

@test "fails on a locked container" {
    stub_command curl "$(container ready "$OLD_IMAGE")" "$(container locked "$IMAGE")"

    deploy "$CONTAINER_ID" "$IMAGE"

    [ "$status" -eq 1 ]
    [[ "$stderr" == *"is locked: no error message"* ]]
}

@test "fails when the rollout does not end in time" {
    export DEPLOY_TIMEOUT_SECONDS=0
    stub_command curl "$(container ready "$OLD_IMAGE")" "$(container updating "$IMAGE")"

    deploy "$CONTAINER_ID" "$IMAGE"

    [ "$status" -eq 1 ]
    [[ "$stderr" == *"still updating after 0s"* ]]
}

@test "fails when the API refuses the update" {
    stub_command curl "$(container ready "$OLD_IMAGE")" "FAIL HTTP 403"

    deploy "$CONTAINER_ID" "$IMAGE"

    [ "$status" -ne 0 ]
    [ "$(calls_of curl | wc -l)" -eq 2 ]
}

@test "rejects an invalid container id, image or region before any call" {
    stub_command curl '{}'

    deploy 'not-a-uuid' "$IMAGE"
    [ "$status" -eq 2 ]

    deploy "$CONTAINER_ID" 'no-tag'
    [ "$status" -eq 2 ]

    SCW_REGION='fr-par/../x' deploy "$CONTAINER_ID" "$IMAGE"
    [ "$status" -eq 2 ]

    [ ! -f "$BATS_TEST_TMPDIR/curl.calls" ]
}

@test "usage error with a single argument" {
    deploy "$CONTAINER_ID"

    [ "$status" -eq 2 ]
}
