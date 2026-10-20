---
name: frontend-quality-gate
description: Verify scoped WeChat Clone frontend changes with the repository's actual lint, unit, build, browser, accessibility, and integration checks. Use before reporting frontend work complete; never fabricate visual or runtime evidence or modify code to silence failures.
---

# Frontend Quality Gate — WeChat Clone

## Purpose

Provide risk-based evidence that an approved frontend change satisfies its acceptance criteria. This Skill is verification-only by default and does not fix implementation or test failures automatically.

## Preconditions and discovery

1. Read `AGENTS.md` and `.agents/projects/wechat-clone/WORKFLOW.md`.
2. Run `git status --short` and record pre-existing changes.
3. Identify the exact change, acceptance criteria, affected routes/components, and risk.
4. Inspect `wechatfrontend/package.json`, `vite.config.js`, `playwright.config.js`, test setup, and relevant tests.
5. Check whether required services, base URLs, browser dependencies, credentials, and environment variables are available without printing secret values.
6. Do not create duplicate tests or run shared-state/destructive checks without approval.

Current scripts include `npm run lint`, `npm run test`, `npm run build`, and `npm run test:e2e`; verify they remain current before execution. Vitest uses the configured jsdom setup, and Playwright requires a reachable base URL or an approved start command.

## Verification selection

Start with the smallest useful checks:

1. Static/lint checks for changed files.
2. Targeted Vitest tests.
3. Frontend module test suite.
4. Production build.
5. Playwright or manual browser checks when the environment and acceptance criteria require them.
6. Check frontend-observable API, authentication, attachment, or STOMP behavior when affected. Assign backend enforcement and cross-replica/runtime verification to the appropriate specialist or `testing`; do not claim those checks passed from client tests alone.

Expand by risk for route/auth changes, state races, uploads, session expiry, reconnect/resync, accessibility, responsive behavior, or cross-service claims.

## Evidence rules

- `PASS`: the check ran and the expected assertion or behavior was observed.
- `FAIL`: the check ran and failed.
- `SKIPPED`: intentionally not run or excluded by configuration.
- `BLOCKED`: unavailable service, permission, browser, credential, dependency, or environment prevented execution.
- `INCONCLUSIVE`: evidence does not establish the acceptance criterion.

Never report a planned command as executed. A successful build does not prove browser behavior, backend compatibility, gateway routing, WebSocket delivery, or production readiness. Frontend unit tests do not prove cross-replica realtime behavior.

## Safety and diagnosis

Do not modify application code, tests, configuration, `.env`, databases, buckets, volumes, containers, or deployed services as part of routine verification. Do not install packages or change sandbox/approval policy. Do not expose tokens, signed URLs, object keys, credentials, or sensitive payloads.

When a check fails, capture the command and relevant non-sensitive output, compare with baseline where available, classify product/test/environment/dependency/configuration/data failure, and report the smallest reproducible issue. Do not weaken assertions or skip a failing check to obtain a green result.

## Required report and handoff

Report:

1. Change under test and acceptance criteria.
2. Test strategy and risk rationale.
3. Commands executed, exit status, and result state.
4. Evidence, failures, baseline limitations, and coverage gaps.
5. Whether the tested scope is verified, partially verified, or not verified.
6. Next-step recommendation with P0/P1/P2 priority, exact Skill, evidence, dependencies, and approval needed.
7. Current workflow stage and handoff information.

Present exactly four choices after the report:

**A. — Fix test failures:** return to the approved implementation scope; do not edit automatically.

**B. — Add missing tests:** propose cases and wait before modifying tests.

**C. — Expand verification:** run additional justified integration, browser, accessibility, security, storage, or realtime checks.

**D. — Stop:** finish with the evidence and gaps.

Recommend one choice and wait for the user's explicit choice. Do not automatically invoke another Skill. This Skill serves the TEST stage of `SCOUT → BA → PLAN → COOK → TEST → REVIEW`.
