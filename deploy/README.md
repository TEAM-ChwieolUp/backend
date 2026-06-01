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
