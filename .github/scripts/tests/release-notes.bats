#!/usr/bin/env bats
# Tests of release-notes.sh: images list and commits grouped by Conventional Commits type.

bats_require_minimum_version 1.5.0

load test_helper

setup() {
    setup_repo
    export IMAGE_PREFIX=rg.fr-par.scw.cloud/bergacast/bergacast
}

notes() {
    run --separate-stderr "$SCRIPTS_DIR/release-notes.sh" "$@"
}

# entry <revision>: the line of a commit in the notes.
entry() {
    echo "- $(git log -1 --format=%s "$1") ($(short_hash "$1"))"
}

@test "lists the images of the released modules" {
    commit backend/a.txt "fix: bug"

    notes 1.2.3 "" HEAD backend frontend

    [ "$status" -eq 0 ]
    [ "${lines[0]}" = "## Images" ]
    [ "${lines[1]}" = "- \`rg.fr-par.scw.cloud/bergacast/bergacast-backend:1.2.3\`" ]
    [ "${lines[2]}" = "- \`rg.fr-par.scw.cloud/bergacast/bergacast-frontend:1.2.3\`" ]
}

@test "lists only the given modules" {
    commit frontend/a.txt "fix: bug"

    notes 1.0.1 "" HEAD frontend

    [ "$status" -eq 0 ]
    [[ "$output" == *"bergacast-frontend:1.0.1"* ]]
    [[ "$output" != *"bergacast-backend"* ]]
}

@test "groups the commits since the previous tag by type" {
    commit backend/a.txt "feat: initial"
    git tag v1.0.0
    commit backend/a.txt "chore(deps): bump library (#3)"
    local chore
    chore=$(entry HEAD)
    commit backend/a.txt "fix(api): handle empty title (#4)"
    local fix
    fix=$(entry HEAD)
    commit frontend/a.txt "feat(ui): episode page (#5)"
    local feature_ui
    feature_ui=$(entry HEAD)
    commit backend/a.txt "feat!: drop the v0 API (#6)"
    local breaking
    breaking=$(entry HEAD)
    commit backend/a.txt "feat: podcast search (#7)"
    local feature
    feature=$(entry HEAD)

    notes 2.0.0 v1.0.0 HEAD backend frontend

    [ "$status" -eq 0 ]
    [ "$output" = "## Images

- \`$IMAGE_PREFIX-backend:2.0.0\`
- \`$IMAGE_PREFIX-frontend:2.0.0\`

## Breaking changes

$breaking

## Features

$feature
$feature_ui

## Fixes

$fix

## Other changes

$chore" ]
}

@test "omits empty sections" {
    commit backend/a.txt "fix: bug"
    local fix
    fix=$(entry HEAD)

    notes 1.0.0 "" HEAD backend

    [ "$status" -eq 0 ]
    [ "$output" = "## Images

- \`$IMAGE_PREFIX-backend:1.0.0\`

## Fixes

$fix" ]
}

@test "excludes the previous tag and the commits before it" {
    commit backend/a.txt "feat: before"
    commit backend/a.txt "feat: tagged"
    git tag v1.0.0
    commit backend/a.txt "fix: after"

    notes 1.0.1 v1.0.0 HEAD backend

    [ "$status" -eq 0 ]
    [[ "$output" == *"fix: after"* ]]
    [[ "$output" != *"feat: before"* ]]
    [[ "$output" != *"feat: tagged"* ]]
    [[ "$output" != *"## Features"* ]]
}

@test "excludes the commits after the released commit" {
    commit backend/a.txt "feat: released"
    commit backend/a.txt "fix: later"

    notes 1.0.0 "" HEAD~1 backend

    [ "$status" -eq 0 ]
    [[ "$output" == *"feat: released"* ]]
    [[ "$output" != *"fix: later"* ]]
}

@test "includes the whole history without previous tag" {
    commit backend/a.txt "chore: first commit"
    git tag foo
    commit docs/guide.md "docs: guide"
    commit backend/a.txt "feat: endpoint"

    notes 1.0.0 "" HEAD backend frontend

    [ "$status" -eq 0 ]
    [[ "$output" == *"$(entry HEAD~2)"* ]]
    [[ "$output" == *"$(entry HEAD~1)"* ]]
    [[ "$output" == *"$(entry HEAD)"* ]]
}

@test "commits with a BREAKING CHANGE or BREAKING-CHANGE footer are breaking changes" {
    commit backend/a.txt "fix: slug rules" "Slugs are validated.

BREAKING CHANGE: invalid slugs are rejected"
    local space
    space=$(entry HEAD)
    commit frontend/a.txt "feat(ui): new layout" "BREAKING-CHANGE: old browsers are unsupported"
    local hyphen
    hyphen=$(entry HEAD)

    notes 2.0.0 "" HEAD backend

    [ "$status" -eq 0 ]
    [ "$output" = "## Images

- \`$IMAGE_PREFIX-backend:2.0.0\`

## Breaking changes

$hyphen
$space" ]
}

@test "scoped types with an exclamation mark are breaking changes" {
    commit backend/a.txt "refactor(api)!: rename fields"

    notes 2.0.0 "" HEAD backend

    [ "$status" -eq 0 ]
    [ "${lines[2]}" = "## Breaking changes" ]
    [ "${lines[3]}" = "$(entry HEAD)" ]
}

@test "subjects not starting with feat or fix types are other changes" {
    commit backend/a.txt "feature: not a feat type"
    commit backend/a.txt "fixup: not a fix type"
    commit backend/a.txt "Update the backend"

    notes 1.0.1 "" HEAD backend

    [ "$status" -eq 0 ]
    [[ "$output" != *"## Features"* ]]
    [[ "$output" != *"## Fixes"* ]]
    [[ "$output" == *"## Other changes

$(entry HEAD)
$(entry HEAD~1)
$(entry HEAD~2)" ]]
}

@test "fails without IMAGE_PREFIX" {
    commit backend/a.txt "fix: bug"
    unset IMAGE_PREFIX

    notes 1.0.0 "" HEAD backend

    [ "$status" -ne 0 ]
    [ -z "$output" ]
    [[ "$stderr" == *"IMAGE_PREFIX is required"* ]]
}

@test "usage error without module" {
    commit backend/a.txt "fix: bug"

    notes 1.0.0 "" HEAD

    [ "$status" -eq 2 ]
    [ -z "$output" ]
    [[ "$stderr" == *"Usage:"* ]]
}
