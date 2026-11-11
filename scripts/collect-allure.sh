#!/usr/bin/env bash
# Copy the Allure results written by the test pods (Minikube node hostPath) to the host.
#
#   collect-allure.sh [DEST]     DEST defaults to ./allure-results (both suites are merged into it)
#
# Needs the Minikube Docker driver (the node is the container named after the profile).
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

DEST="${1:-$ROOT/allure-results}"
mkdir -p "$DEST"
docker inspect "$PROFILE" >/dev/null 2>&1 || die "Minikube node container '$PROFILE' not found (Docker driver required)"

docker exec "$PROFILE" sh -c 'test -d /tmp/hc-allure || exit 3; tar -C /tmp/hc-allure -cf - .' | tar -C "$DEST" -xf - \
  || die "no results in the node at /tmp/hc-allure (did the test pods run?)"
# Results of each suite sit in a subdirectory; flatten them into one results directory.
find "$DEST" -mindepth 2 -type f -exec mv -n {} "$DEST"/ \;
find "$DEST" -mindepth 1 -type d -empty -delete
echo "Collected $(find "$DEST" -type f | wc -l | tr -d ' ') Allure result files into $DEST"
