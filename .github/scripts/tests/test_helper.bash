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

# stub_command <name> <response>...: installs a command that prints its responses one after the other (the last one
# repeats) and records its arguments, one call per line, in $BATS_TEST_TMPDIR/<name>.calls. A response starting with
# "FAIL " makes the command fail with the rest as its error message, like an HTTP error would.
stub_command() {
    local name="$1" index=0 response
    shift
    mkdir -p "$BATS_TEST_TMPDIR/bin" "$BATS_TEST_TMPDIR/$name.responses"
    for response in "$@"; do
        index=$((index + 1))
        printf '%s' "$response" >"$BATS_TEST_TMPDIR/$name.responses/$index"
    done
    cat >"$BATS_TEST_TMPDIR/bin/$name" <<STUB
#!/usr/bin/env bash
dir="$BATS_TEST_TMPDIR"
echo "\$*" >>"\$dir/$name.calls"
count=\$(cat "\$dir/$name.count" 2>/dev/null || echo 0)
count=\$((count + 1))
echo "\$count" >"\$dir/$name.count"
file="\$dir/$name.responses/\$count"
[ -f "\$file" ] || file="\$dir/$name.responses/$index"
response=\$(cat "\$file")
if [[ "\$response" == "FAIL "* ]]; then
    echo "\${response#FAIL }" >&2
    exit 22
fi
printf '%s\\n' "\$response"
STUB
    chmod +x "$BATS_TEST_TMPDIR/bin/$name"
    export PATH="$BATS_TEST_TMPDIR/bin:$PATH"
}

# calls_of <name>: the recorded calls of a stubbed command.
calls_of() {
    cat "$BATS_TEST_TMPDIR/$1.calls"
}
