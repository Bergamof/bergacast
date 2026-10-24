#!/usr/bin/env bash
# Plans the release of a commit of main, from the Conventional Commits since the last vX.Y.Z tag it contains.
#
# Usage: release-plan.sh <commit>   (in a clone with the full history and the tags)
# Prints GITHUB_OUTPUT lines:
#   release=true|false   false when no module changed, or when a release tag already contains the commit
#   version=X.Y.Z        next version: 1.0.0 without previous tag, else major (`type!:` or a `BREAKING CHANGE:`
#                        footer), minor (`feat`) or patch (any other type) bump, from the commits changing a module
#   previous-tag=vX.Y.Z  last release tag contained in the commit (empty for the first release)
#   modules=[…]          JSON array of the modules changed since that tag (every module for the first release)
set -euo pipefail

# shellcheck source=SCRIPTDIR/conventional-commits.bash
source "$(dirname "${BASH_SOURCE[0]}")/conventional-commits.bash"

readonly MODULES=(backend frontend)
readonly FIRST_VERSION=1.0.0
readonly RELEASE_TAG_PATTERN='^v[0-9]+\.[0-9]+\.[0-9]+$'

[ "$#" -eq 1 ] || { echo "Usage: $0 <commit>" >&2; exit 2; }
commit=$(git rev-parse --verify --quiet "$1^{commit}") || { echo "Unknown commit: $1" >&2; exit 2; }

release_tags() { grep -E "$RELEASE_TAG_PATTERN" || true; }

skip() {
    echo "$1" >&2
    echo "release=false"
    exit 0
}

# A CI run that completes after a newer one: the newer release already shipped this commit.
covering_tag=$(git tag --contains "$commit" | release_tags | head -1)
[ -z "$covering_tag" ] || skip "Commit already released in $covering_tag"

previous_tag=$(git tag --merged "$commit" --sort=-v:refname | release_tags | head -1)

changed_modules=()
for module in "${MODULES[@]}"; do
    if [ -z "$previous_tag" ] || ! git diff --quiet "$previous_tag" "$commit" -- "$module/"; then
        changed_modules+=("$module")
    fi
done
[ "${#changed_modules[@]}" -gt 0 ] || skip "No module changed since $previous_tag"

# 2 = major, 1 = minor, 0 = patch.
bump_level() {
    local range="$1" level=0 hash
    shift
    for hash in $(git rev-list "$range" -- "$@"); do
        case $(commit_kind "$hash") in
            breaking)
                echo 2
                return
                ;;
            feat) level=1 ;;
        esac
    done
    echo "$level"
}

if [ -z "$previous_tag" ]; then
    version="$FIRST_VERSION"
else
    IFS=. read -r major minor patch <<<"${previous_tag#v}"
    module_paths=()
    for module in "${changed_modules[@]}"; do
        module_paths+=("$module/")
    done
    case $(bump_level "$previous_tag..$commit" "${module_paths[@]}") in
        2) version="$((major + 1)).0.0" ;;
        1) version="$major.$((minor + 1)).0" ;;
        *) version="$major.$minor.$((patch + 1))" ;;
    esac
fi

modules_json=$(printf '"%s",' "${changed_modules[@]}")
echo "Release $version of ${changed_modules[*]} (previous tag: ${previous_tag:-none})" >&2
echo "release=true"
echo "version=$version"
echo "previous-tag=$previous_tag"
echo "modules=[${modules_json%,}]"
