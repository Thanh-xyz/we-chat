---
name: backend-performance
description: Investigate and improve measured WeChat Clone backend latency, throughput, query volume, pools, caching, concurrency, and resource use. Use when a reproducible performance problem or target exists; avoid speculative optimization.
---

# Backend Performance — WeChat Clone

## Activation and ownership

Use for an observed backend latency, throughput, resource, or scalability problem. Own measurement design and the smallest safe backend optimization. `sql-query-optimization` owns query plans and indexes; `realtime-engineering` owns event delivery semantics.

Do not run load tests against shared or production infrastructure without explicit authorization. Do not add caching, indexes, threads, or connection pool changes based only on intuition.

## Reconnaissance

1. Read `AGENTS.md`, project workflow, task target, Git status, and relevant prior evidence.
2. Define the operation, dataset shape, traffic/concurrency, baseline metric, target, and safe measurement environment.
3. Inspect the actual code path, JDBC calls, query count, pool/executor settings, cache ownership, retry behavior, and existing Micrometer/Actuator instrumentation.
4. Verify the source of each metric; Prometheus dependency/configuration does not prove a live monitoring deployment.

## Investigation protocol

1. Reproduce or obtain trustworthy measurements for latency distribution, throughput, resource use, and error rate as appropriate.
2. Locate the bottleneck: query plan/round trips, N+1 access, thread or connection saturation, serialization, lock contention, cache misses, or external service delay.
3. Compare candidate fixes by expected effect, correctness risk, memory/operational cost, and rollback. Prefer the smallest change with a falsifiable hypothesis.
4. For approved implementation, change one factor at a time, preserve behavior, and add focused regression checks.
5. Re-measure in a comparable environment and report both before and after; label unavailable measurements inconclusive.

Cache changes must consider stale authorization/data, invalidation, cross-replica behavior, and Redis failure. Query optimizations must preserve filters, authorization, pagination, and ordering.

## Quality gate and stop conditions

An optimization is accepted only with measured benefit or a clearly justified correctness/resource safeguard, plus no observed regression in relevant tests. Do not equate a synthetic unit benchmark with production performance.

Stop if the target, workload, or baseline is missing and would change the recommendation; if testing would affect shared state; or if a fix needs unapproved schema, API, security, or infrastructure changes. Never expose secrets, modify `.env`, deploy, or alter sandbox policy.

## Report and handoff

Report scope/result; workload and measurement method; baseline and comparison with limitations; files changed; commands actually run with PASS/FAIL/SKIPPED/BLOCKED; failed, blocked, and unexecuted checks; remaining risks. Give P0/P1/P2 next steps with exact Skill, evidence, scope, prerequisites, or state none.

Record SCOUT → BA → PLAN → COOK stage. PLAN selects `backend-performance` only when a performance criterion exists, and adds `sql-query-optimization` for SQL/index work. COOK runs through the `cook` Skill after PLAN approval; do not invoke it automatically. Never invoke another Skill automatically.
