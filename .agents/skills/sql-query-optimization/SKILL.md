---
name: sql-query-optimization
description: Diagnose and improve measured WeChat Clone PostgreSQL JDBC queries, plans, indexes, joins, filters, sorting, and pagination. Use for query performance or correctness tied to evidence; coordinate schema changes through database-migration.
---

# SQL Query Optimization — WeChat Clone

## Activation and ownership

Use for a specific slow or high-volume SQL path, plan regression, missing index hypothesis, or pagination/query correctness issue. Own SQL and plan analysis. `backend-performance` owns the overall workload and service-level measurement; `database-migration` owns index/schema rollout; `api-contract-design` owns any public pagination contract change.

## Reconnaissance

1. Read `AGENTS.md`, project workflow, task target, Git status, and relevant prior measurements.
2. Inspect the actual JDBC repository query, call frequency, parameters, DTO mapping, migration-defined indexes/constraints, and test fixture.
3. Identify PostgreSQL version and safe database context. The inspected Compose uses PostgreSQL 17, but confirm the database under test; never assume production matches Compose.
4. Define baseline latency, row counts/selectivity, expected result order, authorization predicate, and dataset scale. Mark unknown values as unknown.

## Query protocol

1. Reproduce the query safely with representative parameter shapes. Inspect `EXPLAIN` and, only in an isolated authorized environment, timing/buffer evidence where needed.
2. Check scan/join/sort cost, N+1 call patterns, index selectivity, filtering, and keyset or offset pagination semantics.
3. Compare query rewrite, batching, and index alternatives for performance, write amplification, lock/build cost, and correctness.
4. Keep tenant/user/conversation predicates, ordering, page boundaries, cursor opacity, and transaction behavior intact.
5. For approved changes, make a small query edit or hand an index to `database-migration`; test exact result sets, boundary pages, and relevant authorization cases, then compare plans and timing.

Do not run `EXPLAIN ANALYZE` on data-changing statements or a shared/production database without explicit authorization. Do not add an index based on an unmeasured guess or claim production speedup from tiny fixtures.

## Quality gate and stop conditions

Report plan and measurement evidence, correctness checks, and operational cost. If production-like data or a safe execution context is unavailable, provide a bounded hypothesis rather than a claimed improvement.

Stop before changing pagination/API semantics, adding costly indexes, rewriting applied migrations, reading private row contents, or loading shared infrastructure beyond approval. Do not expose secrets, deploy, or alter sandbox policy.

## References

- Read [references/postgres-diagnostics.md](references/postgres-diagnostics.md) for measurement commands, common causes, and change rules.

## Report and handoff

Report scope/result; original query and plan evidence without sensitive parameter values; change or proposal; files changed/rationale; actual checks with PASS/FAIL/SKIPPED/BLOCKED; failed, blocked, and unexecuted checks; residual risks. Recommend P0/P1/P2 next steps with exact Skill, evidence, scope, and prerequisites, or state none.

Record SCOUT → BA → PLAN → COOK stage. PLAN selects this Skill for SQL evidence and `database-migration` if an index/schema change is approved; `testing` checks PostgreSQL behavior. COOK runs through the `cook` Skill after PLAN approval; do not invoke it automatically. Never invoke another Skill automatically.
