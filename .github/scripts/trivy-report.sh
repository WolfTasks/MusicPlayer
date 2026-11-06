#!/usr/bin/env bash
# Usage: trivy-report.sh <trivy.json> <title> <gate|report>
# Writes a Markdown table of findings to $GITHUB_STEP_SUMMARY (or stdout) and, in
# "gate" mode, exits 1 when any HIGH or CRITICAL vulnerability is present.
set -euo pipefail

json="$1"
title="$2"
mode="$3"
out="${GITHUB_STEP_SUMMARY:-/dev/stdout}"

rows=$(jq -r '
  [.Results[]?.Vulnerabilities[]?]
  | sort_by({"CRITICAL":0,"HIGH":1,"MEDIUM":2,"LOW":3,"UNKNOWN":4}[.Severity], .PkgName)
  | .[]
  | "| \(.Severity) | `\(.PkgName)` | \(.InstalledVersion) | [\(.VulnerabilityID)](\(.PrimaryURL // "")) | \(.FixedVersion // "–") |"
' "$json")

count() { jq --arg s "$1" '[.Results[]?.Vulnerabilities[]? | select(.Severity == $s)] | length' "$json"; }
crit=$(count CRITICAL); high=$(count HIGH); med=$(count MEDIUM); low=$(count LOW)

{
  echo "## $title"
  echo
  echo "CRITICAL: **$crit** · HIGH: **$high** · MEDIUM: $med · LOW: $low"
  echo
  if [ -n "$rows" ]; then
    echo "| Severity | Package | Installed | CVE | Fixed in |"
    echo "|---|---|---|---|---|"
    echo "$rows"
  else
    echo "No known vulnerabilities. ✅"
  fi
  echo
} >> "$out"

if [ "$mode" = "gate" ] && [ $((crit + high)) -gt 0 ]; then
  echo "::error::$((crit + high)) HIGH/CRITICAL vulnerabilities in $title"
  exit 1
fi
