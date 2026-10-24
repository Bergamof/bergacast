# shellcheck shell=bash
# Conventional Commits classification shared by the release scripts (sourced, not executed).

# commit_kind <commit>: prints breaking (`type!:` subject or `BREAKING CHANGE:` footer), feat, fix or other.
commit_kind() {
    local subject body
    subject=$(git log -1 --format=%s "$1")
    body=$(git log -1 --format=%b "$1")
    if [[ "$subject" =~ ^[a-zA-Z]+(\([^\)]*\))?!: ]] || grep -qE '^BREAKING[ -]CHANGE:' <<<"$body"; then
        echo breaking
    elif [[ "$subject" =~ ^feat(\([^\)]*\))?: ]]; then
        echo feat
    elif [[ "$subject" =~ ^fix(\([^\)]*\))?: ]]; then
        echo fix
    else
        echo other
    fi
}
