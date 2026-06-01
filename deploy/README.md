# Dev Deployment

The dev deployment uses `src/main/resources/backend-security` as the source of
truth for application secrets. Do not duplicate app, DB, OAuth, JWT, or mail
settings as GitHub Actions secrets.

GitHub Actions checks out the private `backend-security` submodule, uploads
`application-security.yml` to the dev server, and mounts it into the app
container as read-only configuration.

## GitHub Repository Secrets

Required deployment secrets:

```text
DEV_SERVER_HOST
DEV_SERVER_USER
DEV_SERVER_SSH_KEY
DEV_DEPLOY_PATH
GHCR_USERNAME
GHCR_TOKEN
BACKEND_SECURITY_TOKEN
```

`BACKEND_SECURITY_TOKEN` should be a fine-grained PAT that can read only the
`TEAM-ChwieolUp/backend-security` repository contents.

## Dev Server Prerequisites

The dev server must have Docker Engine and the Docker Compose plugin installed
before the deployment workflow runs.

Ubuntu setup:

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

Allow the deployment SSH user to run Docker:

```bash
sudo usermod -aG docker <DEV_SERVER_USER>
```

After changing group membership, disconnect and reconnect SSH, then verify:

```bash
docker --version
docker compose version
mkdir -p /home/ubuntu/cheerup/backend
```

## backend-security Requirements

`application-security.yml` must include a `dev` profile document with:

```yaml
spring:
  config:
    activate:
      on-profile: dev
  datasource:
    url: jdbc:mysql://mysql:3306/cheerup?serverTimezone=UTC&characterEncoding=UTF-8
    username: ...
    password: ...

deploy:
  app:
    port: 8080
  mysql:
    database: cheerup
    root-password: ...
```

The workflow derives the compose-only `.env.compose` file from this dev
document during deployment. The server copy is a generated deployment artifact
and should not be edited manually.
