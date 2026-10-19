---
name: testing
description: "Run and interpret risk-based tests for a scoped WeChat Clone change. Report executed evidence, PASS/FAIL/SKIPPED/BLOCKED results, coverage gaps, failure diagnosis, and a manual next-step handoff. Do not modify application files or invoke another Skill automatically."
---

# WeChat Testing Skill

Use this Skill to verify a scoped feature, bug fix, regression, API change, frontend change, realtime change, storage change, or runtime behavior in the WeChat Clone repository.

This Skill coordinates testing. It does not replace specialized knowledge in `scout`, `ba-analysis`, `plan`, `wechat-spring-data-change`, `wechat-frontend-integration`, `wechat-realtime-contract`, `wechat-storage-acceptance`, or `wechat-runtime-verification`.

## Scope

Use this Skill to:

- understand the change and its acceptance criteria;
- identify the smallest meaningful tests;
- inspect existing test conventions and configuration;
- expand verification according to risk;
- run safe and relevant checks;
- distinguish product, test, environment, dependency, configuration, and infrastructure failures;
- report evidence and limitations accurately;
- recommend a manual next action.

Do not use this Skill to:

- modify application code, configuration, migrations, tests, or documentation;
- install packages or change system configuration;
- delete data, volumes, databases, buckets, or containers;
- write to production or shared infrastructure;
- expose credentials, tokens, object keys, or presigned URLs;
- automatically invoke another Skill, Agent, or implementation workflow.

Generated build artifacts produced by normal test commands may exist, but source changes must not be made by this Skill.

## Evidence rules

Before investigation:

1. Read `AGENTS.md`.
2. Read the relevant workflow guidance.
3. Run `git status --short`.
4. Preserve existing modifications and untracked files.
5. Identify the request, acceptance criteria, affected components, and risk level.

Use these result states:

- `PASS`: the check actually ran and the expected assertion or behavior was observed.
- `FAIL`: the check actually ran and a product, test, or verification assertion failed.
- `SKIPPED`: the check was intentionally not run or was skipped by configuration.
- `BLOCKED`: the check could not run because of Docker, credentials, unavailable services, permissions, or another external dependency.
- `INCONCLUSIVE`: evidence was insufficient to establish the claim.

Never report a planned command as executed. Never report a skipped or blocked check as passing. Do not state that a check passes unless it ran in this session and its output was read; for a bug fix, show the regression test failing without the fix and passing with it.

## Risk-based workflow

### 1. Define the change under test

Record:

- user request or bug description;
- expected behavior;
- acceptance criteria;
- affected frontend, backend, API, database, realtime, storage, and runtime boundaries;
- known risks and dependencies.

If acceptance criteria are missing, document assumptions or ask a focused question only when the missing information materially changes the test plan.

### 2. Inspect existing conventions

Inspect only the relevant files first:

- backend `pom.xml`, test source, profiles, and test resources;
- frontend `package.json`, Vite/Vitest setup, Playwright setup, and relevant tests;
- Compose files and health checks when runtime behavior is in scope;
- existing reports from `scout`, `ba-analysis`, or `plan` when supplied.

Do not assume that documentation proves current runtime behavior. Prefer source code and current command output.

### 3. Select the smallest meaningful checks

Start with focused deterministic checks:

- changed backend unit or service tests;
- changed frontend unit or component tests;
- focused API, repository, realtime, or storage tests;
- lint/build checks when the changed boundary requires them.

Expand testing according to risk:

- database or migration changes require PostgreSQL compatibility checks;
- Redis, STOMP, reconnect, or resync changes require realtime checks;
- object-storage changes require storage lifecycle and authorization checks;
- cross-replica claims require a multi-instance runtime check;
- browser behavior requires Playwright or another browser-level check;
- security-sensitive behavior requires authorization and negative-path checks.

### 4. Assess environment safety

Before tests involving Docker, Testcontainers, databases, Redis, object storage, Compose, or shared services:

- determine whether the environment is local, isolated, shared, or production-like;
- check whether the command can create, truncate, migrate, or write data;
- avoid destructive cleanup and unapproved shared-service writes;
- ask for approval when the test may affect shared state;
- use a safe alternative or mark the check `BLOCKED` when approval or infrastructure is unavailable.

Do not install dependencies or change `.env`, system configuration, containers, volumes, or deployed services.

### 5. Run relevant checks

Backend examples:

    cd wechatbackend
    mvn test
    mvn verify -Ppostgres-integration
    mvn verify -Predis-integration
    mvn verify -Ps3-integration

Use PostgreSQL planner checks only when relevant and explicitly justified:

    RUN_DB_PERF_TESTS=true mvn verify -Ppostgres-integration

Frontend examples:

    cd wechatfrontend
    npm test
    npm run lint
    npm run build
    npm run test:e2e

Run E2E only when the base URL or controlled start command is available and authentication can be handled safely.

Runtime checks may include only safe, relevant commands such as Compose configuration validation or health inspection. Do not claim cross-replica behavior without actually running an isolated multi-instance verification.

### 6. Interpret limitations

Always account for these boundaries when relevant:

- H2 does not establish PostgreSQL compatibility.
- Testcontainers checks may be unavailable or skipped without Docker.
- Local MinIO/S3-compatible tests do not establish Supabase production behavior.
- Redis Pub/Sub is transient and does not replay missed events.
- A single backend instance does not establish cross-replica delivery.
- Frontend unit tests do not establish gateway, REST, STOMP, or production behavior.
- Playwright requires a reachable application and suitable authentication setup.
- The absence of CI workflows is a coverage and repeatability gap.
- A green command does not prove checks outside that command were executed.

### 7. Diagnose failures

Classify the observed cause as one or more of:

- product or implementation failure;
- test failure;
- environment or infrastructure failure;
- dependency or toolchain failure;
- configuration failure;
- data or fixture failure;
- inconclusive evidence.

Use logs and assertions as evidence. Do not expose secrets, tokens, object keys, signed URLs, or sensitive payloads.

Do not edit the implementation to make a test pass. If a code change appears necessary, report it as a recommendation and wait for the user's decision.

### 8. Recheck repository state

After checks:

- record commands actually executed;
- record exit status and relevant output;
- identify generated artifacts without deleting unrelated files;
- run `git status --short` again;
- preserve all pre-existing changes.

## References

- Read [references/test-discipline.md](references/test-discipline.md) for the verification gate, bug-fix regression proof, risk coverage, mock discipline, and async/realtime test rules.

## Required report

Keep the report proportional to the change and use these sections:

1. **Change under test**
2. **Test strategy**
3. **Commands executed**
4. **Results**
   - `PASS`
   - `FAIL`
   - `SKIPPED`
   - `BLOCKED`
   - `INCONCLUSIVE`
5. **Evidence**
6. **Coverage gaps**
7. **Diagnosis**
8. **Recommended next action**

For every result, distinguish:

- planned but not executed;
- executed and passing;
- executed and failing;
- skipped by configuration;
- blocked by infrastructure, credentials, or environment;
- code failure versus environment failure.

Do not force irrelevant sections into a small task.

## Manual handoff

After reporting results, present exactly these four choices:

**A. — Fix test failures:** recommend returning to implementation when code changes are necessary, but do not edit automatically.

**B. — Add missing tests:** propose the missing cases and wait before modifying tests.

**C. — Expand verification:** recommend additional integration, security, regression, browser, storage, realtime, or runtime checks when justified.

**D. — Stop:** report the evidence and coverage gaps without further action.

Recommend one choice with a short reason.

Wait for the user's explicit choice. Never automatically invoke `plan`, `scout`, `ba-analysis`, a domain Skill, an Agent, or an implementation workflow.
