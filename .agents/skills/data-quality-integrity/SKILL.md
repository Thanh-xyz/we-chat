---
name: data-quality-integrity
description: Verify and safeguard WeChat Clone PostgreSQL data invariants, constraints, orphan handling, retention, idempotency, and transactional consistency. Use for integrity analysis or approved data repair design; distinguish application validation from database enforcement.
---

# Data Quality and Integrity — WeChat Clone

## Activation and ownership

Use when a change could create duplicates, orphans, invalid state transitions, inconsistent read receipts, incorrect retention, or cross-resource mismatches. Own invariant definition, detection, and verification. `data-modeling` defines new schema semantics; `database-migration` owns schema changes; `spring-boot-engineering` owns application fixes.

## Reconnaissance

1. Read `AGENTS.md`, project workflow, task/BA evidence, and Git status; preserve existing work.
2. Inspect relevant Flyway constraints, JDBC write/read predicates, transaction boundaries, cleanup jobs, and tests. Verify the current source of truth for each value.
3. Trace related PostgreSQL rows and private S3 object lifecycle where attachments are involved. Redis Pub/Sub events are transient and are not an integrity ledger.
4. Use read-only counts or samples only when access is authorized and outputs can avoid sensitive row values.

## Integrity protocol

1. State each invariant as a falsifiable condition with owner and enforcement layer: database, application, transaction, or external reconciliation.
2. Identify existing/legacy rows and races: duplicate requests, concurrent writes, partial failure, retry after timeout, cleanup overlap, and soft-delete visibility.
3. Design safe detection queries and isolated fixture tests before proposing repair.
4. If repair is explicitly approved, define exact target population, dry-run count, idempotency, checkpoint, audit evidence, rollback/recovery, and stop condition. Coordinate any schema change through `database-migration`.
5. Verify both the intended postcondition and absence of collateral row/object changes.

Application validation alone does not guarantee database integrity under concurrency. A database constraint alone does not define user-facing error behavior.

## Quality gate and stop conditions

Completion requires a traceable invariant matrix, evidence for observed violations or explicit uncertainty, and a verification strategy. Do not report the dataset clean without an executed safe check.

Do not delete, rewrite, backfill, or expose live data without explicit authorization. Stop when ownership, repair scope, production impact, or rollback is unresolved. Do not edit `.env`, deploy, or alter sandbox/approval settings.

## Report and handoff

Report scope/result; invariant and enforcement evidence; files changed or proposed; commands actually executed with PASS/FAIL/SKIPPED/BLOCKED; failed, blocked, and unexecuted checks; remaining risks and out-of-scope findings. Recommend P0/P1/P2 next steps with exact Skill, reason, scope, and prerequisites, or state none.

Record SCOUT → BA → PLAN → COOK stage. PLAN selects this Skill for a concrete integrity risk and coordinates `database-migration` or `spring-boot-engineering` for approved fixes. COOK runs through the `cook` Skill after PLAN approval; do not invoke it automatically. Never invoke another Skill automatically.
