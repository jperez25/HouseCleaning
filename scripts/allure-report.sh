#!/usr/bin/env bash
# Build the Allure HTML report for each test module: <module>/allure-results -> <module>/allure-report.
#
#   allure-report.sh [--open] [MODULE...]
#
#   MODULE   house-cleaning-integration-tests and/or house-cleaning-bdd-tests (default: every module that has results)
#   --open   serve the (last) report over HTTP in the browser; Allure reports do not work from file://
#
# Needs Node (npx allure@3). The summary email is sent by the test run itself (-DsendEmail=true), not by this script.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
command -v npx >/dev/null || { echo "ERROR: Node/npx is not installed" >&2; exit 1; }

OPEN=false; mods=()
for a in "$@"; do
  case "$a" in
    --open) OPEN=true ;;
    *) mods+=("$a") ;;
  esac
done
if ((${#mods[@]} == 0)); then
  for m in house-cleaning-integration-tests house-cleaning-bdd-tests; do [[ -d "$ROOT/$m/allure-results" ]] && mods+=("$m"); done
fi
((${#mods[@]} > 0)) || { echo "ERROR: no <module>/allure-results found. Run the tests first." >&2; exit 1; }

last=""
for m in "${mods[@]}"; do
  dir="$ROOT/$m"
  [[ -d "$dir/allure-results" ]] || { echo "ERROR: $m/allure-results not found" >&2; exit 1; }
  rm -rf "$dir/allure-report" "$dir/allure-report.zip"
  npx --yes allure@3 generate "$dir/allure-results" -o "$dir/allure-report"
  echo "Report: $dir/allure-report"
  last="$dir/allure-report"
done


if $OPEN; then npx --yes allure@3 open "$last"; fi
