---
name: frontend-integration-review
description: Check WeChat Clone frontend REST, authentication, attachment, and STOMP usage against current or approved backend contracts. Use for read-only client compatibility review; use api-contract-design when a new API contract decision is needed.
---

# Frontend Integration Review — WeChat Clone

## Purpose

Find client contract mismatches and unsafe assumptions at the frontend/backend/realtime boundary before implementation or release. This Skill verifies conformance to a current or approved contract; `api-contract-design` owns any proposed REST contract change. It does not edit the client, backend, database, or infrastructure.

## Required discovery

1. Read `AGENTS.md` and `.agents/projects/wechat-clone/WORKFLOW.md`.
2. Run `git status --short` and separate the requested change from existing work.
3. Read supplied Scout, BA, plan, or testing evidence and re-check high-impact claims against current source.
4. Inspect frontend callers and consumers in pages, components, feature stores/controllers, `src/services/api`, and `src/services/websocket`.
5. Inspect the matching backend controller, DTO/validation, security and event rules, and tests. Follow service, repository, or storage paths only as needed to verify the client-facing behavior.
6. Inspect gateway routes, environment names, and relevant Compose/runtime assumptions when the boundary depends on them.

## Contract traces

For REST:

```text
frontend action → API service/client → gateway path → backend controller/DTO
  → validation/authorization → service/repository/storage → response/error
  → frontend state, loading, retry, and error handling
```

For realtime:

```text
frontend connect/subscribe/send → STOMP destination and auth
  → backend channel authorization/handler → local broker/Redis fanout
  → frontend event reducer, deduplication, reconnect, and REST resync
```

Check method/path, payload names, response shape, pagination/cursors, status/error mapping, token refresh, ownership and membership checks, attachment upload/complete/download lifecycle, and cancellation behavior.

For STOMP, verify destination prefixes, user/topic subscriptions, typing or notification flows, reconnect backoff, duplicate events, ordering/loss assumptions, and resynchronization. Redis Pub/Sub is transient; do not claim replay or durable ordering without evidence.

## Review dimensions

- Frontend and backend use the same route, DTO, enum, identifier, cursor, and error assumptions.
- Client-side authorization is not treated as server authorization.
- Access tokens, refresh behavior, logout, session expiry, and sensitive response handling are consistent.
- Attachment URLs, metadata, authorization, and failure states do not leak private values or persist signed URLs incorrectly.
- Realtime events are reconciled with persisted REST state and do not create duplicate or stale UI state.
- Cross-replica, gateway, storage, and Redis claims are supported by runtime evidence rather than local unit tests.

Classify findings as `CONFIRMED`, `RISK`, `UNVERIFIED`, or `BLOCKED`, with severity, exact paths/symbols, impact, recommendation, and verification method.

## Boundaries and stop conditions

Review is read-only. Do not edit application code, tests, backend, database, Docker, gateway, `.env`, or workflow files. Do not install dependencies, expose secrets, alter sandbox/approval policy, run destructive tests, or invoke another Skill automatically.

If resolving a mismatch requires a new REST contract, hand the evidence to `api-contract-design`; if it requires an approved implementation or another boundary change, hand it to `plan`. Stop when runtime evidence or authorization is missing for a material claim.

## Required report and handoff

Report:

1. Review contract, scope, and acceptance criteria.
2. Integration paths and exact current-source evidence.
3. Findings ranked by severity and evidence state.
4. Compatibility, security, realtime, attachment, and error-handling gaps.
5. Checks actually run and `PASS`, `FAIL`, `SKIPPED`, or `BLOCKED` results.
6. Recommended fixes or additional evidence, without applying them.
7. Next-step recommendation with P0/P1/P2 priority, exact Skill, reason, dependencies, and required approval.
8. Current workflow stage and handoff information.

Use `api-contract-design` to decide a changed REST contract, `plan` for file-level sequencing, `frontend-engineering` for approved client changes, and `frontend-quality-gate` for client verification. `wechat-frontend-integration` remains the compact project contract guide. The workflow is `SCOUT → BA → PLAN → COOK → TEST → REVIEW`; implementation runs through the `cook` Skill, and no automatic handoff is allowed.
