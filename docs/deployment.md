# Docker Compose deployment foundation

This stack provides a reproducible single-instance deployment foundation:

```text
Browser / operator
        |
        v
Gateway Nginx :${HTTP_PORT:-8080}
   |-- / ------------------------> Frontend Nginx :8080
   |-- /api/* -------------------+
   |-- /ws ----------------------+--> Spring Boot :8080
   |-- approved /actuator paths -+          |
                                             v
                                      PostgreSQL :5432
```

The Compose services are `gateway`, `frontend`, `backend`, and `postgres`. Only Gateway publishes a host port. Frontend and Backend are reachable only on the private app network; PostgreSQL is reachable only on the private database network.

## Prerequisites

- Docker Engine with Docker Compose v2
- OpenSSL for generating local secrets

## Configure the environment

From the repository root:

```bash
cp .env.example .env
```

Generate independent secrets rather than reusing credentials:

```bash
openssl rand -base64 64
openssl rand -hex 32
```

Edit `.env` and set every blank required value. Compose rejects missing or empty required values before creating containers.

| Variable | Required | Purpose |
| --- | --- | --- |
| `HTTP_PORT` | No | Host port published by Gateway; defaults to `8080`. |
| `DB_NAME` | Yes | PostgreSQL database and JDBC database name. |
| `DB_USERNAME` | Yes | PostgreSQL and JDBC username. |
| `DB_PASSWORD` | Yes | PostgreSQL and JDBC password. |
| `JWT_SECRET` | Yes | JWT HMAC secret; must be random and at least 32 characters. |
| `JWT_ISSUER` | No | JWT issuer; defaults to `wechat`. |
| `MAIL_HOST` | Yes | Production SMTP host. |
| `MAIL_PORT` | No | Production SMTP port; defaults to `587`. |
| `MAIL_USERNAME` | Yes | SMTP username. |
| `MAIL_PASSWORD` | Yes | SMTP password. |
| `MAIL_FROM` | Yes | Sender address for authentication email. |
| `EMAIL_VERIFICATION_URL` | Yes | Public frontend verification URL ending in `token=`. |
| `PASSWORD_RESET_URL` | Yes | Public frontend reset URL ending in `token=`. |
| `CORS_ALLOWED_ORIGINS` | Yes | Comma-separated exact REST browser origins; never use `*`. |
| `WEBSOCKET_ALLOWED_ORIGINS` | Yes | Comma-separated exact WebSocket origins. |
| `TRUSTED_PROXY_CIDRS` | Yes | Tomcat trusted-proxy expression. The example matches only the fixed Compose Nginx address. |

Cleanup retention, batch, run-cap, and schedule variables are listed in `.env.example`. See `docs/data-retention.md` before changing them, especially the audit-log policy.

`DB_HOST` and `DB_PORT` are intentionally not configurable in this Compose stack: service discovery fixes them to `postgres:5432`. The Compose file constructs the application's `DATABASE_URL` from those internal values.

The checked-in localhost origins and callback URLs are only runnable defaults for this local Compose topology. Replace them with the real HTTPS frontend origin before an internet-facing deployment.

## Validate and start

After filling `.env`:

Because the Compose file lives under `docker/`, pass the root environment file explicitly when invoking Compose from the repository root:

```bash
docker compose --env-file .env -f docker/docker-compose.yml config
docker compose --env-file .env -f docker/docker-compose.yml build
docker compose --env-file .env -f docker/docker-compose.yml up -d
docker compose --env-file .env -f docker/docker-compose.yml ps
```

Startup ordering is:

```text
PostgreSQL pg_isready
        v
Spring Boot starts, runs Flyway, then readiness reports UP
        v
Frontend serves static files and Gateway starts routing traffic
```

`depends_on` only controls startup ordering. Runtime readiness remains the Actuator endpoint; Compose does not provide continuous traffic draining if a previously healthy backend later becomes unready.

## Verify health and metrics

With the default host port:

```bash
curl -fsS http://localhost:8080/actuator/health
curl -fsS http://localhost:8080/health
curl -fsS http://localhost:8080/actuator/health/liveness
curl -fsS http://localhost:8080/actuator/health/readiness
curl -fsS http://localhost:8080/actuator/prometheus
```

Liveness checks only application process state. Readiness also checks the datasource, so PostgreSQL failure removes the backend from the healthy startup chain without turning the liveness probe into a database restart loop.

Gateway exposes `/health` from the independent Frontend service and only the approved backend health/Prometheus paths. Other `/actuator/*` paths return `404`. Restrict `/actuator/prometheus` at the production load balancer or firewall because this foundation does not create a separate management network.

## REST smoke test

No user is seeded automatically. A login attempt for a nonexistent account should return the application's `401` JSON response; this still verifies Nginx routing, Spring Security, and a database-backed user lookup:

```bash
curl -i \
  -H 'Content-Type: application/json' \
  -d '{"identifier":"deployment-smoke-user","password":"not-a-real-password"}' \
  http://localhost:8080/api/auth/login
```

## WebSocket proxy smoke test

The backend endpoint is `/ws` and uses STOMP authentication. After obtaining a valid access token from a real account, a WebSocket client such as `wscat` can connect through Nginx:

```bash
wscat -c ws://localhost:8080/ws -H 'Origin: http://localhost:8080'
```

Send a STOMP `CONNECT` frame containing `authorization:Bearer <access-token>`. The proxy uses HTTP/1.1 upgrade headers and a one-hour idle read timeout. This verifies only the current single-instance broker; it is not a distributed WebSocket test.

## Logs

```bash
docker compose --env-file .env -f docker/docker-compose.yml logs -f backend
docker compose --env-file .env -f docker/docker-compose.yml logs -f gateway frontend
docker compose --env-file .env -f docker/docker-compose.yml logs -f postgres
```

The Nginx access format logs `$uri` without query parameters so reset or verification tokens in browser URLs are not written to access logs.

## Request limits and timeouts

- Nginx `client_max_body_size` is `60m`, matching Spring's `60MB` maximum multipart request.
- REST connect/read/send timeouts are `5s`/`60s`/`60s`.
- WebSocket read timeout is `3600s`; buffering is disabled for upgraded connections.
- No fixed JVM heap is configured. Java 21 uses container-aware memory sizing.

## Forwarded client addresses

Nginx overwrites inbound `X-Forwarded-For` with its direct `$remote_addr` instead of appending an untrusted browser-supplied chain. The backend production profile trusts only the Nginx address specified by `TRUSTED_PROXY_CIDRS` before `RateLimitFilter` reads the effective remote address.

If the edge subnet or Nginx address changes, update both Compose networking and `TRUSTED_PROXY_CIDRS`. If a real load balancer is added in front of Nginx, its trust boundary must be designed explicitly rather than accepting arbitrary forwarded headers.

## Persistent data

- `postgres_data` contains PostgreSQL data.
- `uploads_data` is mounted at `/app/uploads` for the current local filesystem storage implementation.

Back up these volumes before upgrades. `docker compose --env-file .env -f docker/docker-compose.yml down` preserves them; the `-v` variant deletes them and should only be used when intentionally discarding data.

## Shutdown

```bash
docker compose --env-file .env -f docker/docker-compose.yml down
```

## Security notes

- Never commit `.env`; only `.env.example` belongs in source control.
- Secrets are passed as runtime environment variables and are not copied into either image.
- The backend runtime image contains only the JRE and application JAR and runs as UID/GID `10001`, with all Linux capabilities dropped and a read-only root filesystem.
- PostgreSQL has no host port mapping.
- The frontend image contains only the compiled Vite assets and Nginx runtime; the gateway image contains only its reverse-proxy configuration.
- No certificate or private key is included. Terminate production TLS at a controlled ingress/load balancer or extend Nginx with externally managed certificates.

## Current limitations

This is a deployment foundation, not a claim that the system is fully production-ready:

- The Spring simple WebSocket broker is single-instance and not distributed.
- Local upload storage is single-instance; multi-instance deployment requires MinIO/S3 work from R16.
- The in-memory rate limiter is node-local; Redis rate limiting is not implemented.
- Redis, MinIO, RabbitMQ, and a STOMP broker relay are not included.
- The frontend is independently built and deployed from `wechatfrontend`; Playwright runtime tests still require real credentials and a running stack.
- Production TLS, real domains, certificate management, and edge access controls are not configured.
- Prometheus server, alerting, Grafana, and distributed tracing are not deployed.
- CI/CD is not implemented.
- Cleanup overlap protection is process-local; multi-instance scheduling requires distributed coordination or an external job runner.
