#!/usr/bin/env bash
# Fails on any high or critical advisory of an `npm audit --json` report, except the accepted exceptions.
#
# Usage: npm-audit-gate.sh <audit report.json> <exceptions.json>
# The exceptions file is a JSON array of {advisory, package, reason, added}: each one is justified and dated, and
# removed as soon as a fixed version exists. An advisory is identified by its URL; the packages that merely depend on
# the vulnerable one inherit the advisory, so they need no exception of their own.
set -euo pipefail

[ "$#" -eq 2 ] || { echo "Usage: $0 <audit report.json> <exceptions.json>" >&2; exit 2; }
report="$1"
exceptions="$2"

# A registry failure produces a report without results: that must not read as "no vulnerability".
jq -e '.metadata.vulnerabilities' "$report" >/dev/null \
    || { echo "The npm audit report has no result: the audit did not run" >&2; exit 1; }

blocking=$(jq -r --slurpfile accepted "$exceptions" '
    [.vulnerabilities[]?.via[]? | select(type == "object")
        | select(.severity == "high" or .severity == "critical")
        | select(.url as $url | ($accepted[0] | map(.advisory) | index($url)) | not)]
    | unique_by(.url)[]
    | "\(.severity) \(.name): \(.title) (\(.url))"' "$report")

if [ -n "$blocking" ]; then
    echo "High or critical advisories without an exception:" >&2
    echo "$blocking" >&2
    exit 1
fi
echo "No high or critical advisory besides the accepted exceptions."
