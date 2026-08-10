# DevKit server infrastructure

This Compose stack owns only DevKit's Keycloak and PostgreSQL state. It is
separate from the local all-in-one `docker-compose.yml` and does not start the
Java API or Spring Cloud Gateway; deploy those workloads to Kubernetes later.
It exports Keycloak traces and Keycloak/PostgreSQL metrics to the existing
SigNoz Docker deployment.

## First deployment

1. Create `auth.synx.io.vn` in Cloudflare and route it through the trusted
   HTTPS ingress/reverse proxy to port `8181` on this host. Its public issuer
   URL is fixed at `https://auth.synx.io.vn` in the Compose file.
2. Create a host-only Infisical Machine Identity with read access limited to
   the `dev` environment and root (`/`) secret path. Do not pass its
   credentials to a Compose service.
3. Add these values to that Infisical path: `KEYCLOAK_DB_PASSWORD`,
   `KEYCLOAK_ADMIN_PASSWORD`, `DEVKIT_DB_ADMIN_PASSWORD`, and
   `DEVKIT_DB_APP_PASSWORD`, and `DEVKIT_REDIS_PASSWORD`. The Redis value is
   used only when the bundled Redis profile is enabled, but Compose validates
   all service definitions before it applies profiles.
4. Authenticate the Infisical CLI on the host with that Machine Identity, then
   start the required stateful services:

   ```sh
   infisical run --env=dev --path=/ -- \
   docker compose \
     -f deploy/server/docker-compose.infrastructure.yml \
     up -d --build
   ```

   The existing SigNoz stack must already be running and own Docker network
   `signoz-network`. This stack fails instead of silently starting without
   monitoring when that network does not exist.

5. For an existing Keycloak realm, apply the security baseline after the
   container is healthy:

   ```sh
   KEYCLOAK_CONTAINER_NAME=devkit-infra-keycloak \
   KEYCLOAK_ADMIN_SERVER=http://127.0.0.1:8181 \
   ./scripts/apply-keycloak-security.sh
   ```

The first start imports `keycloak/realm-devkit.json`. Existing realms are not
overwritten by Keycloak import; use the helper above to apply policy changes.

## SigNoz telemetry

`devkit-infra-otel-collector` is the only DevKit container connected to the
external `signoz-network`. It forwards OTLP to `signoz-ingester:4317` and:

- receives Keycloak traces over the private network;
- scrapes Keycloak metrics on its private management port `9000`; and
- scrapes separate internal exporters for the Keycloak and DevKit PostgreSQL
  instances.

No database metrics endpoint is published to the host. The optional bundled
Redis remains disabled by default. When its profile is enabled, its own
exporter and collector start alongside it and forward Redis metrics to SigNoz.
Use a dedicated Redis ACL when the application begins using the cache.

After deployment, verify the collector has an egress path and no scrape errors:

```sh
infisical run --projectId="$INFISICAL_PROJECT_ID" --env=dev --path=/ -- \
docker compose -f deploy/server/docker-compose.infrastructure.yml \
  logs --tail=100 devkit-otel-collector
```

## Redis choice

The gateway uses Redis for distributed rate limiting on `GET /v1/desktop/config`
only. Configure `DEVKIT_REDIS_HOST`, `DEVKIT_REDIS_PORT`, and
`DEVKIT_REDIS_PASSWORD` (from Infisical in production). When Redis is unavailable
the gateway fails open for that endpoint and logs a warning; other routes keep
the in-memory per-instance limiter.

The sync API backend does not currently consume Redis, so this stack does not start
a duplicate Redis by default. Reuse the server's existing Redis only after
creating a dedicated ACL/user or logical database and passing its TLS-enabled
URL to the future Kubernetes deployment.

To run an isolated DevKit cache instead, start the optional profile:

```sh
infisical run --env=dev --path=/ -- \
docker compose \
  -f deploy/server/docker-compose.infrastructure.yml \
  --profile bundled-redis \
  up -d --build
```

It has no host port and no persistence because it must never become a
correctness dependency for sync. Redis 8 uses a source-available tri-license;
review the [official image license notice](https://hub.docker.com/_/redis)
before enabling it.

## Kubernetes connectivity

Redis keeps its loopback-only/private deployment posture. Keycloak and the
DevKit PostgreSQL port are narrow exceptions: K3s must reach TCP `8181` for
`auth.synx.io.vn` and TCP `5433` for the Kubernetes sync backend. Both are
published by Docker but restricted to Pod CIDR in `DOCKER-USER`; neither is
Internet-facing.

Docker sends published-port traffic through its own forwarding rules before
UFW's normal `INPUT` rules, so a UFW allow rule is not a sufficient boundary.
Install the provided idempotent host rule before starting the stack:

```sh
sudo install -m 0750 scripts/restrict-keycloak-ingress.sh \
  /usr/local/sbin/restrict-devkit-keycloak-ingress
sudo install -m 0644 systemd/devkit-keycloak-firewall.service \
  /etc/systemd/system/devkit-keycloak-firewall.service
sudo systemctl daemon-reload
sudo systemctl enable --now devkit-keycloak-firewall.service
```

The service adds `DOCKER-USER` rules that allow source `10.42.0.0/16` and drop
all other traffic originally addressed to TCP `8181`. Do not add a broad UFW
rule for `8181`; external traffic must reach Keycloak only through Traefik. TLS
terminates at Traefik and it forwards trusted `X-Forwarded-*` headers to the
HTTP Keycloak backend.

Install the analogous PostgreSQL restriction before deploying the backend:

```sh
sudo install -m 0750 scripts/restrict-devkit-db-ingress.sh \
  /usr/local/sbin/restrict-devkit-db-ingress
sudo install -m 0644 systemd/devkit-db-firewall.service \
  /etc/systemd/system/devkit-db-firewall.service
sudo systemctl daemon-reload
sudo systemctl enable --now devkit-db-firewall.service
```

The rule accepts only `10.42.0.0/16` traffic originally addressed to `5433`.
Verify it before the database port is used by application Pods:

```sh
sudo iptables -S DOCKER-USER | grep -- '--ctorigdstport 5433'
```
