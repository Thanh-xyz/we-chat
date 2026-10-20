---
name: plan
description: "Turn an approved WeChat requirement, BA analysis, or Scout report into an evidence-backed, file-level implementation plan saved as Markdown under docs/plans by date, with dependencies, verification, risks, rollback, and a manual handoff. Use before implementation; never modify application files or invoke another Skill automatically."
---

# WeChat implementation planning

## Purpose

Turn an approved requirement, BA analysis, Scout report, or well-specified change request into a practical technical implementation plan for the current WeChat Clone repository.

The plan explains:

- the objective, scope, non-goals, and measurable acceptance criteria;
- the verified current behavior and affected contracts;
- the files, symbols, data paths, and runtime components involved;
- the ordered implementation tasks and their dependencies;
- the testing, rollout, operational, and rollback strategy.

This Skill plans implementation and saves the complete plan as a Markdown artifact in the repository. It does not implement application changes, invoke another Skill, or start an agent automatically.

## Use this Skill for

- an approved feature or bug-fix scope that needs file-level sequencing;
- a Scout report ready for implementation planning;
- a BA analysis with defined requirements and acceptance criteria;
- cross-layer work involving REST, PostgreSQL, Flyway, STOMP, Redis, storage, frontend, or Compose;
- changes needing explicit dependency, verification, rollout, or rollback decisions.

Use `scout` for broad reconnaissance, `ba-analysis` for unresolved business requirements, and `brainstorm` for open-ended design exploration. Use the domain Skills for specialized implementation or verification.

## Preconditions and safety

1. Start in the repository root.
2. Read `AGENTS.md` and `.agents/projects/wechat-clone/WORKFLOW.md`.
3. Run `git status --short` and preserve all existing changes and untracked files.
4. Read the current request and any supplied Scout or BA report before repeating discovery.
5. Inspect the actual source, tests, configuration, and relevant documentation.

The only project-file write allowed during planning is the plan artifact described below. Do not:

- modify application code, tests, migrations, configuration, existing documentation, or Skill files;
- overwrite or edit an existing plan artifact;
- create helper scripts, agents, or additional Skills;
- install packages or change system configuration;
- modify databases, buckets, containers, volumes, or deployed services;
- run migrations, backfills, data repair, uploads, seeds, or destructive commands;
- reset Git state, discard changes, weaken authorization, or expose secrets.

Safe read-only inspection and proportionate checks are allowed. Report commands actually run and classify results as `PASS`, `FAIL`, `SKIPPED`, or `BLOCKED`. Never claim the plan was implemented or tested.

## Plan artifact and chat response

Save each completed plan as a new Markdown file under:

```text
docs/plans/YYYY/MM/DD/<short-task-slug>.md
```

Use the current date from the user's/session's local time context for the year, month, and day folders. Use a short descriptive kebab-case filename. Create missing date folders as needed. Never overwrite an existing file; if the chosen filename exists, add a numeric suffix such as `-2` and check again. Preserve all other working-tree changes.

Write the complete plan in Vietnamese, including checks performed during PLAN, specialist routing, approval gates, and manual handoff. Keep technical identifiers, code symbols, API paths, commands, and skill names in their canonical form, and explain them in Vietnamese. Verify that the file was written before responding. In chat, do not reproduce or summarize any plan section: provide only a Markdown link to the saved file and one concise Vietnamese sentence stating the recommended next step from its handoff. Wait for the user's explicit decision; do not start the next workflow stage automatically.

## Evidence rules

Label material statements as one of:

- `VERIFIED-SOURCE` — current source or configuration;
- `VERIFIED-TEST` — a test actually run for this task;
- `VERIFIED-RUNTIME` — a current read-only runtime observation;
- `DOCUMENTED` — current documentation, not independently verified;
- `HISTORICAL` — an old prompt or report, not current proof;
- `ASSUMPTION` — needed to continue but not confirmed;
- `RECOMMENDATION` — planning judgment;
- `OPEN QUESTION` — requires a user or stakeholder decision;
- `BLOCKED` — safe verification was not possible.

Prefer evidence in this order: current source/configuration, current test output, current runtime observations, current documentation, then historical material. Do not turn assumptions, documentation, or historical claims into implementation facts.

## Consume Scout and BA outputs

### Scout

Reuse Scout's scope, date, evidence labels, confidence, exact paths, symbols, tests, blockers, and A–H report sections. Re-check high-impact claims that may have changed. Do not repeat a full audit unless the requested change requires it. Scout owns reconnaissance; this Skill owns sequencing and implementation design.

### BA analysis

Use the BA objective, scope, non-goals, rules, acceptance criteria, alternatives, risks, assumptions, and open questions. Do not invent business requirements or convert assumptions into requirements. Verify technical impact against current source. BA owns what the system should accomplish and why; this Skill plans how the approved scope can be implemented and verified.

If current source conflicts with documentation or historical reports, show the conflict and prefer current source plus the current user request.

## Planning workflow

### 1. Establish the task contract

Record:

```text
Task ID:
Owner:
Reviewer:
Objective:
In scope:
Out of scope:
Preconditions:
Acceptance criteria:
Files/components to inspect:
Evidence required:
Safety constraints:
```

Ask one focused clarification only when an unresolved decision materially changes scope, authorization, data behavior, compatibility, or acceptance. Otherwise record a bounded assumption.

### 2. Map the verified current state

Inspect only relevant paths, but trace the complete contract when a boundary is crossed:

- frontend route/page/component, store, API service, token/session handling, and WebSocket consumer;
- backend controller, DTO/validation, service, JDBC repository, storage adapter, error mapping, and transaction;
- authentication, membership, method authorization, and STOMP destination authorization;
- Flyway migration, table, constraint, index, query, concurrency, and after-commit behavior;
- STOMP publisher, Redis delivery, reconnect/resynchronization, ordering, and loss behavior;
- attachment lifecycle, private storage authorization, cleanup, and legacy MinIO boundary;
- Compose service, gateway route, health check, replica assumption, and deployment dependency;
- focused tests, build/lint commands, metrics, logs, and observability.

Use exact paths and symbols only when verified. Mark proposed files as `NEW FILE PROPOSED — not present in the current repository`. Verify project-specific facts on every task; do not blindly rely on historical Spring Boot, PostgreSQL, Redis, STOMP, storage, frontend, or Docker claims.

For API or event changes, trace:

```text
entry point → validation/DTO → service → repository/storage
  → transaction/after-commit behavior → response/event contract
  → frontend consumer → relevant tests
```

### 3. Select the design

State the preferred design and important decisions for API/event contracts, data, transactions, authorization, frontend state, failure behavior, compatibility, and rollout.

Compare two or three genuinely different approaches only when a meaningful choice exists. Compare scope, architecture reuse, compatibility, authorization, realtime behavior, operational complexity, testability, reversibility, and deferred work. Recommend the simplest option satisfying confirmed requirements and explain when another option wins.

### 4. Order implementation tasks

Every task must be file-level and map to acceptance criteria:

```markdown
### P1 — [task title]

- Objective:
- Files/symbols:
- Existing or new:
- Change intent:
- Depends on:
- Can run in parallel with:
- Acceptance criteria covered:
- Verification:
- Risks/rollback:
```

Include a dependency table:

| ID | Task | Depends on | Parallel group | Blocking reason |
|---|---|---|---|---|

Order contract, schema, backend, frontend, runtime, and verification work according to actual dependencies. Do not parallelize coupled contract changes. For schema work, propose a new Flyway version rather than editing an applied migration; consider existing rows, constraints, indexes, replicas, transactions, and recovery.

### 5. Plan verification

Map every acceptance criterion to a measurable verification method:

| ID | Criterion | Verification method | Required environment | Expected evidence |
|---|---|---|---|---|

Consider only relevant layers: unit/service/repository, API, PostgreSQL/Flyway, frontend, lint/build, security/authorization, STOMP/Redis, storage, cross-replica runtime, regression, browser/manual, and observability/failure checks.

Separate planned commands from commands actually run. Mark unavailable credentials, Docker, services, or browser access as `BLOCKED`; a skipped test is not a pass. H2 does not prove PostgreSQL compatibility, local S3/MinIO does not prove Supabase behavior, one backend does not prove cross-replica behavior, and Redis Pub/Sub does not prove replay or durable ordering.

### 6. Cover operational concerns proportionally

Address applicable concerns: backward compatibility, authorization, data integrity, idempotency, concurrency, pagination/order, performance, resource limits, observability, sensitive-data boundaries, deployment order, health/readiness, rollout controls, migration/backfill safety, failure recovery, and rollback. State `N/A` with a reason when a concern does not apply; do not force architecture work into a small task.

## Required plan structure

Write a proportional report in the plan artifact with:

1. Objective and acceptance criteria
2. Scope and non-goals
3. Verified current state and constraints
4. Proposed design and important decisions
5. Files and components affected
6. Ordered implementation tasks with dependencies
7. Specialist routing record and approval gates
8. Testing and validation strategy
9. Security, compatibility, data migration, and operational considerations
10. Risks, alternatives, assumptions, unresolved questions, and rollback plan
11. Definition of done

The specialist routing record (section 7) follows "Mandatory routing record" in `.agents/projects/wechat-clone/WORKFLOW.md` and uses its `## Specialist routing` handoff block. It records:

- each affected layer or component, with its SCOUT evidence as `path:symbol`;
- each selected specialist Skill, its purpose, and the stage it applies in;
- dependencies and implementation order;
- the verification Skill and the expected evidence;
- relevant Skills that were considered but excluded, and why;
- the approval gates.

The routing table in `WORKFLOW.md` is a menu, not a checklist. Select Skills from repository evidence and actual dependencies.

Record authentication, authorization, schema, migration, realtime, storage, and infrastructure as affected or not affected, with evidence for each. If evidence cannot establish the affected layers, recommend returning to `scout` instead of guessing.

The report must distinguish verified facts, assumptions, recommendations, unresolved questions, planned tests, and tests actually run. Keep it specific to the requested change and do not invent files, symbols, behavior, test results, or infrastructure state.

## Definition of done

The plan is ready for approval when:

- objective, scope, non-goals, and measurable acceptance criteria are explicit;
- material current-state claims have evidence labels and exact references;
- affected boundaries and dependencies are identified where relevant;
- file-level tasks show ordering, dependencies, parallel work, and verification;
- security, compatibility, data integrity, concurrency, performance, observability, deployment, and rollback are covered when applicable;
- every acceptance criterion has a verification method;
- the four PLAN deliverables required by `WORKFLOW.md` are complete:
  - a dependency-ordered implementation plan;
  - the specialist routing record;
  - a verification matrix mapping each criterion to its owner and its check;
  - a list of approval gates;
- assumptions and open questions are visible;
- no project file other than this new plan artifact was modified during planning.

## Manual handoff

In the plan artifact, provide exactly these choices:

**A — Approve plan:** wait for explicit approval of all four PLAN deliverables before COOK or any other implementation workflow begins.

**B — Revise plan:** ask what needs to change.

**C — Return to analysis:** recommend `scout` or `ba-analysis` if evidence or requirements are insufficient.

**D — Stop:** finish without implementing anything.

Recommend one choice based on the findings. Never execute it automatically, invoke another Skill, or start an agent. Approval of a plan is not blanket authorization for unrelated changes.
