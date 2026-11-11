# Shared helpers for deploy.sh / port-forward.sh / teardown.sh. Source it; do not run it.
# Overrides: MINIKUBE_PROFILE, MINIKUBE_CPUS, MINIKUBE_MEMORY, DOCKER_WAIT_SECONDS
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROFILE="${MINIKUBE_PROFILE:-minikube}"
CPUS="${MINIKUBE_CPUS:-2}"
MEMORY="${MINIKUBE_MEMORY:-4096}"
DOCKER_WAIT="${DOCKER_WAIT_SECONDS:-120}"
NS=house-cleaning
APP_IMAGE=house-cleaning-app:local

log()  { printf '\n==> %s\n' "$*"; }
die()  { echo "ERROR: $*" >&2; exit 1; }
need() { for t in "$@"; do command -v "$t" >/dev/null || die "$t is not installed"; done; }

docker_up()   { docker info >/dev/null 2>&1; }
minikube_up() { minikube status -p "$PROFILE" >/dev/null 2>&1; }

ensure_docker() {
  need docker
  if docker_up; then echo "Docker is already running."; return; fi
  echo "Docker is not running, starting it..."
  local os; os="$(uname -s)"
  case "$os" in
    Darwin|Linux) ;;
    *) die "start Docker manually on this OS" ;;
  esac
  local i
  for ((i = 0; i < DOCKER_WAIT; i += 2)); do
    docker_up && break
    # (Re)issue the start every 20s: a launch while Docker Desktop is still quitting is silently ignored.
    if ((i % 20 == 0)); then
      if [[ "$os" == Darwin ]]; then open -a Docker; else sudo systemctl start docker; fi
    fi
    sleep 2
  done
  docker_up || die "Docker did not become ready within ${DOCKER_WAIT}s"
  echo "Docker is running."
}

ensure_minikube() {
  need minikube kubectl
  if minikube_up; then
    echo "Minikube profile '$PROFILE' is already running."
  else
    echo "Minikube profile '$PROFILE' is not running, starting it..."
    minikube start -p "$PROFILE" --driver=docker --cpus="$CPUS" --memory="$MEMORY"
  fi
  kubectl config use-context "$PROFILE" >/dev/null
}

# For targets that need an existing cluster but should not start one.
require_cluster() {
  need kubectl minikube
  minikube_up || die "Minikube is not running. Run scripts/deploy.sh (or: scripts/deploy.sh minikube) first."
  kubectl config use-context "$PROFILE" >/dev/null
}

# wait_http URL SECONDS
wait_http() {
  local url="$1" secs="$2" i
  for ((i = 0; i < secs; i += 2)); do curl -sf "$url" >/dev/null 2>&1 && return 0; sleep 2; done
  return 1
}

# ---- Helm ----------------------------------------------------------------------------------
# HC_ENV selects charts/<chart>/values-$HC_ENV.yaml (on top of the chart's values.yaml):
#   minikube (default) | minikube-local (app runs from IntelliJ) | dev (dummy CI/CD values)
HC_ENV="${HC_ENV:-minikube}"
CHARTS="$ROOT/charts"
is_minikube_env() { [[ "$HC_ENV" == minikube* ]]; }

# helm_release CHART [extra helm args]: install or upgrade, release name = chart name.
helm_release() {
  local chart="$1"; shift
  local vals="$CHARTS/$chart/values-$HC_ENV.yaml"
  [[ -f "$vals" ]] || die "no values file for HC_ENV=$HC_ENV: $vals"
  helm upgrade --install "$chart" "$CHARTS/$chart" -n "$NS" --create-namespace -f "$vals" "$@"
}

# build_image IMAGE DOCKERFILE: build with host Docker and (Minikube envs only) load into the cluster, no registry.
# Other environments are expected to get their images from a registry via the pipeline.
build_image() {
  local image="$1" dockerfile="$2"
  if ! is_minikube_env; then echo "HC_ENV=$HC_ENV: skipping local image build ($image comes from the registry)"; return; fi
  docker_up || die "Docker is not running. Run scripts/deploy.sh docker first."
  docker build -f "$ROOT/$dockerfile" -t "$image:local" "$ROOT"
  minikube -p "$PROFILE" image load --overwrite=true "$image:local"
}

# ---- Environment config (house-cleaning-integration-tests/src/test/resources/configs/{environment,secured}.<env>.json) -----------------------
# load_test_config sets CONFIG_ARGS: a generated Helm values file (-f) with report.* and mail.* from the JSON file
# (typed, so booleans and numbers stay booleans and numbers), plus the Kubernetes Secret smtp-creds created from the
# secured file. No-op when the files are missing; needs jq otherwise.
CONFIG_ARGS=()
load_test_config() {
  CONFIG_ARGS=()
  local cfg="$ROOT/house-cleaning-integration-tests/src/test/resources/configs"
  local env_file="$cfg/environment.$HC_ENV.json" sec_file="$cfg/secured.$HC_ENV.json"
  if [[ -f "$env_file" ]]; then
    command -v jq >/dev/null || die "jq is required to read $env_file"
    local vals; vals="$(mktemp "${TMPDIR:-/tmp}/hc-values.XXXXXX")"
    jq '{report: {sendEmail: .sendEmail, baseUrl: .reportBaseUrl},
         mail: (.mail // {} | {host, port, starttls, from, to})}
        | del(..|select(. == null))' "$env_file" > "$vals"   # JSON is valid YAML
    CONFIG_ARGS+=(-f "$vals")
  fi
  if [[ -f "$sec_file" ]]; then
    command -v jq >/dev/null || die "jq is required to read $sec_file"
    local user pass
    user="$(jq -r '.mail.username // empty' "$sec_file")"; pass="$(jq -r '.mail.password // empty' "$sec_file")"
    if [[ -n "$pass" ]]; then
      kubectl create namespace "$NS" --dry-run=client -o yaml | kubectl apply -f - >/dev/null
      kubectl -n "$NS" create secret generic smtp-creds --from-literal=username="$user" --from-literal=password="$pass" \
        --dry-run=client -o yaml | kubectl apply -f - >/dev/null
      CONFIG_ARGS+=(--set-string mail.credentialsSecret=smtp-creds)
    fi
  fi
}
