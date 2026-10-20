---
name: ba-analysis
description: "Analyze WeChat Clone feature requests, bug reports, and change requests into evidence-backed business requirements, scope, affected components, acceptance criteria, risks, alternatives, and a manual planning handoff. Use before implementation when behavior or scope needs definition. Do not use for coding, migrations, deployment changes, or automatic Skill invocation."
---

# WeChat Business Analysis

## Purpose

Translate a feature request, bug report, or change request into a decision-ready analysis for the WeChat Clone project.

The analysis must connect:

- the user's problem and expected outcome;
- confirmed current behavior;
- functional requirements and business rules;
- affected frontend, backend, data, realtime, storage, and external-service boundaries;
- verifiable acceptance criteria;
- risks, dependencies, assumptions, and unresolved questions;
- reasonable implementation alternatives;
- a structured handoff to the planning Skill.

This Skill defines what the system should accomplish and why. It does not implement how the code should be changed.

## Scope

Use this Skill for:

- ambiguous feature requests;
- bug reports requiring business-impact analysis;
- behavior changes;
- cross-layer changes;
- requirements and acceptance-criteria definition;
- comparison of multiple implementation approaches.

Do not use it for:

- direct implementation of an approved design;
- migrations, deployments, runtime repair, or data changes;
- pure repository orientation;
- focused tracing better handled by `wechat-flow-trace`;
- automatic invocation of another Skill.

## Preconditions

1. Start in the repository root.
2. Read `AGENTS.md`.
3. Read `.agents/projects/wechat-clone/WORKFLOW.md`.
4. Run `git status --short`.
5. Preserve all existing changes and untracked files.
6. Read a supplied Scout report before repeating discovery.
7. Use current source, current tests, and read-only runtime evidence for existing behavior.
8. Treat historical prompts and project context as guidance, not proof.

Ask a focused clarification only when missing information materially changes scope, authorization, data behavior, or acceptance criteria. Otherwise document an assumption and continue.

## Hard boundaries

Analysis is read-only.

Do not:

- modify application code, configuration, migrations, tests, or documentation;
- create or delete project files;
- run migrations, seeds, cleanup jobs, or data repair;
- modify databases, containers, volumes, buckets, or deployed services;
- upload files or send application messages;
- weaken authorization or bypass validation;
- expose credentials, tokens, object keys, private paths, or presigned URLs;
- claim tests or behavior are verified without evidence;
- automatically invoke Scout, planning, implementation, review, or runtime Skills.

Safe, focused read-only checks may be used when necessary. Report commands actually run and classify results as `PASS`, `FAIL`, `SKIPPED`, or `BLOCKED`.

## Evidence classification

Classify every material statement as one of:

- `Confirmed fact`: supported by current source, test output, or runtime evidence.
- `User requirement`: explicitly requested by the current user.
- `Assumption`: needed to continue but not confirmed.
- `Recommendation`: analyst judgment based on evidence and trade-offs.
- `Open question`: unresolved decision requiring user or stakeholder input.

Use this evidence precedence:

1. Current source and configuration.
2. Current test output.
3. Read-only runtime observations.
4. Current documentation.
5. Historical reports and prompts.

Do not treat documentation as runtime proof, skipped tests as passing, local MinIO as production S3 proof, or one backend process as cross-replica proof.

## Analysis workflow

### 1. Restate the request

Record:

- request type;
- affected user or operator;
- requested behavior;
- desired outcome;
- explicit constraints;
- supplied evidence;
- material ambiguity.

Separate the requested solution from the underlying problem.

### 2. Reuse Scout evidence

When a Scout report exists:

- record its scope and date;
- reuse its verified facts and references;
- preserve its confidence and limitation labels;
- re-check high-impact claims if the system may have changed;
- list evidence gaps affecting this analysis.

If no Scout report exists, inspect only the smallest relevant source paths. Do not silently perform a full architecture audit.

### 3. Define the business problem

Describe:

- who experiences the problem;
- the blocked, inefficient, or risky task;
- the current workaround or failure;
- the impact of not addressing it;
- the desired observable outcome;
- available success signals.

Do not invent personas, revenue impact, customer research, metrics, or regulatory obligations.

### 4. Define scope

State:

- in-scope behavior;
- out-of-scope behavior;
- non-goals;
- actors and permissions;
- rollout boundary, if known;
- dependencies required before implementation.

List related but deferred concerns separately. Do not absorb scope creep silently.

### 5. Identify affected components

Inspect only relevant boundaries:

- frontend routes, guards, components, stores, API services, and WebSocket consumers;
- backend controllers, DTOs, validation, services, repositories, storage adapters, and error mapping;
- REST contracts, pagination, authorization, and failure responses;
- database entities, Flyway migrations, indexes, constraints, and transactions;
- STOMP destinations, Redis delivery, reconnect, replay, and resynchronization;
- attachment lifecycle and storage authorization;
- gateway, Compose, health checks, external services, tests, and observability.

Classify each item as existing-confirmed, incomplete, configured-unverified, likely affected, or new.

### 6. Define requirements and business rules

Write observable requirements covering, where relevant:

- actors and preconditions;
- triggering action;
- expected result;
- state changes;
- authorization;
- validation;
- errors;
- persistence;
- events and notifications;
- frontend state;
- retry and recovery.

Make business rules explicit for eligibility, membership, ownership, lifecycle transitions, uniqueness, idempotency, ordering, visibility, privacy, rate limits, and retention.

Do not turn assumptions into requirements.

### 7. Analyze edge cases and failures

Cover relevant cases such as:

- invalid input;
- missing or stale resources;
- unauthorized access;
- duplicate requests;
- concurrent updates;
- partial failure;
- dependency timeout or outage;
- retry behavior;
- reconnect or missed realtime events;
- pagination boundaries;
- attachment or storage failure;
- cleanup and orphaned state;
- cross-replica behavior;
- sensitive-data exposure.

For each material failure, describe user-visible behavior, remaining state, retry safety, cleanup, and verification method.

### 8. Define acceptance criteria

Prefer:

```text
Given [precondition]
When [actor action]
Then [observable result]
And [state, authorization, event, or error condition]
```

Include relevant happy-path, validation, authorization, duplicate/conflict, persistence, realtime, frontend, and dependency-failure criteria.

Map each criterion to a verification surface. Do not mark criteria as passed unless direct evidence exists.

Each criterion must be observable and measurable; replace vague terms such as "fast", "easy", or "secure" with a threshold or a concrete behavior. Check user stories against INVEST (independent, negotiable, valuable, estimable, small, testable) and propose splitting a story that bundles independent outcomes. When scope must be cut, rank requirements as Must/Should/Could/Won't and record the reason.

### 9. Compare approaches

Present two or three genuinely different approaches only when a meaningful choice exists.

Compare:

- user and business fit;
- reuse of current architecture;
- implementation scope;
- API and data compatibility;
- authorization impact;
- realtime and failure behavior;
- operational complexity;
- testability;
- reversibility;
- deferred work.

Recommend the simplest approach satisfying confirmed objectives and constraints. State trade-offs and conditions that would favor another option.

### 10. Prepare the planning handoff

Provide:

- problem and expected outcome;
- scope and non-goals;
- confirmed current-system facts;
- requirements and business rules;
- acceptance criteria;
- affected files, modules, contracts, and data boundaries;
- alternatives and recommendation;
- risks, dependencies, assumptions, and open questions;
- verification questions for planning;
- security and authorization constraints.

This is not an implementation plan. Do not create a file-mutation manifest unless the planning Skill requests it.

## Standard output

Produce a report proportional to the request:

1. Request summary.
2. Business objective and expected outcome.
3. In-scope and out-of-scope items.
4. Existing behavior and evidence.
5. Functional requirements and business rules.
6. Affected components and integration points.
7. Edge cases and failure scenarios.
8. Acceptance criteria.
9. Non-functional requirements.
10. Risks, dependencies, assumptions, and unresolved questions.
11. Alternatives and recommended approach.
12. Suggested next step and planning handoff.

Omit irrelevant sections for small tasks and state why they were omitted.

## Routing boundaries

The next step is a manual handoff to the planning Skill when available.

Do not automatically invoke it.

If implementation or specialized verification is later approved, route manually to:

- frontend/API/auth integration -> `wechat-frontend-integration`;
- STOMP, Redis, notifications, reconnect, or resync -> `wechat-realtime-contract`;
- Spring REST, JDBC, PostgreSQL, Flyway, or transactions -> `wechat-spring-data-change`;
- attachment storage acceptance -> `wechat-storage-acceptance`;
- Compose, health, replicas, or deployment acceptance -> `wechat-runtime-verification`;
- focused cross-layer tracing -> `wechat-flow-trace`;
- implementation ownership -> `wechat_project_lead`.

## Mandatory manual handoff

After completing the analysis, present exactly these choices:

**A.** Accept the analysis and proceed to the planning Skill.

**B.** Refine the analysis according to the user's feedback.

**C.** Return to `scout` for further investigation.

**D.** Stop without modifying project files.

Recommend one option and explain why.

Wait for the user's explicit choice. Never automatically start the next Skill.
