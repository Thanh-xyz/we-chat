---
name: database-migration
description: Design, implement, and verify approved WeChat Clone Flyway migrations for PostgreSQL with existing-data safety, version ordering, rollout compatibility, and recovery. Use for schema or data migration work; never rewrite applied versions or run production migrations without explicit authorization.
---

# Database Migration — WeChat Clone

## Activation and ownership

Use when a schema or approved data change belongs in a Flyway migration. Own version selection, schema-local SQL/data backfills, upgrade compatibility, and recovery. `data-modeling` decides schema semantics; `spring-boot-engineering` updates JDBC consumers; `data-quality-integrity` verifies invariants. Route long-running or cross-system data movement to `data-pipeline-engineering`.

## Reconnaissance

1. Read `AGENTS.md`, project workflow, approved scope, Git status, and relevant data-model/BA evidence.
2. Inspect the current `V*__*.sql` sequence in `wechatbackend/src/main/resources/db/migration`, Flyway configuration, affected JDBC repositories, tests, and deployment order.
3. The inspected repository has versions V1–V19, PostgreSQL/JDBC, and Maven `postgres-integration` tests. Recheck before choosing the next version; do not assume a local file proves which versions ran in production.
4. Determine what is known about existing rows, nulls, duplicates, lock duration, concurrent replicas, and old/new application compatibility.

## Migration protocol

1. Define the desired postcondition and preconditions, including row preservation and failure behavior.
2. Prefer a new forward Flyway version; do not edit an already-applied migration without explicit approval and a justified repair strategy.
3. Sequence additive schema, bounded schema-local backfill, constraint/index enforcement, and application cutover so mixed versions remain safe where required. Plan a separate pipeline for bulk or external data movement.
4. Evaluate transaction behavior, table locks, index cost, batch size, retry/idempotency, and large-table impact using actual PostgreSQL evidence.
5. Implement approved migration SQL and migration tests. Coordinate any JDBC repository change with `spring-boot-engineering`. Test on an isolated PostgreSQL database with representative rows, then verify both fresh install and upgrade path when feasible.
6. Document recovery/forward-fix steps; do not promise automatic down migration where Flyway or data semantics do not support it.

H2 or SQL syntax checks do not establish PostgreSQL migration safety. A test database does not prove production row distribution or lock duration.

## Quality gate and stop conditions

Completion requires unique version ordering, migration discovery, tested postconditions, preservation of existing rows, and a deploy/rollback decision. Mark unrun upgrade tests as gaps.

Never run destructive production operations or alter live data without explicit authorization. Stop if the migration may discard data, change ownership, require an unapproved contract cutover, or collide with another pending version. Do not print credentials or row contents, edit `.env`, deploy, or alter Git history.

## References

- Read [references/postgres-migration-safety.md](references/postgres-migration-safety.md) before writing or reviewing DDL, constraints, indexes, or backfills.

## Report and handoff

Report scope/result; migration version/files and rationale; compatibility and recovery; actual commands with PASS/FAIL/SKIPPED/BLOCKED; failed, blocked, and unexecuted checks; remaining data and rollout risks. Give P0/P1/P2 next steps with exact Skill, evidence, scope, and prerequisites, or state none.

Record SCOUT → BA → PLAN → COOK stage. PLAN selects this Skill only for an approved migration and coordinates `spring-boot-engineering`, `data-quality-integrity`, and `testing` as needed. COOK runs through the `cook` Skill after PLAN approval; do not invoke it automatically. Never invoke another Skill automatically.
