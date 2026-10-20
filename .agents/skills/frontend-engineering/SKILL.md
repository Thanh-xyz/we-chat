---
name: frontend-engineering
description: Implement approved WeChat Clone React/Vite frontend features and bug fixes with scoped, contract-aware, accessible code. Use for frontend implementation after requirements and plan are clear; preserve existing routing, API, auth, state, and STOMP behavior.
---

# Frontend Engineering — WeChat Clone

## Purpose

Implement an approved frontend phase in small, verifiable changes. This is the frontend execution Skill; it does not replace business analysis, architecture review, integration review, or quality verification.

## Preconditions

Before editing:

1. Read `AGENTS.md`, `.agents/projects/wechat-clone/WORKFLOW.md`, and relevant specialist Skills.
2. Identify the approved requirement, plan phase, acceptance criteria, exact files, and out-of-scope items.
3. Run `git status --short` and preserve staged, unstaged, and untracked user changes.
4. Inspect current source and confirm the plan still matches the repository.
5. Trace any API, attachment, authentication, or STOMP boundary through the current or approved backend contract before changing callers. Route a needed REST contract decision to `api-contract-design`.
6. Run the smallest safe baseline check when practical and record its result.

If no approved scope exists, a contract is undocumented, or implementation requires material expansion, stop and request an updated plan.

## Repository-specific implementation rules

The current frontend is JavaScript/JSX React/Vite. Use existing JavaScript conventions unless TypeScript migration is explicitly approved; do not add `any`-like escape hatches, a new framework, or a new state/data layer by assumption.

Preserve:

- routes and route guards;
- `src/services/api/client.js` token/refresh/error behavior;
- API service boundaries and environment variable names;
- feature store/controller ownership and cleanup;
- `src/services/websocket/WebSocketManager.js` authentication, destinations, reconnect, and resync assumptions;
- attachment lifecycle and server-side validation boundaries;
- loading, empty, error, disabled, and success states.

Use existing components and design tokens before adding new ones. Keep components focused, avoid unrelated formatting/refactors, and account for stale requests, cancellation, race conditions, subscriptions, timers, and unmount cleanup where relevant.

## Implementation loop

1. Inspect imports, callers, state owners, tests, and configuration in scope.
2. Make the smallest coherent change.
3. Update all affected frontend references and tests.
4. Inspect `git diff` and `git status` immediately.
5. Run targeted lint/tests/build checks from `wechatfrontend/package.json`.
6. Compare evidence with each acceptance criterion.
7. Stop on the first unexplained regression or scope expansion.

Do not invent request fields, response shapes, permissions, WebSocket destinations, or error semantics. Use `frontend-integration-review` for a read-only compatibility check; `wechat-frontend-integration` remains a compact implementation guide. Do not invoke another Skill automatically.

## Safety and authorization

Only edit files in the approved frontend scope. Do not change backend, database, Docker, gateway, `.env`, secrets, sandbox/approval policy, or deployed services unless separately approved. Do not use client-side checks as a substitute for server authorization. Do not claim cross-replica realtime behavior without runtime evidence.

Do not commit, push, reset, clean, or discard user changes unless explicitly authorized.

## References

- Read [references/react-performance.md](references/react-performance.md) when the change touches lists, Zustand stores, effects, async UI, or the realtime client.

## Completion report

Report:

1. Scope completed and acceptance criteria met/unmet.
2. Files created, modified, moved, or deleted with reasons.
3. API/auth/realtime/design-system impacts.
4. Commands actually executed with exit status and `PASS`, `FAIL`, `SKIPPED`, or `BLOCKED` results.
5. Remaining risks, blockers, and unverified behavior.
6. Next-step recommendation with P0/P1/P2 priority, exact Skill, evidence, dependencies, and required approval.
7. Current workflow stage and handoff information.

Use `frontend-quality-gate` after implementation when verification is needed. Use `frontend-integration-review` for unresolved contract risk. Within the `cook` Skill's execution loop, stop after the approved frontend phase and hand off; do not invoke another stage automatically.
