# Test layers

The backend has deliberately separate test layers:

1. `mvn test` runs unit, service, fast application, and focused H2 tests. It does not require Docker. H2 PostgreSQL compatibility mode is a fast-test convenience, not proof of production PostgreSQL compatibility.
2. `mvn verify -Ppostgres-integration` runs the fast suite and the `*IT` suite against an ephemeral `postgres:17` Testcontainer. Spring Boot obtains the random JDBC endpoint and credentials through `@ServiceConnection`; Flyway builds the database from empty state through the latest migration.
3. `RUN_DB_PERF_TESTS=true mvn verify -Ppostgres-integration` additionally runs opt-in `EXPLAIN (ANALYZE, BUFFERS)` checks for representative keyset and trigram-search plans.
4. `mvn verify -Pminio-integration` runs the object-storage integration class against a pinned MinIO Testcontainer. It covers presigned PUT, HEAD, GET, delete, health and unsafe-key rejection.

## Prerequisites and outcomes

Pulling the production-major image in advance is optional but useful:

```bash
docker pull postgres:17
```

Testcontainers requires a reachable Docker-compatible daemon. If it is unavailable, JUnit reports the PostgreSQL integration class as skipped/not runnable; that result must not be reported as a PostgreSQL pass. The container uses no fixed host port, host path, permanent volume, or application database credential and is stopped by Testcontainers after the suite.

The PostgreSQL layer covers fresh Flyway V1-through-latest migration, `pg_trgm` and GIN/partial indexes, JSONB, database uniqueness constraints and controlled races, UTC `timestamptz`, keyset pagination, search visibility/deduplication, batch unread aggregation, notification batches, auth/token persistence, and bounded retention cleanup.

The former `RUN_DB_TESTS` context-load gate depended on an externally configured database and has been removed. Fast auth, CORS, rate-limit, and actuator API tests still use their focused H2 fixtures; database-critical compatibility claims come only from the PostgreSQL profile.

Performance checks are opt-in because planner tests create representative data and execute the queries. They assert that the relevant indexes are available to PostgreSQL, but intentionally do not impose a latency threshold without a maintained benchmark baseline.

## Attachment runtime acceptance

The MinIO integration profile verifies storage semantics in isolation. The R16 cross-instance proof must still run against Compose:

```bash
docker compose --env-file .env -f docker/docker-compose.yml config -q
mvn test
mvn verify -Pminio-integration
docker compose --env-file .env -f docker/docker-compose.yml up -d --scale backend=2
```

Create a real account/session, upload an image through `POST /api/attachments/presigned-upload`, PUT to the returned URL, finalize it, and send a message reference. Then stop one backend container and open the attachment from the other replica through `GET /api/attachments/{id}/download-url`. The expected result is a successful download from shared MinIO, not a local filesystem lookup. Also verify a non-member receives `404` and that invalid magic bytes never become `READY`.
