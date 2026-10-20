---
name: spring-boot-engineering
description: Implement approved WeChat Clone Spring Boot controller, service, JDBC repository, validation, exception, and transaction changes. Use for scoped backend business logic; route schema design, API contracts, security, and realtime semantics to their specialists when needed.
---

# Spring Boot Engineering — WeChat Clone

## Activation and ownership

Use for an approved backend feature or bug fix inside the Java application. Own controller/service/repository wiring, implementation of approved validation and error contracts, dependency injection, and transactions. `api-contract-design` owns externally visible contract decisions. `wechat-spring-data-change` remains a compact cross-layer guide; `backend-architecture` owns structural decisions, and `database-migration` owns schema rollout.

Do not introduce JPA or rewrite established JDBC repositories without an approved migration plan. Do not silently change REST, STOMP, authentication, or database contracts.

## Reconnaissance

1. Read `AGENTS.md`, the project workflow, the approved task/plan, and relevant domain Skill. Run `git status --short`; preserve staged, unstaged, and untracked work.
2. Verify the active `pom.xml`, source and test layout. The inspected baseline uses Java 21, Spring Boot 4.0.6, Spring MVC, JDBC, Flyway, PostgreSQL, and Maven; recheck before acting.
3. Trace controller/DTO → validation → service → JDBC repository/storage → transaction and error mapping → frontend caller/tests. Inspect related authorization and event publication when they are touched.
4. Establish the smallest safe baseline check and record any pre-existing failure.

## Implementation protocol

1. Confirm observable acceptance criteria and files in scope. Ask only if missing behavior would materially change the implementation.
2. Make one coherent change using existing package, constructor injection, DTO, JDBC, and exception conventions.
3. Keep transaction boundaries around atomic data changes. Consider idempotency, concurrency, pagination, and after-commit publication where relevant.
4. Update focused tests for changed behavior and negative paths. Inspect `git diff` after each coherent edit.
5. Run targeted Maven tests first; use the configured `postgres-integration`, `redis-integration`, or `s3-integration` profile only when the affected boundary and an isolated environment justify it.

An H2 test does not establish PostgreSQL compatibility. A green service test does not prove frontend, gateway, cross-replica, or storage behavior.

## Quality gate and stop conditions

Complete only when each acceptance criterion maps to a changed file and observed check or an explicit gap. Preserve current API/error behavior unless the contract was approved. Report test commands and exit outcomes truthfully.

Stop when implementation requires an unapproved schema migration, contract change, permission change, deployment, destructive data operation, or new dependency. Do not edit `.env`, expose secrets, weaken authorization to pass tests, or stage/commit/push without authorization.

## Report and handoff

Report scope/result; files changed and rationale; contract, schema, and transaction effects; commands actually executed and PASS/FAIL/SKIPPED/BLOCKED outcomes; failed, blocked, and unexecuted checks; remaining risks. For any useful next step give P0/P1/P2, exact Skill, evidence, scope, and prerequisites; say none when complete.

Record the current SCOUT → BA → PLAN → COOK stage and handoff facts. PLAN selects only needed specialists; COOK means execution of the approved phase through the `cook` Skill. Use `api-contract-design`, `backend-security`, `realtime-engineering`, or `database-migration` only when that boundary is in scope; do not invoke them automatically.
