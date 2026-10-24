#!/usr/bin/env bats
# Tests of check-pr-title.sh: Conventional Commits pull request titles.

bats_require_minimum_version 1.5.0

load test_helper

check() {
    run --separate-stderr "$SCRIPTS_DIR/check-pr-title.sh" "$@"
}

# expect_accepted <title>...: each title passes the check (the failing title is printed by bats on failure).
expect_accepted() {
    local title
    for title in "$@"; do
        check "$title"
        echo "accepted? $title"
        [ "$status" -eq 0 ]
        [ "$output" = "Conventional Commits title: $title" ]
    done
}

# expect_rejected <title>...: each title fails the check.
expect_rejected() {
    local title
    for title in "$@"; do
        check "$title"
        echo "rejected? $title"
        [ "$status" -eq 1 ]
        [ -z "$output" ]
        [[ "$stderr" == *"Not a Conventional Commits title: $title"* ]]
    done
}

@test "accepts every type" {
    expect_accepted "build: x" "chore: x" "ci: x" "docs: x" "feat: x" "fix: x" "perf: x" "refactor: x" \
        "revert: x" "style: x" "test: x"
}

@test "accepts a scope" {
    expect_accepted "feat(api): add search" "fix(ui-forms): trim title" "chore(deps/backend): bump library" \
        "feat(api, ui): search" "feat(api,ui): search" "fix(1): bug"
}

@test "accepts a breaking change marker, with or without scope" {
    expect_accepted "feat!: drop the v0 API" "refactor(api)!: rename fields"
}

@test "accepts any description after the separator" {
    expect_accepted "fix: handle empty titles (#12)" "docs: README: Docker section" "feat: Ajout de la recherche"
}

@test "rejects an unknown or misspelled type" {
    expect_rejected "feature: add search" "wip: stuff" "Feat: add search" "FIX: bug" "fixes: bug"
}

@test "rejects a title without type" {
    expect_rejected "Add search" "" ": add search" "(api): add search"
}

@test "rejects a malformed scope" {
    expect_rejected "feat(): add search" "feat(API): add search" "feat(-api): add search" "feat( api): add search" \
        "feat(api_ui): add search" "feat (api): add search" "feat(api: add search" "feat(api)(ui): add search"
}

@test "rejects a misplaced or repeated breaking change marker" {
    expect_rejected "feat!(api): drop" "feat!!: drop" "feat(api)!!: drop" "feat !: drop"
}

@test "rejects a missing or malformed separator" {
    expect_rejected "feat add search" "feat:add search" "feat - add search" "feat:: add search"
}

@test "rejects a missing description" {
    expect_rejected "feat:" "feat: " "feat:  add search" "feat(api): "
}

@test "usage error without argument" {
    check

    [ "$status" -eq 2 ]
    [[ "$stderr" == *"Usage:"* ]]
}

@test "usage error with several arguments" {
    check feat: add search

    [ "$status" -eq 2 ]
    [[ "$stderr" == *"Usage:"* ]]
}
