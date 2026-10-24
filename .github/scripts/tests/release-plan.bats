#!/usr/bin/env bats
# Tests of release-plan.sh: version bump, changed modules and skipped releases, from the Conventional Commits of a repo.

bats_require_minimum_version 1.5.0

load test_helper

setup() {
    setup_repo
}

plan() {
    run --separate-stderr "$SCRIPTS_DIR/release-plan.sh" "$@"
}

# expect_release <version> <previous tag> <modules JSON>: the plan of the last run is a release with these values.
expect_release() {
    [ "$status" -eq 0 ]
    [ "$output" = "release=true
version=$1
previous-tag=$2
modules=$3" ]
}

expect_no_release() {
    [ "$status" -eq 0 ]
    [ "$output" = "release=false" ]
}

# First release

@test "first release is 1.0.0 with every module, whatever the commits" {
    commit backend/a.txt "feat!: initial backend"
    commit docs/readme.md "docs: readme"

    plan HEAD

    expect_release 1.0.0 "" '["backend","frontend"]'
    [[ "$stderr" == *"Release 1.0.0 of backend frontend (previous tag: none)"* ]]
}

@test "first release ignores non-release tags" {
    commit backend/a.txt "feat: backend"
    git tag foo
    git tag v1.2
    git tag v1.2.3-rc.1
    git tag v1.2.3.4
    git tag 1.2.3
    commit frontend/a.txt "fix: frontend"

    plan HEAD

    expect_release 1.0.0 "" '["backend","frontend"]'
}

# Changed modules

@test "only the backend changed since the last release tag" {
    commit backend/a.txt "feat: backend"
    commit frontend/a.txt "feat: frontend"
    git tag v1.0.0
    commit backend/a.txt "fix: backend bug"
    commit docs/guide.md "docs: guide"

    plan HEAD

    expect_release 1.0.1 v1.0.0 '["backend"]'
}

@test "only the frontend changed since the last release tag" {
    commit backend/a.txt "feat: backend"
    git tag v1.0.0
    commit frontend/a.txt "fix(ui): frontend bug"

    plan HEAD

    expect_release 1.0.1 v1.0.0 '["frontend"]'
}

@test "both modules changed since the last release tag" {
    commit backend/a.txt "feat: backend"
    git tag v1.0.0
    commit frontend/a.txt "fix: frontend"
    commit backend/a.txt "fix: backend"

    plan HEAD

    expect_release 1.0.1 v1.0.0 '["backend","frontend"]'
}

@test "no release when no module changed since the last release tag" {
    commit backend/a.txt "feat: backend"
    git tag v1.0.0
    commit docs/guide.md "docs: guide"
    commit .github/workflows/ci.yml "ci: workflow"
    commit README.md "chore: readme"

    plan HEAD

    expect_no_release
    [[ "$stderr" == *"No module changed since v1.0.0"* ]]
}

@test "no release for a breaking change outside the modules" {
    commit backend/a.txt "feat: backend"
    git tag v1.0.0
    commit docs/guide.md "feat!: rewrite the documentation"

    plan HEAD

    expect_no_release
}

@test "a file named like a module outside its directory is not a module change" {
    commit backend/a.txt "feat: backend"
    git tag v1.0.0
    commit docs/backend.md "docs: backend notes"
    commit backend-notes/a.txt "chore: notes"

    plan HEAD

    expect_no_release
}

# Version bump

@test "patch bump for a fix" {
    commit backend/a.txt "feat: backend"
    git tag v1.2.3
    commit backend/a.txt "fix: bug"

    plan HEAD

    expect_release 1.2.4 v1.2.3 '["backend"]'
}

@test "patch bump for non-feature types and non-conventional subjects" {
    commit backend/a.txt "feat: backend"
    git tag v1.2.3
    commit backend/a.txt "chore(deps): bump library"
    commit backend/a.txt "refactor: tidy"
    commit backend/a.txt "Update the backend"
    commit backend/a.txt "feature: not a feat type"
    commit backend/a.txt "feat add something without colon"

    plan HEAD

    expect_release 1.2.4 v1.2.3 '["backend"]'
}

@test "minor bump for a feat, resetting the patch" {
    commit backend/a.txt "feat: backend"
    git tag v1.2.3
    commit backend/a.txt "fix: bug"
    commit backend/a.txt "feat: new endpoint"
    commit backend/a.txt "chore: tidy"

    plan HEAD

    expect_release 1.3.0 v1.2.3 '["backend"]'
}

@test "minor bump for a scoped feat" {
    commit backend/a.txt "feat: backend"
    git tag v1.2.3
    commit frontend/a.txt "feat(ui): new page"

    plan HEAD

    expect_release 1.3.0 v1.2.3 '["frontend"]'
}

@test "major bump for a type with an exclamation mark" {
    commit backend/a.txt "feat: backend"
    git tag v1.2.3
    commit backend/a.txt "feat: new endpoint"
    commit backend/a.txt "feat!: drop the v0 API"
    commit backend/a.txt "fix: bug"

    plan HEAD

    expect_release 2.0.0 v1.2.3 '["backend"]'
}

@test "major bump for a scoped non-feature type with an exclamation mark" {
    commit backend/a.txt "feat: backend"
    git tag v1.2.3
    commit backend/a.txt "refactor(api)!: rename fields"

    plan HEAD

    expect_release 2.0.0 v1.2.3 '["backend"]'
}

@test "major bump for a BREAKING CHANGE footer" {
    commit backend/a.txt "feat: backend"
    git tag v1.2.3
    commit backend/a.txt "fix: change the slug rules" "Slugs are now validated.

BREAKING CHANGE: existing invalid slugs are rejected"

    plan HEAD

    expect_release 2.0.0 v1.2.3 '["backend"]'
}

@test "major bump for a BREAKING-CHANGE footer" {
    commit backend/a.txt "feat: backend"
    git tag v1.2.3
    commit frontend/a.txt "chore: drop old browsers" "BREAKING-CHANGE: Safari 15 is no longer supported"

    plan HEAD

    expect_release 2.0.0 v1.2.3 '["frontend"]'
}

@test "BREAKING CHANGE mentioned inside a line of the body is not a footer" {
    commit backend/a.txt "feat: backend"
    git tag v1.2.3
    commit backend/a.txt "fix: bug" "This is not a BREAKING CHANGE: just a fix"

    plan HEAD

    expect_release 1.2.4 v1.2.3 '["backend"]'
}

@test "bump only considers commits changing a changed module" {
    commit backend/a.txt "feat: backend"
    git tag v1.2.3
    commit docs/guide.md "feat!: rewrite the documentation"
    commit .github/workflows/ci.yml "feat: new CI job"
    commit backend/a.txt "fix: bug"

    plan HEAD

    expect_release 1.2.4 v1.2.3 '["backend"]'
}

@test "bump ignores commits already released" {
    commit backend/a.txt "feat!: backend"
    commit backend/a.txt "feat: endpoint"
    git tag v1.0.0
    commit backend/a.txt "fix: bug"

    plan HEAD

    expect_release 1.0.1 v1.0.0 '["backend"]'
}

# Previous tag

@test "release tags are ordered by version, not alphabetically" {
    commit backend/a.txt "feat: backend"
    git tag v1.9.0
    commit backend/a.txt "feat: endpoint"
    git tag v1.10.0
    commit backend/a.txt "fix: bug"

    plan HEAD

    expect_release 1.10.1 v1.10.0 '["backend"]'
}

@test "non-release tags after the last release tag are ignored" {
    commit backend/a.txt "feat: backend"
    git tag v1.0.0
    commit backend/a.txt "fix: bug"
    git tag foo
    git tag v2.0
    git tag v3.0.0-rc.1
    commit frontend/a.txt "fix: other bug"
    git tag edge

    plan HEAD

    expect_release 1.0.1 v1.0.0 '["backend","frontend"]'
}

@test "release tags not merged into the commit are ignored" {
    commit backend/a.txt "feat: backend"
    git tag v1.0.0
    git switch --quiet -c other
    commit backend/b.txt "feat: elsewhere"
    git tag v5.0.0
    git switch --quiet main
    commit backend/a.txt "fix: bug"

    plan HEAD

    expect_release 1.0.1 v1.0.0 '["backend"]'
}

# Already released commits

@test "no release for a commit carrying a release tag" {
    commit backend/a.txt "feat: backend"
    git tag v1.0.0
    commit backend/a.txt "feat: endpoint"
    git tag v1.1.0

    plan HEAD

    expect_no_release
    [[ "$stderr" == *"Commit already released in v1.1.0"* ]]
}

@test "no release for an older commit already contained in a newer release tag" {
    commit backend/a.txt "feat: backend"
    git tag v1.0.0
    commit backend/a.txt "feat: endpoint"
    local older
    older=$(git rev-parse HEAD)
    commit frontend/a.txt "fix: page"
    git tag v1.1.0

    plan "$older"

    expect_no_release
    [[ "$stderr" == *"Commit already released in v1.1.0"* ]]
}

@test "a non-release tag on the commit does not prevent its release" {
    commit backend/a.txt "feat: backend"
    git tag v1.0.0
    commit backend/a.txt "fix: bug"
    git tag latest
    git tag v1.0

    plan HEAD

    expect_release 1.0.1 v1.0.0 '["backend"]'
}

@test "plans an older commit from the tags it contains" {
    commit backend/a.txt "feat: backend"
    git tag v1.0.0
    commit backend/a.txt "fix: bug"
    local older
    older=$(git rev-parse HEAD)
    commit frontend/a.txt "feat: page"

    plan "$older"

    expect_release 1.0.1 v1.0.0 '["backend"]'
}

# Usage

@test "accepts a branch name as commit" {
    commit backend/a.txt "feat: backend"

    plan main

    expect_release 1.0.0 "" '["backend","frontend"]'
}

@test "usage error without argument" {
    commit backend/a.txt "feat: backend"

    plan

    [ "$status" -eq 2 ]
    [ -z "$output" ]
    [[ "$stderr" == *"Usage:"* ]]
}

@test "usage error with two arguments" {
    commit backend/a.txt "feat: backend"

    plan HEAD HEAD

    [ "$status" -eq 2 ]
    [[ "$stderr" == *"Usage:"* ]]
}

@test "error for an unknown commit" {
    commit backend/a.txt "feat: backend"

    plan 0123456789abcdef0123456789abcdef01234567

    [ "$status" -eq 2 ]
    [ -z "$output" ]
    [[ "$stderr" == *"Unknown commit: 0123456789abcdef0123456789abcdef01234567"* ]]
}

@test "error for a revision that is not a commit" {
    commit backend/a.txt "feat: backend"

    plan "HEAD:backend"

    [ "$status" -eq 2 ]
    [[ "$stderr" == *"Unknown commit: HEAD:backend"* ]]
}
