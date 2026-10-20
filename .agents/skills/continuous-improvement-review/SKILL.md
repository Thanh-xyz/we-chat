---
name: continuous-improvement-review
description: "Own the REVIEW stage and proportionate improvement checkpoints for WeChat Clone changes: independently challenge a meaningful diff against its approved plan and acceptance criteria, report evidence-backed findings with severity and confidence, track their remediation and re-verification, and recommend the next stage or specialist Skill. Review-and-recommend by default; do not modify code unless remediation was explicitly authorized."
---

# Continuous Improvement Review — WeChat Clone

## Purpose and ownership

Find weaknesses that tests and builds do not reveal, decide whether a change is ready to finish, and route each remaining issue to the right owner.

This Skill owns:

- the REVIEW stage of `SCOUT → BA → PLAN → COOK → TEST → REVIEW` (see `.agents/projects/wechat-clone/WORKFLOW.md`);
- lightweight improvement checkpoints requested during COOK or TEST;
- conformance of the final diff to the approved plan, scope, and acceptance criteria;
- the lifecycle of findings: open, fixed, partially fixed, unverified, accepted risk;
- the decision to finish, return to COOK, or return to PLAN.

It delegates rather than duplicates:

- defect-tracing procedure for REST, database, auth, realtime, attachment, frontend, and runtime changes → follow the trace checklists in `code-review` §4;
- deep domain judgment → the owning specialist (routing table below), consulted only for the dimensions actually affected;
- test execution and acceptance-criteria verification → `testing` and `frontend-quality-gate` (TEST stage);
- Git state changes → `git-workflow`.

`code-review` remains the standalone read-only review for any diff, branch, or PR outside this workflow. When both apply, this Skill uses `code-review`'s procedure and adds plan conformance, finding lifecycle, and stage routing.

## Preconditions

1. Read `AGENTS.md` and `.agents/projects/wechat-clone/WORKFLOW.md`.
2. Run `git status --short` and `git diff --check`; separate the change under review from pre-existing modifications and untracked files.
3. Read the approved task contract, BA acceptance criteria, PLAN output, COOK deviation notes, and TEST report when available. If no approved plan or acceptance criteria exist, review against the user's request and label the review `NO-PLAN BASELINE`.
4. Identify the comparison: unstaged diff, staged diff, branch against base (`git merge-base`), or supplied patch.

Never review only the implementer's summary. Inspect the actual diff and the surrounding code it depends on.

## Remediation mode

Default: **review and recommend**. Do not edit files.

Remediation is allowed only when the current user request explicitly authorizes fixing review findings within the approved scope. Then:

1. Fix only findings whose remedy is inside the approved file and contract scope.
2. Follow the owning specialist Skill's implementation rules for that fix.
3. Rerun the checks that the fix affects, then re-review the changed lines and their direct callers.
4. Report each fix with its evidence.

Even when remediation is authorized, never: expand scope silently, rewrite unrelated modules, add or edit migrations or production data without separate authorization, weaken authentication/authorization/validation, change public contracts outside the plan, edit `.env`, install dependencies, deploy, stage, commit, or push.

## Review protocol

### 1. Establish scope

Record the task, stage, base/target, files in the change, affected layers (frontend, backend, data, realtime, storage, runtime), and the acceptance criteria being reviewed.

### 2. Map affected boundaries

From the diff, trace outward to callers, consumers, contracts, transactions, events, and tests. For each crossed boundary, use the matching `code-review` §4 trace. Do not trace layers the change cannot affect.

### 3. Select dimensions by risk

Review only relevant dimensions, but go deep on high-risk ones:

| Dimension | Review when the change touches | Specialist to consult |
|---|---|---|
| Functional correctness, edge cases | any behavior | owning implementation Skill |
| Security, authorization, privacy | identity, membership, tokens, rate limits, STOMP policy, attachment access | `backend-security` |
| API and cross-layer contract | DTOs, status/error mapping, pagination, client callers | `api-contract-design`, `frontend-integration-review` |
| Architecture, maintainability | package boundaries, new abstractions, dependency direction | `backend-architecture`, `frontend-architecture`, `architecture-review` |
| Data integrity, transactions | writes, constraints, migrations, idempotency, concurrency | `data-quality-integrity`, `database-migration`, `data-modeling` |
| Query efficiency | SQL, indexes, pagination queries | `sql-query-optimization` |
| Performance, resources | hot paths, caching, pools, executors, loops over I/O | `backend-performance` |
| Realtime delivery, consistency | STOMP, Redis Pub/Sub, after-commit events, reconnect/resync | `realtime-engineering`, `wechat-realtime-contract` |
| Storage | upload/finalize/download, object lifecycle | `wechat-storage-acceptance` |
| UI/UX, responsive, accessibility | components, forms, states, styling | `frontend-ui-ux`, `frontend-design-system` |
| Test adequacy, regression risk | every meaningful change | `testing`, `frontend-quality-gate` |
| Build, config, deploy, operations | Compose, gateway, env names, health, profiles | `wechat-runtime-verification` |

Domain checklists, read only for affected dimensions: `backend-security/references/security-checklist.md`, `api-contract-design/references/rest-conventions.md`, `database-migration/references/postgres-migration-safety.md`, `sql-query-optimization/references/postgres-diagnostics.md`, `frontend-engineering/references/react-performance.md`, `frontend-ui-ux/references/accessibility-checklist.md`, and `testing/references/test-discipline.md`. A `disputed` finding was defended with evidence by the implementer (see `cook/references/review-findings.md`); the user arbitrates it.

### 4. Review independently

- Check spec compliance before quality: every acceptance criterion maps to code and evidence, nothing unrequested was added, and the implementer's summary matches the diff. Flag unplanned files, unexplained behavior changes, and dead or speculative code as scope drift.
- Challenge the happy path with invalid input, unauthorized actors, duplicates, concurrency, partial failure, retries, reconnect, and rollback as relevant.
- Check that tests assert the claimed behavior, including negative paths, rather than only executing it.
- For each suspected issue, find the exact location and a concrete triggering scenario before reporting it.

### 5. Classify and rank

Rank findings by impact × likelihood. Merge duplicate symptoms into one finding. Separate:

- **introduced** by the change under review;
- **pre-existing** and only exposed or touched by it;
- **pre-existing and unrelated** — record as an out-of-scope follow-up and do not treat it as blocking this change.

### 6. Propose remediation

For each finding give the minimal safe fix and, only when justified, a more comprehensive option with its trade-off. If the only adequate fix changes requirements, a public contract, schema, security boundary, or the approved file scope, mark it `RETURN TO PLAN`.

### 7. Verify and re-review

After approved fixes, confirm that TEST reran the affected checks (or rerun them yourself in authorized remediation mode), then re-review the delta plus its direct dependents. Do not repeat a full review for a narrow fix.

### 8. Decide and hand off

Decide `READY`, `READY WITH FOLLOW-UPS`, `RETURN TO COOK`, `RETURN TO PLAN`, or `BLOCKED`, and recommend the next stage or Skill.

## Severity and confidence

| Severity | Meaning | `code-review` equivalent | Gate |
|---|---|---|---|
| Critical | Exploitable auth bypass, credential or private-data exposure, data loss/corruption, or likely severe outage without workaround | P0 | Surface immediately; stop further implementation; must fix before finishing |
| High | Wrong behavior on a normal or important path, contract break, transaction/concurrency defect, or serious security weakness | P1 | Must fix before finishing |
| Medium | Realistic edge-case defect, important missing failure-path test, meaningful compatibility or operability gap | P2 | Fix or explicitly accept with a follow-up |
| Low | Narrow, actionable maintainability, observability, or clarity issue | P3 | Optional improvement |

Confidence is independent of severity:

- **High** — confirmed by the current source path and a test, reproduction, or unambiguous code reading;
- **Medium** — strong code evidence, but the triggering scenario has not been exercised;
- **Low** — plausible hypothesis that needs investigation; report as a hypothesis, never as a confirmed defect.

Next-action priorities use the shared P0/P1/P2 convention of the specialist Skills; that is a scheduling priority, not a finding severity.

Style preferences are not findings. Never invent vulnerabilities, line numbers, measurements, or test results. Performance findings without measurements are hypotheses for `backend-performance`.

## Finding format

```markdown
### CIR-<n> — <short title>

- Severity / Confidence: High / Medium
- Category: security | correctness | contract | architecture | data-integrity | performance | realtime | ux-accessibility | test-coverage | build-ops
- Origin: introduced | pre-existing (touched) | pre-existing (unrelated)
- Evidence: `path/to/File.java:120-138` `methodName` — what the code does; failing test or reproduction if any
- Problem and impact: concrete triggering scenario → consequence
- Remediation: minimal fix; [comprehensive option and trade-off]
- Verification: exact test or check that would prove the fix
- Owner: `<specialist-skill>` | RETURN TO PLAN
- Status: open | fixed | partially fixed | unverified | disputed | deferred | accepted risk
```

Use stable IDs across re-reviews so a finding's status can be tracked. Never include secrets, tokens, object keys, signed URLs, or sensitive payloads in evidence.

## Checkpoint mode

During COOK or TEST, a checkpoint review covers only the latest coherent increment. Use it after:

- a contract, schema, authorization, or realtime-semantics change lands;
- the first increment of a cross-layer change, before dependent layers build on it;
- a deviation from the plan.

Skip checkpoints for trivial edits such as renames, copy text, or formatting. A checkpoint reports at most the new findings, their severity, and continue/stop. Any Critical finding, or a High finding that invalidates the plan, stops COOK and returns to PLAN.

## Loop control

- A REVIEW → COOK → TEST → REVIEW cycle re-reviews only the fix delta and open findings.
- If the same finding fails to close after two remediation cycles, or each cycle introduces new High findings, stop and recommend `RETURN TO PLAN` with the evidence.
- Finish when acceptance criteria are verified, no Critical/High finding is open, and every Medium finding is fixed or explicitly accepted. Otherwise report the remaining blockers. Do not keep reviewing for marginal improvements.

## Required report

1. **Verdict and scope** — decision, task, stage, base/target, files and layers reviewed, dimensions reviewed and skipped with reasons.
2. **Findings** — ordered by severity, in the finding format.
3. **Triage** — must-fix (Critical/High), recommended follow-ups (Medium), optional improvements (Low).
4. **Remediation options** — minimal versus comprehensive fixes, with trade-offs where they differ.
5. **Checks performed** — commands actually run and `PASS`/`FAIL`/`SKIPPED`/`BLOCKED`/`INCONCLUSIVE`; TEST results reused are labeled as reused, with their source.
6. **Finding status** — fixed, partially fixed, open, unverified, accepted risk.
7. **Origin split** — regressions introduced by this change versus pre-existing issues.
8. **Plan conformance** — acceptance criteria met/unmet/unverified; scope drift.
9. **Next actions** — each with P0/P1/P2, exact Skill or stage, reason, dependencies, and required approval.
10. **Handoff** — the `WORKFLOW.md` handoff block.

If no findings are supported, state the scope reviewed and checks performed. Never claim the system, or code outside the reviewed scope, is defect-free.

## Manual handoff

After the report, offer exactly these choices:

**A — Return to COOK:** fix the listed in-scope findings under the approved plan; requires approval unless remediation was already authorized.

**B — Return to PLAN:** reassess scope, contract, schema, or architecture when a finding invalidates the plan.

**C — Verify or expand review:** run a focused investigation of a low-confidence finding or an unreviewed dimension.

**D — Finish:** accept the verdict with the listed follow-ups and open risks.

Recommend one option. Recommendations do not authorize execution; never invoke another Skill, Agent, or stage automatically.
