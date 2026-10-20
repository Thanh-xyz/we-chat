---
name: data-modeling
description: Design WeChat Clone PostgreSQL entities, relationships, keys, constraints, lifecycle, and access patterns. Use when data semantics or schema shape must be decided; use database-migration for rollout and data-quality-integrity for invariant verification.
---

# Data Modeling — WeChat Clone

## Activation and ownership

Use when requirements change stored entities, relationships, ownership, retention, uniqueness, or access patterns. Own logical and physical model decisions. `database-migration` owns Flyway implementation and rollout; `data-quality-integrity` owns detecting and verifying violated invariants.

Do not create tables or indexes simply for a cleaner model, or infer business rules from a proposed UI. Do not treat application validation as a substitute for database constraints.

## Reconnaissance

1. Read `AGENTS.md`, project workflow, the request and BA/Scout/plan evidence; run `git status --short` and preserve user changes.
2. Inspect relevant `wechatbackend/src/main/resources/db/migration` versions, JDBC repositories, models/DTOs, services, and tests. Verify current PostgreSQL schema rather than trusting a historical diagram.
3. Trace reads/writes, authorization and ownership predicates, transactions, external storage metadata, and cleanup/retention jobs where relevant.
4. Record existing rows and deployment compatibility as unknown unless current evidence establishes them.

## Modeling protocol

1. Define entities, cardinality, identifiers, ownership, lifecycle transitions, nullability, uniqueness, and delete/retention rules from approved requirements.
2. Map each invariant to its enforcement layer: database constraint, transaction, application validation, or a combination.
3. Evaluate normalization and access paths against actual repository queries and scale evidence. Identify indexes as hypotheses for `sql-query-optimization` if plan evidence is needed.
4. Compare reasonable schemas for compatibility with existing rows, JDBC mapping, pagination, and Flyway rollout.
5. Propose exact new/changed tables or columns and an acceptance matrix; mark proposed schema as unimplemented until migrated.

For attachment data, distinguish PostgreSQL metadata/ownership from private S3-compatible bytes. Do not infer durable realtime history from Redis Pub/Sub.

## Quality gate and stop conditions

The model is ready when every field and relation maps to an explicit requirement or verified access pattern, existing-data compatibility is addressed, and constraints and rollback/recovery are defined. A diagram alone is insufficient.

Remain read-only for analysis. Stop before unapproved schema, API, auth, retention, or data deletion changes. Do not inspect or report sensitive row values, run production migrations, alter `.env`, or deploy.

## Report and handoff

Report scope/result; current-model evidence; proposed model and invariant table; files changed or proposed with rationale; actual checks with PASS/FAIL/SKIPPED/BLOCKED; failed, blocked, and unexecuted checks; assumptions and risks. Recommend P0/P1/P2 next steps with exact Skill, reason, scope, and prerequisites, or state none.

Record SCOUT → BA → PLAN → COOK stage. Hand an approved model to `plan` and then `database-migration`/`spring-boot-engineering` as selected; use `data-quality-integrity` for invariant verification. COOK runs through the `cook` Skill after PLAN approval; do not invoke it automatically. Never invoke another Skill automatically.
