# WeChat diagnostics map

Read during Phase 1 to find where to observe each boundary. Verify every path and command against the current repository before relying on it. Never print `.env` values, JWTs, refresh tokens, object keys, or presigned URLs.

## Backend (Spring Boot, `wechatbackend/`)

- **Error mapping:** `common/exception/GlobalExceptionHandler`, `ApiException`, `ErrorResponse`. A 4xx or 5xx response body is shaped here. Check which handler matched before assuming the cause.
- **Security:**
  - `SecurityConfig` and the JWT filter, for 401/403 on REST.
  - The STOMP channel interceptor and destination policy, for a rejected `CONNECT`, `SUBSCRIBE`, or `SEND`.
  - Distinguish an authentication failure (identity) from an authorization failure (membership or role).
- **Transactions and events:**
  - Data that is written but never delivered usually means after-commit publication did not run, or the Redis bridge dropped the event.
  - Data that is delivered but missing on refresh usually means it was published before commit, or the transaction rolled back.
- **Focused tests:** `cd wechatbackend && ./mvnw -q test -Dtest=ClassName#method`. Integration profiles: `-Ppostgres-integration`, `-Predis-integration`, `-Ps3-integration`. These need Docker; mark them `BLOCKED` if it is unavailable.
- **Runtime state:** the read-only Actuator endpoints routed by the gateway: `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`.
  - Readiness DOWN with liveness UP means a dependency problem, such as the database, Redis, or a Flyway failure at startup.
- **JVM hangs and slowness:** take a thread dump of the backend container (`jcmd 1 Thread.print`, if `jcmd` exists in the image) to find blocked or pool-exhausted threads before guessing.

## PostgreSQL

- **Flyway:** a startup failure names the version. Inspect `flyway_schema_history` read-only, and never edit an applied migration to "fix" it.
- **Locks and slow queries:** `pg_stat_activity` (state, `wait_event`, query start) and `pg_locks` joined to `pg_stat_activity`, to find blockers.
- **H2:** a green H2 test does not reproduce PostgreSQL-only behavior (trigram search, keyset pagination, constraints).

## Redis and realtime

- **Pub/Sub is transient.** An event published while a replica was disconnected is lost by design. Check whether the client resync path (REST reload) ran.
- **Is the event published?** On an isolated, non-shared Redis only, `SUBSCRIBE` to the configured `REALTIME_REDIS_CHANNEL` and reproduce. Never `MONITOR` a shared or production Redis.
- **Which replica?** Identify which replica handled the write and which held the subscriber. A single-replica reproduction does not explain a cross-replica bug.

## Gateway and runtime (`docker/`)

- **Logs:** `docker compose --env-file .env -f docker/docker-compose.yml logs --tail=200 <service>` for `gateway`, `backend`, `frontend`, `postgres`, `redis`. Do not run `config` output into reports, because it resolves secrets.
- **Container health:** `docker compose ... ps` for health status. A 502 or 504 at the gateway usually means the backend is unready or the upstream name or port is wrong. Check `docker/gateway/nginx.conf`.
- **WebSocket:** the `/ws` location needs the `Upgrade` and `Connection` headers. A handshake that succeeds and then drops may come from proxy timeouts or origin checks (`WEBSOCKET_ALLOWED_ORIGINS`).

## Frontend (`wechatfrontend/`)

- **REST:** `src/services/api` (axios client, token refresh). For a 401 loop, check refresh-token rotation and retry guards.
- **Realtime:** `src/services/websocket` (STOMP client, reconnect, resubscribe). For duplicate or missing messages, check deduplication keys and the resync after reconnect.
- **State:** Zustand stores under `src/store`. For stale UI, check selector subscriptions and whether updates replace or mutate state.
- **Focused tests:** `cd wechatfrontend && npx vitest run <file>`.
