---
name: analytics-data-engineering
description: Define and build explicitly requested WeChat Clone analytics metrics, aggregations, reporting datasets, and workload isolation. Activate only with approved metric definitions and a real reporting need; do not assume a warehouse exists.
---

# Analytics Data Engineering — WeChat Clone

## Activation and ownership

Use for a requested KPI, reporting dataset, historical aggregation, or analytical query. The inspected repository has transactional PostgreSQL data and Micrometer operational metrics, but no verified analytics warehouse or reporting pipeline. Recheck before designing an analytics system.

Own metric semantics, grain, dimensions, aggregation, freshness, lineage, and analytical workload boundaries. `frontend-ui-ux` owns dashboard interaction design and `frontend-engineering` owns client implementation. `data-pipeline-engineering` owns recurring movement; `sql-query-optimization` owns query-plan tuning; `data-modeling` owns transactional schema semantics.

## Reconnaissance

1. Read `AGENTS.md`, project workflow, current request/BA output, and Git status; preserve existing changes.
2. Obtain an explicit metric definition: business question, numerator/denominator, population, time zone/window, exclusions, refresh lag, and authorized audience.
3. Inspect verified source tables/events, retention rules, privacy constraints, query patterns, tests, and infrastructure. Do not infer durable event history from Redis Pub/Sub.
4. Separate operational metrics from product/business analytics and identify missing historical data.

## Analytics protocol

1. Specify dataset grain, keys, dimensions, measures, provenance, and late/corrected data handling.
2. Compare direct bounded queries, pre-aggregation, and a separate analytical store only when volume and freshness evidence justify a choice.
3. Evaluate impact on transactional PostgreSQL: query load, locks, indexes, retention, replication, and access control.
4. Define reconciliation against source records, privacy checks, and representative acceptance examples.
5. Implement only an approved scope; verify metric math, edge windows, permissions, and performance in an isolated environment before claiming the report works.

Do not invent business metrics, historical events, warehouse tables, or user identifiers. If definitions or source data are missing, return an explicit open question or data gap.

## Quality gate and stop conditions

A deliverable is ready when stakeholders can reproduce the metric formula, trace it to source evidence, and see freshness/coverage limits. A chart rendering does not prove correct aggregation.

Stop before adding an analytics service, warehouse, ETL pipeline, production read load, sensitive export, or retention change without explicit approval. Never print private row contents or credentials, edit `.env`, deploy, or alter sandbox policy.

## Report and handoff

Report scope/result; metric definitions and source lineage; files changed or proposed with rationale; actual commands and PASS/FAIL/SKIPPED/BLOCKED; failed, blocked, and unexecuted checks; missing history, privacy and performance risks. Recommend P0/P1/P2 next steps with exact Skill, reason, scope, and prerequisites, or state none.

Record SCOUT → BA → PLAN → COOK stage. BA confirms metric meaning; PLAN selects this Skill only for an analytics requirement and adds `data-pipeline-engineering` only if movement is justified. COOK runs through the `cook` Skill after PLAN approval; do not invoke it automatically. Never invoke another Skill automatically.
