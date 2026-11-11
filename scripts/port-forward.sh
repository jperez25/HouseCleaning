#!/usr/bin/env bash
# Forward in-cluster services to the host. Runs until Ctrl+C.
#
#   port-forward.sh app      localhost:8080 -> house-cleaning-app:8080
#   port-forward.sh db       localhost:5432 -> house-cleaning-db:5432
#   port-forward.sh all      both
#
# Overrides: LOCAL_APP_PORT (8080), LOCAL_DB_PORT (5432)
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

APP_PORT="${LOCAL_APP_PORT:-8080}"
DB_PORT="${LOCAL_DB_PORT:-5432}"

require_cluster
pids=()
trap 'kill "${pids[@]}" 2>/dev/null || true' EXIT INT TERM

forward() { # forward SERVICE LOCAL REMOTE
  echo "Forwarding localhost:$2 -> $1:$3"
  kubectl -n "$NS" port-forward "svc/$1" "$2:$3" &
  pids+=($!)
}

case "${1:-}" in
  app) forward house-cleaning-app "$APP_PORT" 8080 ;;
  db)  forward house-cleaning-db "$DB_PORT" 5432 ;;
  all) forward house-cleaning-app "$APP_PORT" 8080; forward house-cleaning-db "$DB_PORT" 5432 ;;
  *)   sed -n '2,8p' "${BASH_SOURCE[0]}"; exit 1 ;;
esac
echo "(Ctrl+C to stop)"
wait
