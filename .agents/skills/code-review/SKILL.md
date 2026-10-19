---
name: code-review
description: "Perform a read-only, evidence-backed review of WeChat Clone changes for correctness, security, contracts, data integrity, concurrency, regressions, and test gaps. Report actionable P0-P3 findings and a manual handoff. Do not modify code or automatically invoke another Skill."
---

# WeChat Code Review

Review the actual requested changes and their surrounding implementation before merge.

This Skill defines the review procedure. It complements the wechat_code_reviewer Agent and the domain Skills. It does not replace them, implement fixes, run broad architecture audits, or automatically invoke another Skill.

## Use this Skill for

- reviewing a working-tree diff, branch diff, commit range, or supplied patch;
- checking correctness and regression risk before merge;
- reviewing authentication, authorization, privacy, API, database, realtime, storage, frontend, or runtime changes;
- identifying missing verification and unresolved risks;
- producing actionable findings tied to current source evidence.

Do not use it for:

- implementing or fixing findings;
- broad repository onboarding without a change under review;
- writing an implementation plan;
- running a general test campaign;
- staging, committing, pushing, reverting, or resetting Git state;
- automatically invoking scout, ba-analysis, plan, testing, a domain Skill, or an Agent.

## Safety boundaries

Review is read-only by default.

Do not:

- modify application code, tests, configuration, migrations, documentation, or Skill files;
- stage, commit, push, revert, reset, or discard changes;
- install dependencies or alter system configuration;
- run migrations, backfills, cleanup, destructive tests, or production writes;
- modify databases, buckets, volumes, containers, or shared services;
- expose passwords, tokens, cookies, credentials, private object keys, presigned URLs, or sensitive user data.

If a verification command could affect a database, container, bucket, or shared service, assess the impact first. Ask for approval before any potentially destructive or shared-state operation.

## Evidence precedence

Prefer evidence in this order:

1. Current source and configuration.
2. Current diff and surrounding source.
3. Tests actually run during this review.
4. Current read-only runtime observations.
5. Current project documentation.
6. Historical prompts and reports.

Do not treat comments, commit messages, historical claims, generated summaries, skipped tests, or documentation alone as proof of current behavior.

Use these labels where useful:

- CONFIRMED: directly supported by current source or executed verification;
- RISK: plausible concern requiring focused verification;
- UNVERIFIED: relevant area not safely or currently testable;
- BLOCKED: verification could not run because of environment, credentials, Docker, permissions, or shared-state constraints.

## Review workflow

### 1. Establish the review contract

Record:

- requested change and intended outcome;
- acceptance criteria or linked requirement;
- base and target commits, branch, or working tree;
- in-scope files and components;
- out-of-scope pre-existing changes;
- required evidence;
- known environment limitations.

If the intended change or comparison base is materially ambiguous, ask one focused clarification. Otherwise make a bounded assumption and label it.

### 2. Establish repository state

Start from the repository root.

Read:

- AGENTS.md;
- .agents/projects/wechat-clone/WORKFLOW.md;
- supplied Scout, BA, plan, or testing reports when available;
- the relevant domain Skill only when its review procedure applies.

Run:

    git status --short
    git diff --check

Inspect the appropriate comparison:

- git diff for unstaged tracked changes;
- git diff --cached for staged changes;
- the requested base/target diff when commits are supplied;
- untracked files explicitly when they are part of the task.

Separate the requested change from unrelated pre-existing modifications. Do not review every untracked file merely because it exists.

### 3. Inspect the diff and surrounding code

Review the diff first, then inspect enough surrounding source to verify:

- callers and consumers;
- validation and error mapping;
- authorization and identity checks;
- persistence and transaction boundaries;
- event publication and frontend consumers;
- tests and configuration that define the contract.

Do not review only changed lines when surrounding code determines behavior.

Do not assume a changed comment, test name, or documentation statement matches implementation.

### 4. Trace material flows

For REST or API changes, trace:

    frontend caller → controller → DTO/validation → service
      → repository/storage → transaction/error mapping → response
      → frontend state and error handling

For database changes, inspect:

- Flyway version and whether an applied migration is being edited;
- tables, constraints, indexes, and existing rows;
- JDBC predicates and pagination;
- transaction boundaries;
- concurrent update and idempotency behavior;
- rollback or recovery implications.

For authentication and authorization, inspect:

- token issuance, validation, refresh, logout, revocation, and account state;
- method authorization and ownership/membership predicates;
- REST and STOMP authorization;
- CORS, gateway, forwarded-header, and rate-limit assumptions;
- private attachment access and object-key boundaries.

For realtime changes, inspect:

- transactional writer;
- after-commit publication;
- STOMP destination policy;
- Redis Pub/Sub bridge;
- local broker delivery;
- duplicate suppression;
- reconnect and resynchronization;
- ordering, loss, and replay assumptions.

Redis Pub/Sub is transient and does not provide replay or durable ordering unless the current implementation proves otherwise.

For attachment changes, inspect:

- initiation, upload, completion, validation, and lifecycle state;
- server-side byte and metadata validation;
- download and deletion authorization;
- storage adapter behavior;
- cleanup and orphan handling;
- cross-replica access.

Local MinIO or S3-compatible tests do not prove Supabase production behavior.

For frontend changes, inspect:

- React component/page behavior;
- Zustand or feature-store state transitions;
- API request lifecycle;
- access-token and refresh handling;
- loading, retry, cancellation, and error states;
- STOMP connection, reconnect, resync, and deduplication;
- attachment URL and upload lifecycle handling.

For runtime changes, inspect:

- Compose service dependencies;
- gateway routing;
- health and readiness checks;
- environment configuration;
- replica assumptions;
- observability and failure handling.

### 5. Evaluate review dimensions

Apply only the dimensions relevant to the requested change.

Review in two passes when a plan or requirement exists: first **spec compliance** (everything requested is present, nothing unrequested was added, and the summary matches the diff), then **code quality**. Read the code rather than trusting the implementer's report.

Prioritize:

1. correctness and regressions;
2. authentication, authorization, privacy, and security;
3. data integrity, transactions, concurrency, and idempotency;
4. API, database, and event-contract compatibility;
5. error handling and failure behavior;
6. performance and resource usage;
7. test adequacy and failure-path coverage;
8. maintainability and project consistency.

Do not turn style preferences into defects. Mention maintainability only when it creates a concrete correctness, security, operational, or future-change risk.

### 6. Check verification coverage

Inspect relevant existing tests and determine whether they cover:

- the changed happy path;
- invalid input and authorization failures;
- duplicate or concurrent requests;
- persistence and rollback behavior;
- API or event compatibility;
- frontend loading and error states;
- reconnect, resync, duplicate delivery, or cross-replica behavior;
- attachment lifecycle and unauthorized access;
- runtime configuration and health behavior.

Safe focused checks may be run when proportionate, such as:

    cd wechatbackend && mvn test
    cd wechatfrontend && npm test
    cd wechatfrontend && npm run lint
    cd wechatfrontend && npm run build

Use PostgreSQL, Redis, S3, browser, Compose, or cross-replica checks only when relevant and safe. Do not claim a check was run unless it was actually executed.

Report every check as:

- PASS: executed and expected evidence observed;
- FAIL: executed and assertion or behavior failed;
- SKIPPED: intentionally not run or skipped by configuration;
- BLOCKED: could not run because of an external prerequisite.

A passing test suite does not prove that the implementation is correct or secure.

### 7. Classify findings

Use one severity for each actionable finding.

- P0: release-blocking critical issue such as an exploitable authorization bypass, credential exposure, corruption/data-loss path, or likely severe production outage with no safe workaround.
- P1: high-impact correctness, security, contract, transaction, concurrency, or regression defect that affects a normal or important path and should block merge.
- P2: material defect or realistic edge-case risk with limited scope, an important failure-path gap, or a meaningful compatibility/testability issue.
- P3: low-impact but actionable maintainability, observability, or narrow behavior issue. Do not use P3 for pure style preference.

Severity is separate from confidence. A finding can be P1 — RISK when the scenario is serious but needs focused verification.

### 8. Validate and deduplicate findings

Before reporting a finding:

- confirm it is present in the current change or its direct integration path;
- verify the file and line range when possible;
- identify the triggering scenario;
- explain the likely impact;
- distinguish confirmed defects from risks needing a test;
- check that the finding is not already fixed elsewhere in the diff;
- merge duplicate symptoms into one actionable finding;
- prefer a narrow correction over a broad rewrite.

If no actionable finding is supported, state that explicitly. Do not claim the change is defect-free.

Only objective, provable gaps are blocking (P0/P1). Do not inflate preferences or nitpicks into high severities, and do not write "looks good" for areas you did not inspect. Briefly note well-handled risky areas so later reviews do not re-litigate them.

## Finding format

For each finding, use:

    ### P1 — Short actionable title

    - Location: path/to/file:line-line when verifiable
    - Status: CONFIRMED, RISK, UNVERIFIED, or BLOCKED
    - Scenario: concrete request, input, race, failure, or deployment condition
    - Impact: user, security, data, contract, operational, or regression consequence
    - Evidence: current diff, source path/symbol, test output, or runtime observation
    - Recommendation: smallest correction or focused verification

Order findings from highest severity to lowest. Do not include secrets or sensitive payloads.

## Required report

Produce a concise report with:

1. Review scope and base/target comparison.
2. Summary and overall risk.
3. Findings ordered by severity.
4. Evidence and affected files.
5. Checks actually executed and outcomes.
6. Unverified areas and environmental limitations.
7. Residual risks and recommended next action.

Begin with a verdict such as PASS, FAIL, or BLOCKED, and explain what that verdict means for the reviewed scope.

Keep confirmed findings, risks, assumptions, and recommendations visibly distinct.

## Manual handoff

After presenting the review, offer exactly these choices:

**A — Fix findings:** recommend an implementation workflow, but do not edit code.

**B — Verify a finding:** propose a safe focused investigation and wait for approval before executing anything that needs approval.

**C — Expand review:** review additional files or risks if the current scope is insufficient.

**D — Stop:** preserve the findings and finish without further action.

Recommend one option based on the findings and wait for the user's explicit choice.

Never invoke another Skill, Agent, or implementation process automatically.
