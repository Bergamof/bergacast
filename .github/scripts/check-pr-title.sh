#!/usr/bin/env bash
# Checks that a pull request title follows Conventional Commits: with squash merges it becomes the commit message of
# main, from which release-plan.sh computes the next version.
#
# Usage: check-pr-title.sh <title>
set -euo pipefail

readonly TYPES='build|chore|ci|docs|feat|fix|perf|refactor|revert|style|test'
readonly TITLE_PATTERN="^($TYPES)(\([a-z0-9][a-z0-9 ,/-]*\))?!?: [^ ].*$"

[ "$#" -eq 1 ] || { echo "Usage: $0 <title>" >&2; exit 2; }

if [[ "$1" =~ $TITLE_PATTERN ]]; then
    echo "Conventional Commits title: $1"
else
    echo "Not a Conventional Commits title: $1" >&2
    echo "Expected <type>[(scope)][!]: <description>, with type in ${TYPES//|/, }" >&2
    exit 1
fi
