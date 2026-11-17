#!/usr/bin/env bats
# Tests of npm-audit-gate.sh against hand-written `npm audit --json` reports.

bats_require_minimum_version 1.5.0

load test_helper

readonly ADVISORY=https://github.com/advisories/GHSA-aaaa-bbbb-cccc
readonly OTHER_ADVISORY=https://github.com/advisories/GHSA-dddd-eeee-ffff

gate() {
    run --separate-stderr "$SCRIPTS_DIR/npm-audit-gate.sh" "$BATS_TEST_TMPDIR/audit.json" "$BATS_TEST_TMPDIR/exceptions.json"
}

# report <severity> <advisory url>: a report whose package `lib` has this advisory, and `app` inherits it.
report() {
    printf '{"metadata":{"vulnerabilities":{"total":2}},"vulnerabilities":{"lib":{"severity":"%s","via":[{"name":"lib","title":"ReDoS","url":"%s","severity":"%s"}]},"app":{"severity":"%s","via":["lib"]}}}' \
        "$1" "$2" "$1" "$1" >"$BATS_TEST_TMPDIR/audit.json"
}

accept() {
    printf '[{"advisory":"%s","package":"lib","reason":"no fix","added":"2026-11-17"}]' "$1" >"$BATS_TEST_TMPDIR/exceptions.json"
}

setup() {
    echo '[]' >"$BATS_TEST_TMPDIR/exceptions.json"
}

@test "passes when nothing is vulnerable" {
    echo '{"metadata":{"vulnerabilities":{"total":0}},"vulnerabilities":{}}' >"$BATS_TEST_TMPDIR/audit.json"

    gate

    [ "$status" -eq 0 ]
}

@test "fails on a high advisory without exception" {
    report high "$ADVISORY"

    gate

    [ "$status" -eq 1 ]
    [[ "$stderr" == *"high lib: ReDoS ($ADVISORY)"* ]]
}

@test "fails on a critical advisory" {
    report critical "$ADVISORY"

    gate

    [ "$status" -eq 1 ]
}

@test "passes on a high advisory accepted as an exception, and on the packages inheriting it" {
    report high "$ADVISORY"
    accept "$ADVISORY"

    gate

    [ "$status" -eq 0 ]
}

@test "an exception covers only its own advisory" {
    report high "$OTHER_ADVISORY"
    accept "$ADVISORY"

    gate

    [ "$status" -eq 1 ]
    [[ "$stderr" == *"$OTHER_ADVISORY"* ]]
}

@test "ignores moderate advisories" {
    report moderate "$ADVISORY"

    gate

    [ "$status" -eq 0 ]
}

@test "fails when the report has no result, as after a registry failure" {
    echo '{"error":{"code":"ENOTFOUND","summary":"registry unreachable"}}' >"$BATS_TEST_TMPDIR/audit.json"

    gate

    [ "$status" -eq 1 ]
    [[ "$stderr" == *"did not run"* ]]
}

@test "usage error without arguments" {
    run --separate-stderr "$SCRIPTS_DIR/npm-audit-gate.sh"

    [ "$status" -eq 2 ]
}
