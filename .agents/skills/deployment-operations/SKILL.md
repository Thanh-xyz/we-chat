---
name: deployment-operations
description: Change or review WeChat Clone Dockerfiles, Docker Compose services, the Nginx gateway, health checks, environment variable contracts, and release or rollback runbooks. Use for build and deployment configuration work; use wechat-runtime-verification to verify a running stack.
---

# Deployment Operations — WeChat Clone

## Activation and ownership

Use when a task changes or reviews how the system is built, configured, started, routed, or rolled back:

- `docker/backend|frontend|gateway/Dockerfile`;
- `docker/docker-compose.yml` and the root `docker-compose.yml` include file;
- `docker/gateway/nginx.conf` and `docker/frontend/nginx.conf`;
- Actuator health exposure and the environment variable contract in `.env.example` and `docs/deployment.md`;
- release, migration rollout, and rollback runbooks.

This Skill owns build and deployment configuration and its runbooks. `wechat-runtime-verification` owns evidence that a running stack behaves correctly. `database-migration` owns Flyway content. `backend-security` owns application security decisions, while this Skill owns their gateway and container enforcement (exposed ports, headers, origins, least privilege).

Root-level `nginx/` and `monitoring/` are not referenced by `docker/docker-compose.yml` in the inspected baseline. Confirm which configuration is actually wired before changing either.

## Hard boundaries

These require explicit authorization in the current request:

- editing `.env` (never print its values);
- running `docker compose down -v`, deleting volumes, or pruning images;
- deploying to a shared or remote host;
- publishing new host ports;
- exposing Postgres, Redis, the backend, or the frontend outside the private networks.

Do not run `docker compose config` output into reports, because it resolves secrets. Validate it with `docker compose ... config --quiet` instead.

## Reconnaissance

1. Read `AGENTS.md`, `.agents/projects/wechat-clone/WORKFLOW.md`, `docs/deployment.md`, and the task. Run `git status --short` and preserve user changes.
2. Map the services and their relationships:
   - services: `postgres`, `redis`, `backend`, `frontend`, `gateway`;
   - networks;
   - `depends_on` conditions;
   - health checks;
   - published ports (only `gateway` in the baseline);
   - volumes (`postgres_data`);
   - hardening (`read_only`, `cap_drop`, `security_opt`, non-root `USER`).
3. Trace any affected route through the gateway (`/`, `/api/`, `/ws`, approved `/actuator/*`) to its upstream, and through Spring configuration to the variable that controls it.
4. Identify the backend's consumers of each changed variable (`application*.properties`) and the documentation that lists it.

## Change protocol

1. **Preserve invariants unless the plan changes them:**
   - only the gateway publishes a port;
   - the database and Redis stay on private networks;
   - containers run as non-root;
   - images use pinned versions;
   - Flyway runs at backend startup before readiness;
   - the WebSocket `Upgrade`/`Connection` proxy headers stay on `/ws`;
   - `/actuator/*` stays deny-by-default except for the approved paths.
2. **Health:** keep liveness (process alive) separate from readiness (dependencies usable). `depends_on: condition: service_healthy` orders startup only; it does not drain traffic later.
3. **Configuration contract:** add a variable in one coherent change across `.env.example`, Compose, Spring properties, and `docs/deployment.md`. Required secrets must fail fast when blank. Never add defaults for secrets.
4. **Images:** use multi-stage builds, minimal runtime images, a non-root user, and no build tools or secrets in the final layer. Keep build contexts and `.dockerignore` consistent with moved paths.
5. **Replicas:** anything assuming a single backend (local file storage, in-memory state, per-JVM rate limits) needs Redis or S3-backed equivalents before scaling. Cross-replica claims need `wechat-runtime-verification`.

## Release and rollback runbook

For a release-affecting change, write or update a runbook with:

- **Pre-deploy:**
  - a database backup or snapshot plan;
  - migration compatibility with the previous application version (expand → migrate → contract);
  - the images built and tagged;
  - `config --quiet` passes.
- **Deploy steps:** service order, and the health or readiness condition that must be met at each step.
- **Rollback trigger and steps:** which signal triggers a rollback, the previous image tags, and whether the schema is backward-compatible. A forward-only migration needs a documented forward fix instead of a rollback.
- **Post-deploy smoke checks:** health, login, send a message, receive it over WebSocket, an attachment round-trip. Run these via `wechat-runtime-verification`.

## Verification

Static checks:

- `docker compose --env-file .env -f docker/docker-compose.yml config --quiet`;
- `docker build` for each changed Dockerfile;
- `nginx -t` inside the gateway image.

Runtime checks, only when authorized and isolated: `up -d`, `ps` health, gateway routes, and the Actuator liveness and readiness endpoints. Report each check as PASS/FAIL/SKIPPED/BLOCKED. A missing Docker daemon or `.env` is `BLOCKED`, not a pass.

## Report and handoff

Report:

- scope and result;
- files changed and the invariant each one preserves or changes;
- environment variable contract changes;
- the runbook delta;
- commands actually run, with their results;
- residual operational risks.

Recommend next steps with P0/P1/P2 priority, the exact Skill (usually `wechat-runtime-verification` for runtime evidence), and the approvals needed. Record the workflow stage. Never deploy, push images, or invoke another Skill automatically.
