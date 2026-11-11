# Environment configuration

Everything for the post-run summary email lives in the `reporting` package (`src/test/java/.../reporting`) and
this folder (`configs`). Secured files are kept out of Docker images by `.dockerignore`. Only the integration tests send the email.

| File | Committed | Content |
|---|---|---|
| `environment.<env>.json` | yes | Non-secret settings (`sendEmail`, `reportBaseUrl`, `mail.host/port/starttls/from/to`) |
| `secured.<env>.json` | **no (gitignored)** | Secrets (`mail.username`, `mail.password`) |
| `secured.<env>.example.json` | yes | Template: copy to `secured.<env>.json` and fill in |

`<env>` is `HC_ENV` (default `minikube`).

**Maven runs on the host** (`./mvnw test`): the reporter reads both files (secured wins). Precedence per setting:
system property (`-Dmail.host=...`) > environment variable (`SMTP_HOST`...) > `secured` file > `environment` file.
Select the environment with `-Denv=dev` or `HC_ENV=dev`; this directory is found by walking up from the working
directory, or set `-Dconfig.dir=` / `CONFIG_DIR`.

**Cluster runs** (`scripts/deploy.sh`, needs `jq`): the files are not in the images. `deploy.sh` turns
`environment.<env>.json` into Helm `--set` values and `secured.<env>.json` into the Kubernetes Secret `smtp-creds`.

Turn the email on with `"sendEmail": true` (or `-DsendEmail=true`).
