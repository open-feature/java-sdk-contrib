#!/usr/bin/env bash
# Renders the conformance reports in $1 as a Markdown table, one row per suite.
#
# A scenario's outcome is the most severe status among its steps, the same rule the TCK's report
# plugin documents, so a capability-gated skip counts as skipped rather than passed.
set -euo pipefail

dir="${1:?usage: tck-report-summary.sh <report dir>}"
shopt -s nullglob
envelopes=("$dir"/*.json)

echo "## Provider conformance"
echo
if [ ${#envelopes[@]} -eq 0 ]; then
  echo "No conformance reports were written."
  exit 0
fi

echo "| Provider | Configuration | Backend | Passed | Failed | Skipped | Declared | Known deviations |"
echo "|---|---|---|--:|--:|--:|---|--:|"
for envelope in "${envelopes[@]}"; do
  stream="${envelope%.json}.ndjson"
  counts=$(jq -rs '
    def rank: {"FAILED":5,"AMBIGUOUS":4,"UNDEFINED":3,"PENDING":2,"SKIPPED":1,"PASSED":0}[.] // 0;
    [ .[] | .testStepFinished // empty ]
    | group_by(.testCaseStartedId)
    | map([ .[].testStepResult.status ] | max_by(rank))
    | [ (map(select(. == "PASSED")) | length),
        (map(select(rank >= 2)) | length),
        (map(select(. == "SKIPPED")) | length) ]
    | @tsv' "$stream")
  read -r passed failed skipped <<<"$counts"
  jq -r --arg p "$passed" --arg f "$failed" --arg s "$skipped" '
    "| \(.provider.name) | \(.provider.configuration) | \(.backend.backendType) | \($p) | \($f) | \($s) | "
    + ((.declaration.declared // []) | join(" "))
    + " | \((.knownDeviations // []) | length) |"' "$envelope"
done
