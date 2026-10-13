# WeChat Clone — Project Context

Version: `v1.0`
Last synchronized with repository source: `2026-10-13`
Source context: [shared ChatGPT conversation](https://chatgpt.com/share/6acc6394-d060-83ec-9568-1b8c7dbad3f6)

## Purpose

Đây là context dùng chung cho các prompt hỗ trợ project **WeChat Clone / Real-Time Web Chat System**. File này giúp agent không phải dựng lại mental model ở mỗi task, nhưng **không thay thế việc đọc source code**.

### Evidence boundary

- Nội dung lấy từ shared conversation là historical context, không phải bằng chứng runtime hiện tại.
- Source code, config, test output và runtime state trong repository ứng dụng luôn có ưu tiên cao hơn file này.
- Nếu context cũ mâu thuẫn với source hiện tại, agent phải báo mâu thuẫn và follow source hiện tại.
- Không được tuyên bố một R-item đã hoàn tất chỉ vì nó được liệt kê là “đã làm” trong file này.

## Project identity

Hệ thống là một real-time web chat có:

- frontend web;
- gateway/reverse proxy;
- Spring Boot backend;
- PostgreSQL với Flyway migrations;
- Redis cho realtime distribution và rate limiting;
- private Supabase Storage qua S3-compatible API cho attachment mới;
- nhiều backend replicas chạy qua Docker Compose.

Kiến trúc runtime được mô tả trong shared conversation theo hướng:

```text
Browser
  ↓
Gateway / reverse proxy
  ↓
Backend replica A or B
  ├── PostgreSQL 17 + Flyway
  ├── Redis Pub/Sub + distributed rate limiter
  └── private Supabase Storage S3 bucket
```

Đây là mental model ban đầu. Agent phải xác minh tên service, port, routing, health check và endpoint thực tế trong repository ứng dụng trước khi chạy lệnh.

## Reported roadmap state

| Area | Historical state | How to treat it |
|---|---|---|
| R1 WebSocket authorization | Đã xử lý cross-user topic authorization; shared chat nhắc tới 20 WebSocket security tests và 77 backend tests pass | Re-verify source/tests before relying on it |
| R5 Redis Pub/Sub broker | Đã hoàn thành | Treat as historical context; run regression if touched |
| R6 Redis distributed rate limiter | Đã hoàn thành | Treat as historical context; run regression if touched |
| R10 deployment foundation | Docker/Nginx/PostgreSQL/Compose foundation đã được xây | Verify current Compose and deployment docs |
| R12 message keyset pagination | Đã chuyển khỏi OFFSET | Verify query and cursor contract before changes |
| R13 PostgreSQL search | Trigram/Gin và search plan đã được runtime benchmark; follow-up đã được xử lý theo shared chat | Verify actual EXPLAIN and tests |
| R14 data retention | Được liệt kê trong roadmap đã xử lý | Verify migrations/jobs/tests |
| R15 async notification fanout | Được liệt kê trong roadmap đã xử lý | Verify implementation and tests |
| R16 Supabase/S3 storage | Adapter and Compose configuration are present; prior prompts report runtime checks | Re-verify any acceptance claim against the current stack; legacy MinIO object backfill is separate |
| R17 JWT hardening | Được liệt kê trong roadmap đã xử lý | Verify config and security tests |
| R18 PostgreSQL 17 runtime | Đã runtime-verified trên PostgreSQL 17.11 theo shared chat | Verify with current test output when relevant |
| R19 trusted proxy/IP resolution | Được liệt kê trong roadmap đã xử lý | Verify deployment and security tests |
| R20 batch unread aggregation | Được liệt kê trong roadmap đã xử lý | Verify query-count and integration tests |
| Frontend Phase 1/2 | Đã hoàn thành theo shared chat | Verify current frontend scripts and attachment UX |
| R11 auth-state cache | Chưa phải task hiện tại; dự kiến sau R16 | Do not start unless explicitly requested |

## Known runtime evidence from the shared conversation

The historical conversation reports:

```text
PostgreSQL: 17.11
Flyway: V1 → V19
mvn test: 220 passed, 0 failures, 0 errors, 0 skipped
PostgreSQL integration with performance enabled: 13/13 passed
MinIO: non-root, readiness HTTP 200, private bucket
Gateway readiness: HTTP 200
API without token: HTTP 401
Unsigned object access: HTTP 403
Two backend replicas and Compose services: healthy
```

The following proof must be repeated when a new task needs current runtime acceptance:

```text
Upload through backend replica A
  → shared PostgreSQL + Supabase Storage state
  → finalize/download through backend replica B
  → identical bytes and preserved authorization
```

## Historical roadmap order

The active user request determines scope and priority. The earlier roadmap listed:

1. R16 runtime attachment acceptance.
2. R11 auth-state cache, only after R16 is genuinely complete.
3. Two-user E2E through gateway, both backend replicas, Redis and Supabase Storage.
4. Performance tuning based on measured runtime data.

## Standard verification commands

Use only commands supported by the actual application repository. Common commands from the shared context are:

```bash
docker compose --env-file .env -f docker/docker-compose.yml ps
docker compose --env-file .env -f docker/docker-compose.yml config -q

mvn test
mvn verify -Ppostgres-integration
RUN_DB_PERF_TESTS=true mvn verify -Ppostgres-integration
mvn verify -Ps3-integration

npm test
npm run lint
npm run build

git diff --check
```

The agent must report which commands actually ran, not copy historical results into a new report.

## Non-negotiable safety rules

- Do not edit `.env` unless the current user request specifically authorizes that change; historical prompts do not grant standing permission.
- Do not print, commit or report tokens, passwords, credentials, presigned URLs or sensitive object keys.
- Do not run `docker compose down -v`.
- Do not delete database or legacy MinIO volumes.
- Do not disable authentication or authorization.
- Do not make the private storage bucket public to simplify testing.
- Do not expose internal backend or legacy MinIO ports publicly for debugging.
- Do not change Redis, JWT, frontend or database behavior when the task is scoped to another R-item.
- Do not turn skipped tests or infrastructure failures into `PASS`.

## Default evidence vocabulary

Every acceptance item is one of:

- `PASS` — direct evidence satisfies the criterion.
- `FAIL` — the system ran and behavior was incorrect.
- `BLOCKED` — the test could not be safely or honestly executed.

The final report must separate implementation failures from environment blockers.
