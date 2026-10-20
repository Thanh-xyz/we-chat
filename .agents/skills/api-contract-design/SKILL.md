---
name: api-contract-design
description: Define proposed WeChat Clone REST request, response, validation, error, status, and pagination contracts. Use when an API contract must change or be newly specified; use frontend-integration-review to check an existing client's compatibility.
---

# API Contract Design — WeChat Clone

## Activation and ownership

Use when request/response DTOs, endpoint behavior, status codes, validation errors, or pagination must be specified for a new or changed API. Own the intended contract and compatibility strategy. `spring-boot-engineering` implements the approved server contract; `frontend-integration-review` checks whether the client conforms to the current or approved contract.

For a read-only compatibility check with no contract decision, use `frontend-integration-review`. Do not take ownership of unrelated server or client implementation, invent endpoints, or make a database schema change merely to simplify a payload.

## Reconnaissance

1. Read `AGENTS.md`, project workflow, current request, available BA/Scout/plan output, and Git status. Preserve user changes.
2. Inspect the exact controller, DTO, validation, service, repository/storage, exception mapper, relevant tests, and affected `wechatfrontend/src/services/api` caller to establish compatibility impact.
3. Inspect gateway `/api` routing and authentication/session behavior when path or error handling depends on them.
4. Separate verified current behavior, user requirements, assumptions, and proposed behavior.

## Contract protocol

1. State actor, authorization, method/path, request fields, validation, success response, error response/status, and side effects.
2. Check identifier ownership, null/optional semantics, pagination cursor or offset, sort stability, idempotency, and concurrency where relevant.
3. Identify existing consumers and compatibility risks. Compare additive evolution, a coordinated breaking change, and preserving the contract when alternatives matter.
4. Specify server and client changes separately, with a transition/rollback path and acceptance examples for happy, invalid, unauthorized, and failure cases.
5. Deliver the proposed contract for PLAN. Edit DTO/contract files only when their implementation is explicitly in scope; leave full server and client behavior to their implementation owners.

## Quality gate and safety

The output is ready when every field/status is grounded in source or explicitly proposed, consumers are mapped, and acceptance checks are observable. Tests not run are not passing. For changed contracts, trace controller → service → persistence/event → frontend consumer before declaring compatibility.

Stop if auth, data visibility, or realtime semantics change beyond approval. Do not weaken server authorization, modify `.env`, expose credentials or signed URLs, migrate data, deploy, or invoke another Skill automatically.

## References

- Read [references/rest-conventions.md](references/rest-conventions.md) when specifying status codes, errors, pagination, idempotency, or compatibility.

## Report and handoff

Report scope/result; contract table or exact proposed delta; files changed and why (or affected files if read-only); executed commands with PASS/FAIL/SKIPPED/BLOCKED; failed, blocked, unexecuted checks; compatibility risks and out-of-scope findings. Recommend a P0/P1/P2 next step with exact Skill, reason, scope, and prerequisites only if needed.

Record the SCOUT → BA → PLAN → COOK stage. Hand the agreed contract to `plan`; selected execution may use `spring-boot-engineering` and `frontend-engineering`, then `frontend-integration-review`/`testing`. COOK runs through the `cook` Skill after PLAN approval; do not invoke it automatically. Never continue automatically.
