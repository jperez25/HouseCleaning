#!/usr/bin/env bash
# Tear the stack down: helm uninstall for each release, then the infrastructure.
#
#   teardown.sh app                 helm uninstall the app
#   teardown.sh tests               helm uninstall both test releases (and their pods)
#   teardown.sh db [--purge]        remove PostgreSQL; --purge also deletes its data volume
#   teardown.sh minikube [--purge]  stop the cluster; --purge deletes it (and all its data)
#   teardown.sh docker              quit Docker
#   teardown.sh all [--purge]       tests, app, db, minikube, docker (--purge applies to db and minikube)
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"
need helm

PURGE=false
[[ "${2:-}" == "--purge" ]] && PURGE=true

uninstall() { helm uninstall "$1" -n "$NS" --ignore-not-found 2>&1 | sed 's/^/  /'; }

down_app() {
  log "Removing app"
  uninstall house-cleaning-app
}

down_tests() {
  log "Removing test releases"
  uninstall house-cleaning-integration-tests
  uninstall house-cleaning-bdd-tests
  kubectl -n "$NS" delete pod -l 'app in (house-cleaning-integration-tests,house-cleaning-bdd-tests)' --ignore-not-found   # hook pods outlive the release
}

down_db() {
  log "Removing PostgreSQL"
  uninstall house-cleaning-db
  if $PURGE; then kubectl -n "$NS" delete pvc -l app=house-cleaning-db --ignore-not-found; fi   # StatefulSet PVCs survive uninstall
}

down_minikube() {
  log "Stopping Minikube"
  if $PURGE; then minikube delete -p "$PROFILE"; docker volume rm "$PROFILE" >/dev/null 2>&1 || true; else minikube stop -p "$PROFILE"; fi
}

down_docker() {
  log "Quitting Docker"
  case "$(uname -s)" in
    Darwin) osascript -e 'quit app "Docker"' ;;
    Linux)  sudo systemctl stop docker ;;
    *)      die "stop Docker manually on this OS" ;;
  esac
}

pkill -f "kubectl.*port-forward" 2>/dev/null || true   # forwards die with their targets anyway

case "${1:-}" in
  app)      require_cluster; down_app ;;
  tests)    require_cluster; down_tests ;;
  db)       require_cluster; down_db ;;
  minikube) need minikube; down_minikube ;;
  docker)   down_docker ;;
  all)
    if minikube_up; then
      kubectl config use-context "$PROFILE" >/dev/null
      down_tests; down_app; down_db
      if $PURGE; then kubectl delete namespace "$NS" --ignore-not-found; fi   # a namespace delete also drops the DB volume
      down_minikube
    fi
    if docker_up; then down_docker; fi ;;
  *)        sed -n '2,11p' "${BASH_SOURCE[0]}"; exit 1 ;;
esac
