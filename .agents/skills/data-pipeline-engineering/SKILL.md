---
name: data-pipeline-engineering
description: Design or implement an explicitly requested WeChat Clone batch or ETL/ELT data movement workflow with retries, idempotency, checkpoints, lineage, and recovery. Activate only when a real workload exists; do not invent pipeline infrastructure.
---

# Data Pipeline Engineering — WeChat Clone

## Activation and ownership

Use only for a real, approved bulk or cross-system data movement, import/export, operational backfill, or ETL/ELT workload. Bounded schema-local data updates that run as part of Flyway belong to `database-migration`. The inspected repository has scheduled cleanup jobs and documentation for a legacy attachment backfill, but no verified general pipeline framework or analytics warehouse. Recheck current source before treating any of these as an active pipeline.

Own movement lifecycle and operational recovery. `database-migration` owns schema evolution, `data-quality-integrity` owns row/object invariants, and `wechat-storage-acceptance` owns private attachment storage boundaries.

## Reconnaissance

1. Read `AGENTS.md`, project workflow, current approved request, and Git status; preserve user changes.
2. Prove the workload exists: source/target, data contract, volume, schedule/trigger, ownership, retention, and consent or access requirements.
3. Inspect any existing cleanup/backfill code, JDBC/storage adapters, Compose/runtime limits, tests, and monitoring before choosing tools.
4. Distinguish a one-time migration, a retention job, and a recurring pipeline; do not reclassify cleanup jobs as ETL by default.

## Pipeline protocol

1. Define input/output schema and source of truth, including private object bytes when relevant.
2. Specify stable item identity, batching, checkpoint/resume, idempotent writes, retries, poison record handling, and partial-failure recovery.
3. Plan validation/reconciliation counts, lineage, observability, sensitive-data limits, and rollback or safe forward repair.
4. Evaluate the smallest existing tooling first. Introduce a framework, queue, or new service only with an explicit workload justification and approval.
5. For approved implementation, test with isolated representative data, interrupted runs, reruns, and no double processing; record actual evidence.

## Quality gate and stop conditions

A plan or implementation is ready only when duplicate/retry behavior, checkpoints, failure visibility, and reconciliation are specified and verified proportionally. Do not claim exactly-once behavior without end-to-end evidence.

Stop when no real workload or ownership exists; report not applicable. Never run a backfill, bulk export, destructive cleanup, production data operation, or remote storage transfer without explicit authorization. Do not expose credentials, private data, object keys, or signed URLs; do not deploy or alter sandbox policy.

## Report and handoff

Report scope/result; workload evidence or activation gap; source/target and recovery design; files changed/rationale; actual checks with PASS/FAIL/SKIPPED/BLOCKED; failed, blocked, and unexecuted checks; remaining risks. Give P0/P1/P2 next steps with exact Skill, reason, scope, and prerequisites, or state none.

Record SCOUT → BA → PLAN → COOK stage. BA must define data meaning; PLAN selects this Skill only for a real movement workload and may select `data-quality-integrity`/`database-migration` for related boundaries. COOK runs through the `cook` Skill after PLAN approval; do not invoke it automatically. Never invoke another Skill automatically.
