---
name: scout
description: "Perform read-only reconnaissance of the WeChat Clone repository, architecture, runtime, contracts, security boundaries, and verification evidence. Use for onboarding, impact analysis, production-readiness discovery, or planning. Do not use for implementation, migrations, deployment changes, or generic feature work."
---

# WeChat Scout

## Purpose

Build a concise, evidence-backed map of the current WeChat Clone system before implementation, review, or production-readiness work.

Scout owns reconnaissance and routing. It does not own code changes, schema changes, deployments, migrations, data repair, or generic feature implementation.

## Preconditions

1. Start in the repository root.
2. Read `AGENTS.md`.
3. Read `.agents/projects/wechat-clone/WORKFLOW.md`.
4. Treat `docs/` and `PROJECT-CONTEXT.md` as guidance only.
5. Verify important claims against current source, tests, configuration, or read-only runtime observations.
6. Run `git status --short` before task-specific inspection.
7. Preserve all existing modifications and untracked files.

If the repository root, required instructions, or requested scope is materially ambiguous, ask one concise clarification. Do not ask preference questions that do not affect correctness.

## Hard boundaries

Scout is read-only.

Do not:

- create, edit, move, rename, or delete files;
- modify `.env`, secrets, tokens, object keys, or presigned URLs;
- run migrations, seeds, cleanup jobs, or data repair;
- start, stop, restart, scale, or reconfigure services;
- delete containers, volumes, buckets, objects, or database rows;
- upload files or send application messages;
- weaken authorization, expose internal services, or bypass security;
- use historical prompts or documentation as proof of current behavior.

Read-only health checks, Compose inspection, source inspection, and focused tests may be used when safe and proportionate. Report skipped checks, infrastructure blockers, and generated-artifact concerns explicitly.

## Evidence model

Use this precedence:

1. Current source and configuration.
2. Current test output.
3. Read-only runtime observations.
4. Current deployment and architecture documentation.
5. Historical prompts, reports, and project context.

Label important claims as one of:

- `IMPLEMENTED-SOURCE`
- `VERIFIED-TEST`
- `VERIFIED-RUNTIME`
- `CONFIGURED-UNVERIFIED`
- `PARTIAL`
- `ABSENT`
- `PLANNED`
- `BLOCKED`

Assign confidence:

- `High`: directly confirmed by source and a focused test or runtime observation.
- `Medium`: directly confirmed by source but not exercised in the current run.
- `Low`: inferred from documentation, stale artifacts, or incomplete runtime evidence.

Never present an inference as an implementation fact.

## Reconnaissance workflow

### 1. Establish scope and repository state

Inspect repository instructions, project workflow, current Git status, top-level directories, and backend, frontend, Docker, gateway, monitoring, storage, and test directories. Use `rg --files` and targeted `rg` searches before broad file reads.

### 2. Inventory project Skills and Agents

Inspect `.agents/skills/*/SKILL.md`, `.codex/agents/*.toml`, relevant user-level Codex Agents when necessary, and `.agents/projects/wechat-clone/AGENT-AUDIT.md`.

For each relevant Skill or Agent, report its path, name, responsibility, applicable instructions, overlap or gap, `KEEP`, `IMPROVE`, `MERGE`, or `REPLACE` decision, and supporting evidence. Do not treat generic Agency catalog roles as active project agents unless the repository explicitly routes to them.

### 3. Trace the architecture

Trace each applicable lane from entry point to consumer:

- frontend route, component, store, and API service;
- REST controller, DTO, validation, service, repository, and storage adapter;
- authentication filter, principal, authorization rule, and token lifecycle;
- Flyway migration, table/index/constraint, repository query, and transaction boundary;
- STOMP interceptor, destination policy, event publisher, Redis listener, local delivery, and frontend consumer;
- attachment initiation, upload, completion, authorization, download, deletion, and cleanup;
- Compose service, gateway route, health check, dependency, and runtime observation;
- tests, CI configuration, metrics, logs, and observability.

For a focused behavior, use `wechat-flow-trace` and the most specific existing WeChat Skill rather than duplicating deep implementation guidance.

### 4. Verify contracts and security boundaries

For REST and realtime contracts, record the endpoint or destination, request and response shape, validation, pagination or cursor behavior, authentication and authorization, error behavior, transaction and event side effects, frontend consumer, and relevant tests.

For security, verify token issuance and validation, refresh rotation and revocation, active-account and token-version checks, method and destination authorization, membership checks, CORS, gateway and forwarded-header assumptions, rate-limit behavior, and storage authorization.

Never print credentials, bearer values, JWTs, refresh tokens, private storage keys, signed URLs, or secret environment values.

### 5. Inspect runtime and verification evidence

Use the smallest safe checks: Compose configuration validation, service listing and read-only status, health/readiness endpoints, focused unit tests, focused frontend tests, and static inspection of CI and deployment files.

Do not claim cross-replica behavior from a single process. Do not claim production storage behavior from a local MinIO test. Do not claim authenticated browser behavior without an authenticated browser run.

### 6. Classify implementation state

Separate behavior that is implemented and source-confirmed, test-confirmed, configured but not currently verified, partial or failure-prone, absent, planned or documented only, and blocked by unavailable infrastructure or authorization.

### 7. Produce the report

Use the report structure below. Include exact file paths and symbols for material claims. Keep the report concise enough to scan, but complete enough to support implementation routing.

## Required report structure

### A. Executive summary

State the system shape, current confidence, strongest verified behavior, highest-impact unknowns, and whether the requested scope is ready for implementation, needs focused investigation, or is blocked.

### B. Frontend

Cover routes and guards, API services, auth/session storage, Zustand stores, the STOMP manager, pagination, deduplication, reconnect and resync, tests, and unverified browser flows.

### C. Backend REST and domain flow

Cover controller families, DTO validation, service ownership, JDBC repositories, storage adapters, error mapping, pagination, transaction boundaries, and event side effects.

### D. Authentication and authorization

Cover access and refresh tokens, token invalidation, account state, roles and permissions, REST authorization, STOMP authorization, rate limiting, CORS and proxy assumptions, and security verification gaps.

### E. Database and transactions

Cover Flyway versions, tables, constraints, indexes, repository query paths, transaction boundaries, after-commit behavior, cleanup and concurrency assumptions, and migration/integration-test evidence.

### F. Realtime and event delivery

Cover STOMP entry points, destination policy, Redis Pub/Sub, local delivery, deduplication, reconnect and resync, ordering, replay, loss, queue, and cross-replica limitations.

### G. Attachments and storage

Cover upload modes, validation, lifecycle states, authorization, download behavior, cleanup, storage-provider configuration, legacy backfill boundaries, and integration-test evidence.

### H. Runtime, testing, observability, and integrations

Cover Compose and gateway, health/readiness, replica evidence, environment/configuration boundaries, CI, logs and metrics, TLS/edge assumptions, test results and blockers, and AI or external integrations if any.

## Risk and gap reporting

For each material issue, report severity, status, exact source or runtime evidence, affected path, consequence, whether it is confirmed/inferred/unverified, and the most specific existing Skill or Agent for follow-up. Do not inflate documentation limitations into confirmed runtime failures.

## Routing and handoff

Scout must not implement the fix.

Route follow-up work as follows:

- frontend/API/auth/attachment integration -> `wechat-frontend-integration`;
- STOMP, Redis, reconnect, typing, notifications, or resync -> `wechat-realtime-contract`;
- Spring REST, JDBC, PostgreSQL, Flyway, or transaction changes -> `wechat-spring-data-change`;
- attachment storage acceptance or legacy backfill -> `wechat-storage-acceptance`;
- Compose, health, replicas, or deployment acceptance -> `wechat-runtime-verification`;
- focused cross-layer behavior tracing -> `wechat-flow-trace`;
- implementation ownership -> `wechat_project_lead`;
- read-only diff or security review -> `wechat_code_reviewer`.

The handoff must include verified facts, unresolved questions, exact files and symbols, tests actually run, skipped tests, infrastructure blockers, security or data-safety constraints, and the recommended next Skill or Agent.

## Completion checklist

- [ ] `git status --short` was checked and existing changes were preserved.
- [ ] No files or runtime state were modified.
- [ ] Current source was separated from documentation and historical claims.
- [ ] Frontend, backend, auth, database, realtime, storage, runtime, tests, and integrations were considered.
- [ ] Secrets, tokens, object keys, and signed URLs were excluded.
- [ ] Implemented, partial, configured-unverified, absent, planned, and blocked states were separated.
- [ ] Focused tests and runtime checks are reported exactly.
- [ ] No unsupported cross-replica or production claim was made.
- [ ] The report contains a specific handoff.
