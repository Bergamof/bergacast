#!/usr/bin/env bash
# Writes the Markdown notes of a release: published images, then the commits since the previous release grouped by
# Conventional Commits type (with squash merges, one commit per pull request, whose `(#N)` GitHub turns into a link).
#
# Usage: IMAGE_PREFIX=<registry>/<namespace>/bergacast \
#        release-notes.sh <version> <previous tag, empty for the first release> <commit> <module>...
set -euo pipefail

# shellcheck source=SCRIPTDIR/conventional-commits.bash
source "$(dirname "${BASH_SOURCE[0]}")/conventional-commits.bash"

: "${IMAGE_PREFIX:?IMAGE_PREFIX is required (images are named <IMAGE_PREFIX>-<module>)}"

[ "$#" -ge 4 ] || { echo "Usage: $0 <version> <previous tag> <commit> <module>..." >&2; exit 2; }
version="$1" previous_tag="$2" commit="$3"
shift 3

range="$commit"
[ -z "$previous_tag" ] || range="$previous_tag..$commit"

breaking=() features=() fixes=() others=()
while IFS= read -r hash; do
    entry="- $(git log -1 --format=%s "$hash") ($(git rev-parse --short "$hash"))"
    case $(commit_kind "$hash") in
        breaking) breaking+=("$entry") ;;
        feat) features+=("$entry") ;;
        fix) fixes+=("$entry") ;;
        *) others+=("$entry") ;;
    esac
done < <(git rev-list "$range")

section() {
    local title="$1"
    shift
    [ "$#" -gt 0 ] || return 0
    printf '\n## %s\n\n' "$title"
    printf '%s\n' "$@"
}

echo "## Images"
echo
for module in "$@"; do
    echo "- \`$IMAGE_PREFIX-$module:$version\`"
done
section "Breaking changes" "${breaking[@]}"
section "Features" "${features[@]}"
section "Fixes" "${fixes[@]}"
section "Other changes" "${others[@]}"
