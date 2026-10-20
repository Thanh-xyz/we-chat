---
name: backend-security
description: Implement or review WeChat Clone server authentication, authorization, JWT/session state, rate limits, attachment access, and sensitive-data boundaries. Use for security-sensitive backend changes and regressions; never use client visibility as an authorization boundary.
---

# Backend Security — WeChat Clone

## Activation and ownership

Use for server identity, ownership/membership, roles, JWT issuance/validation/revocation, rate limiting, CORS, STOMP authorization policy, upload/download security, or security regression analysis. This Skill owns server-side access decisions; `realtime-engineering` owns frame routing and delivery, while `frontend-integration-review` checks client assumptions. `code-review` provides independent change review.

## Reconnaissance

1. Read `AGENTS.md`, project workflow, approved scope or review target, and Git status. Preserve user work.
2. Inspect `SecurityConfig`, the affected controller/service/repository predicates, JWT and auth-state classes, STOMP channel interceptor when relevant, and negative-path tests.
3. Trace the actor's identity to each protected resource and persistence predicate. For attachments, follow `FileStorageService` and the short-lived URL boundary without outputting URLs or object keys.
4. Verify actual security behavior in source and tests; do not infer it from hidden frontend controls or docs.

## Security protocol

1. Enumerate actors, permissions, ownership/membership, denial response, and trust boundaries.
2. Check whether validation occurs before a sensitive action and whether failures leak existence or private data.
3. Assess token lifecycle, account/token-version changes, cache staleness/fallback, CORS, rate limiting, and audit logging only when affected.
4. Implement the smallest approved server-side rule or produce review findings. Add targeted positive and negative tests.
5. Verify that REST, STOMP, and storage enforcement remain consistent where the change crosses those surfaces. Inspect client assumptions, but leave client implementation to `frontend-engineering`.

Never weaken authentication, authorization, JWT validation, CORS, or production defaults to make a test pass. Client-side authorization is not a security boundary. Treat presigned URLs as temporary credentials.

## Quality gate and stop conditions

Report who can and cannot perform the action, the enforcement point, and test evidence. A passed happy path alone is insufficient for security-sensitive changes. Do not print tokens, passwords, private keys, object keys, signed URLs, or sensitive payloads.

Stop before unapproved changes to permissions, security defaults, schema, production data, shared services, or deployment. Do not edit `.env`, change sandbox/approval policy, or commit/push without authorization.

## References

- Read [references/security-checklist.md](references/security-checklist.md) for the authorization, token, input, abuse, attachment, gateway, and secret checks relevant to the change.

## Report and handoff

Report scope/result; threat or access rule; exact enforcement files and changes; commands actually run with PASS/FAIL/SKIPPED/BLOCKED; failed, blocked, and unexecuted checks; residual security risks and out-of-scope findings. Give any next step as P0/P1/P2 with exact Skill, evidence, scope, and prerequisites; say none if no follow-up is needed.

Record SCOUT → BA → PLAN → COOK stage and required handoff facts. PLAN selects `backend-security` only for a real security boundary; approved implementation may coordinate with `spring-boot-engineering`, `realtime-engineering`, or `wechat-storage-acceptance`. COOK runs through the `cook` Skill after PLAN approval; do not invoke it automatically. Never invoke another Skill automatically.
