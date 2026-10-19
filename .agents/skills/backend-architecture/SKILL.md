---
name: backend-architecture
description: Assess and design WeChat Clone backend domain boundaries, dependency direction, transaction ownership, and incremental structural refactors. Use for backend architecture decisions; use spring-boot-engineering for scoped Spring implementation.
---

# Backend Architecture — WeChat Clone

## Activation and ownership

Use when a change raises questions about Java domain packages, cross-domain dependencies, responsibility placement, transaction ownership, or a backend structural refactor. Own the backend architectural decision and migration sequence. `frontend-architecture` owns React module boundaries; `architecture-review` covers the whole repository; `refactor-plan` sequences an approved refactor; `spring-boot-engineering` implements scoped backend behavior.

Do not redesign the whole repository, replace JDBC with JPA, add services or frameworks, or implement feature logic solely for architectural purity.

## Reconnaissance

1. Read `AGENTS.md`, `.agents/projects/wechat-clone/WORKFLOW.md`, the request and available Scout/BA/plan evidence. Run `git status --short` and preserve user changes.
2. Inspect the affected packages under `wechatbackend/src/main/java/main/com/chat/wechat`, callers, tests, `pom.xml`, and configuration. Treat current source as evidence and older reports as leads.
3. Map entry point → service → JDBC repository/storage → transaction/event publication → consumer. Inspect package imports before alleging a cycle.
4. Check Spring component scanning, resource discovery, Flyway location, and Docker build paths before proposing moves.

## Decision protocol

1. State the current responsibility and a concrete problem, with exact files/symbols and impact.
2. Distinguish orchestration across domains from unwanted dependency cycles; do not label every cross-package import a defect.
3. Compare the smallest viable boundary changes, including keeping the current structure. Specify ownership of data writes, authorization, transaction boundaries, and after-commit effects.
4. Propose incremental file/package moves or ports/events with all affected imports, tests, and runtime references. Mark proposed paths as proposed.
5. Define acceptance criteria, dependency order, regression checks, and a rollback point for each phase.

Preserve REST/STOMP contracts, JWT/RBAC behavior, JDBC semantics, Flyway history, Redis delivery assumptions, and S3 authorization unless a separate approved requirement changes them.

## Quality gate and stop conditions

A recommendation is ready when confirmed findings are separated from assumptions, alternatives have concrete tradeoffs, and each proposed move has a reference map and verification method. For a read-only review, report tests as not run. Do not claim runtime architecture verified from source alone.

Stop for clarification when the only solution requires changing a public contract, applied migration, security boundary, or unrelated service outside the approved scope. Do not edit, deploy, modify data, expose secrets, or alter Git history during an audit.

## Report and handoff

Report scope/result, current evidence, findings, decision, files changed and why (or proposed paths if read-only), commands actually run with PASS/FAIL/SKIPPED/BLOCKED, failed/blocked/unexecuted checks, remaining risks and out-of-scope findings. Give the smallest useful next step with P0/P1/P2 priority, exact Skill name, reason, proposed scope, and prerequisites; say none if no follow-up is needed.

Record the current SCOUT → BA → PLAN → COOK stage. Hand architectural decisions to `refactor-plan` or `plan`; an approved implementation can use `spring-boot-engineering`. COOK runs through the `cook` Skill after PLAN approval; do not invoke it automatically. Recommendations do not authorize execution; never invoke another Skill automatically.
