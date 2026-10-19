---
name: frontend-architecture
description: Audit and improve the React/Vite frontend architecture of the WeChat Clone using current-source evidence. Use for pages, features, components, hooks, stores, API services, routing, dependency direction, and incremental frontend restructuring; do not make broad repository or backend changes.
---

# Frontend Architecture — WeChat Clone

## Purpose

Define maintainable frontend boundaries and safe incremental restructuring based on the repository that actually exists. This Skill is architecture review and design; it does not authorize a broad refactor.

## Current-repository assumptions to verify

The current source is JavaScript/JSX React/Vite, with React Router, Zustand, Axios, STOMP.js, page components, feature stores/controllers, API services, and a WebSocket manager. Verify every claim before relying on it; do not introduce TypeScript or a new state/data framework merely because the Skill description mentions architecture.

Inspect:

- `src/App.jsx`, `src/main.jsx`, route guards, pages, and layouts;
- `src/components` and `src/features` ownership;
- `src/services/api`, `src/services/websocket`, utilities, and test setup;
- `package.json`, `vite.config.js`, Playwright config, assets, and relevant tests.

## Audit workflow

1. Read `AGENTS.md` and `.agents/projects/wechat-clone/WORKFLOW.md`.
2. Run `git status --short`; preserve all existing changes.
3. Establish the task contract: objective, scope, non-goals, acceptance criteria, evidence, and safety limits.
4. Map route → page/component → feature state/controller → API or WebSocket boundary → tests.
5. Inspect imports and dependency direction before proposing moves.
6. Separate confirmed coupling or duplication from stylistic preference and unverified risk.
7. Compare reasonable structures and recommend the smallest useful change.
8. Produce a migration order, acceptance criteria, rollback points, and verification plan.

## Architecture rules

- Keep pages responsible for route composition, not every business operation.
- Keep reusable components presentational unless they have an explicit shared behavior.
- Keep feature state and orchestration near the feature that owns it.
- Keep API calls in existing service boundaries and session refresh behavior in its current owner.
- Keep WebSocket connection, subscription, reconnect, and resynchronization policy in the existing realtime owner unless a plan explicitly changes it.
- Avoid circular imports, cross-feature state mutation, duplicated business rules, and abstraction without a concrete consumer.
- Do not rename or move files without finding imports, tests, build references, documentation references, and generated/runtime implications.
- Preserve routes, API contracts, authentication, attachment behavior, and realtime semantics unless explicitly approved.

## Findings and target design

For every material finding, report an ID, severity, category, exact evidence, current behavior, impact, recommendation, dependencies/regressions, verification method, and whether it is confirmed or requires investigation.

Distinguish existing paths from proposed paths in target trees. For each move identify known references and rollback strategy. Do not propose backend, database, Docker, or public API changes as part of a frontend-only refactor unless the boundary is explicitly in scope.

## Boundaries and stop conditions

Remain read-only when performing an audit or plan. Stop before implementation when there is no approved plan, the current repository materially differs from the plan, or the change would alter a backend contract, authorization rule, persistence behavior, or realtime guarantee.

Do not modify `.env`, expose secrets, install dependencies, change sandbox/approval policy, deploy, or automatically invoke another Skill.

## Required report and handoff

Report:

1. Executive summary, scope, and non-goals.
2. Current frontend structure and evidence.
3. Confirmed findings and unverified risks.
4. Proposed target structure and current-to-target mapping.
5. Dependency order, phased plan, acceptance criteria, verification, and rollback.
6. Files changed or proposed.
7. Actual commands and `PASS`, `FAIL`, `SKIPPED`, or `BLOCKED` results.
8. Open questions and next-step recommendation with P0/P1/P2 priority, exact Skill, evidence, and approval needed.
9. Workflow stage and handoff information.

Use `refactor-plan` for approved structural sequencing, `frontend-engineering` for approved implementation, and `frontend-quality-gate` for verification. The workflow is `SCOUT → BA → PLAN → COOK → TEST → REVIEW`; implementation runs through the `cook` Skill, and no automatic handoff is performed.
