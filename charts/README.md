# Helm charts

One chart per deployable module. Release name = chart name, namespace `house-cleaning`.

| Chart | Deploys | Image |
|---|---|---|
| `house-cleaning-db` | PostgreSQL 17 StatefulSet + Service + Secret + init ConfigMap (`files/*.sql`: settings, full schema) | `postgres:17-alpine` |
| `house-cleaning-app` | Spring Boot app Deployment + Service (profile `k8s`, health probes) | `house-cleaning-app:local` (`house-cleaning-app/Dockerfile`) |
| `house-cleaning-integration-tests` | REST Assured suite as a Helm **test hook** pod | `house-cleaning-integration-tests:local` |
| `house-cleaning-bdd-tests` | Cucumber + Karate suites as a Helm **test hook** pod | `house-cleaning-bdd-tests:local` |

Test charts install nothing by themselves; `helm test <release> --logs` runs the pod against `target.baseUrl` (exported as `TEST_BASE_URL`) and the pod's result is the exit code.

## Values files (in every chart)

| File | Purpose |
|---|---|
| `values.yaml` | Shared defaults, always applied |
| `values-minikube.yaml` | Everything (db, app, tests) in Minikube. **Default** |
| `values-minikube-local.yaml` | App runs from IntelliJ on the host: only the DB is deployed (`app.enabled=false`); test pods call `http://host.minikube.internal:8080` |
| `values-dev.yaml` | CI/CD "dev" environment. **Dummy values** (registry, hostnames, sizes): replace before use |

Pick one with `HC_ENV` (default `minikube`); `scripts/deploy.sh` layers `charts/<chart>/values-$HC_ENV.yaml` over `values.yaml`:

```
scripts/deploy.sh                      # all, HC_ENV=minikube
HC_ENV=minikube-local scripts/deploy.sh db      # DB only, then run the app from IntelliJ:
                                                #   profile k8s, env DB_URL=jdbc:postgresql://localhost:5432/housecleaning
scripts/port-forward.sh db                      #   (keep running)
HC_ENV=minikube-local scripts/deploy.sh tests   # tests in pods against the IntelliJ app on :8080
helm lint charts/house-cleaning-app -f charts/house-cleaning-app/values-dev.yaml
```

## Notes
- **Schema** lives in `charts/house-cleaning-db/files/02-schema.sql` (mirrors the JPA entities; the app only validates it). It runs once on an empty volume: after changing it run `scripts/teardown.sh db --purge` then `scripts/deploy.sh db`.
- **Credentials**: local defaults are in `charts/house-cleaning-db/values.yaml`. Other environments set `auth.existingSecret` (db) and `database.credentialsSecret` (app) to an out-of-band Secret.
- **Images**: on Minikube, `deploy.sh` builds with host Docker and `minikube image load`s them (no registry). For `dev` the pipeline is expected to push to the registry named in `values-dev.yaml`, so `deploy.sh` skips the local build.
- **Test pods** download any Maven artifacts not cached in the image at build time, so they need access to Maven Central (or a mirror).
