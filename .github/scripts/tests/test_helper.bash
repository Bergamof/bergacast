#!/usr/bin/env bash
# Shared helpers of the release scripts tests: a throwaway git repository per test, isolated from the user's git config.

# shellcheck disable=SC2034 # used by the .bats files
readonly SCRIPTS_DIR="$BATS_TEST_DIRNAME/.."

# Creates an empty repository on main in $BATS_TEST_TMPDIR/repo and moves into it.
setup_repo() {
    export HOME="$BATS_TEST_TMPDIR/home" GIT_CONFIG_NOSYSTEM=1
    mkdir -p "$HOME"
    git init --quiet -b main "$BATS_TEST_TMPDIR/repo"
    cd "$BATS_TEST_TMPDIR/repo" || return 1
    git config user.name "Bergacast Tests"
    git config user.email "tests@bergacast.invalid"
    git config commit.gpgsign false
    git config tag.gpgsign false
}

# commit <file> <subject> [body]: appends a line to <file> (creating its directories) and commits it.
commit() {
    local file="$1" subject="$2" body="${3:-}"
    mkdir -p "$(dirname "$file")"
    echo "$subject" >>"$file"
    git add "$file"
    if [ -n "$body" ]; then
        git commit --quiet -m "$subject" -m "$body"
    else
        git commit --quiet -m "$subject"
    fi
}

# Prints the abbreviated hash of a revision, as release-notes.sh writes it.
short_hash() {
    git rev-parse --short "$1"
}
