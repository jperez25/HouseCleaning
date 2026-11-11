#!/usr/bin/env bash
# Deploy and test the HouseCleaning stack on Minikube (Docker driver).
#
#   deploy.sh [all]   ensure Docker + Minikube are running (start them if not), then db, app, tests
#   deploy.sh docker | minikube     just the infrastructure
#   deploy.sh db                    PostgreSQL         (helm release charts/house-cleaning-db)
#   deploy.sh app                   build image, load into Minikube, helm release charts/house-cleaning-app
#   deploy.sh iTest                 integration tests in a pod (helm test, charts/house-cleaning-integration-tests)
#   deploy.sh bTest                 BDD tests in a pod         (helm test, charts/house-cleaning-bdd-tests)
#   deploy.sh tests                 iTest, then bTest (stops at the first failing suite)
#
# HC_ENV=minikube (default) | minikube-local | dev   picks charts/*/values-<env>.yaml (see charts/README.md)
# Test charts also get report/mail settings from integration-tests/src/test/resources/configs (see its README.md)
# Extra overrides: see lib.sh
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

need helm

deploy_db() {
  log "Deploying PostgreSQL ($HC_ENV)"
  require_cluster
  helm_release house-cleaning-db --wait --timeout 3m
  echo "DB ready: house-cleaning-db.$NS.svc.cluster.local:5432 (host access: scripts/port-forward.sh db)"
}

deploy_app() {
  log "Deploying app ($HC_ENV)"
  require_cluster
  helm status house-cleaning-db -n "$NS" >/dev/null 2>&1 || die "database not deployed. Run scripts/deploy.sh db first."
  local extra=()
  if is_minikube_env; then
    build_image house-cleaning-app house-cleaning-app/Dockerfile
    extra=(--set-string "podAnnotations.deployedAt=$(date +%s)")   # same :local tag: force the pods to roll
  fi
  helm_release house-cleaning-app --wait --timeout 5m "${extra[@]}"
  echo "App release done (house-cleaning-app.$NS.svc.cluster.local:8080; host access: scripts/port-forward.sh app)"
}

# run_suite CHART DOCKERFILE: build + install the test chart, then run it as a Helm test (pod logs are streamed).
run_suite() {
  local chart="$1" dockerfile="$2"
  log "Running $chart ($HC_ENV)"
  require_cluster
  if kubectl -n "$NS" get deployment house-cleaning-app >/dev/null 2>&1; then
    kubectl -n "$NS" rollout status deployment/house-cleaning-app --timeout=120s
  else
    echo "NOTE: no app deployment in the cluster (expected with HC_ENV=minikube-local: the app must be running on the host)."
  fi
  build_image "$chart" "$dockerfile"
  CONFIG_ARGS=()
  [[ "$chart" == house-cleaning-integration-tests ]] && load_test_config   # only that suite sends the report email
  helm_release "$chart" "${CONFIG_ARGS[@]}"
  helm test "$chart" -n "$NS" --logs --timeout 15m
}

itest() { run_suite house-cleaning-integration-tests house-cleaning-integration-tests/Dockerfile; }
btest() { run_suite house-cleaning-bdd-tests house-cleaning-bdd-tests/Dockerfile; }

case "${1:-all}" in
  all)      ensure_docker; ensure_minikube; deploy_db; deploy_app; itest; btest ;;
  docker)   ensure_docker ;;
  minikube) ensure_docker; ensure_minikube ;;
  db)       deploy_db ;;
  app)      deploy_app ;;
  iTest)    itest ;;
  bTest)    btest ;;
  tests)    itest; btest ;;
  *)        sed -n '2,15p' "${BASH_SOURCE[0]}"; exit 1 ;;
esac
