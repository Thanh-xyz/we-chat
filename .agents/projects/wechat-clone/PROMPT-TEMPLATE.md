# WeChat Clone — Task Prompt Template

Copy this template when creating the next task prompt. Replace every `[placeholder]` before using it.

```markdown
# Prompt [NN] — [R-item / task name]

Version: v[major.minor]
Date: YYYY-MM-DD
Project: WeChat Clone / Real-Time Web Chat System
Primary agent: [agent name]
Reviewer: [agent name]

## Objective

[One measurable sentence describing the outcome.]

## Current context

Read first:

- `.agents/projects/wechat-clone/PROJECT-CONTEXT.md`
- `[application-repo path]`
- `[specific source/doc/test files]`

Historical facts are not runtime proof. Inspect the current source and report contradictions.

## Scope

### In scope

- [item]
- [item]

### Out of scope

- [item]
- [item]

## Safety constraints

- Do not expose secrets, tokens, passwords, presigned URLs or sensitive data.
- Do not edit `.env` unless explicitly authorized.
- Do not delete volumes or user data.
- Do not weaken authentication, authorization or security policy.
- Do not change unrelated R-items.

## Phase 1 — Inspect before acting

Read the actual implementation and identify:

1. [contract/entry point]
2. [data flow]
3. [runtime dependency]
4. [existing test]

Do not invent endpoint names, DTOs, configuration keys or status values.

## Phase 2 — Baseline

Run the smallest safe baseline commands:

```bash
[command]
[command]
```

Record actual results as `PASS`, `FAIL` or `BLOCKED`.

## Phase 3 — Execute

[Numbered execution steps. Each step must have an expected observable result.]

## Acceptance criteria

| ID | Criterion | Expected evidence | Status |
|---|---|---|---|
| A1 | [criterion] | [specific output/test/artifact] | PASS/FAIL/BLOCKED |
| A2 | [criterion] | [specific output/test/artifact] | PASS/FAIL/BLOCKED |
| A3 | [criterion] | [specific output/test/artifact] | PASS/FAIL/BLOCKED |

## Failure policy

If a test fails:

1. classify environment, contract, implementation, data, security or test-fixture cause;
2. state the exact reproduction;
3. make the smallest in-scope fix only if authorized by this prompt;
4. add/update a regression test;
5. rerun the affected tests and report before/after.

If a test is not safely executable, mark it `BLOCKED`; do not claim success by inference.

## Regression commands

```bash
[direct test]
[related suite]
[build/lint]
git diff --check
```

## Final report format

```markdown
# Verdict
[PASS / FAIL / BLOCKED]

## What was verified
- [criterion] — [status] — [evidence]

## Findings
- [finding]

## Files changed
- `path` — [reason]

## Tests
- `[command]` — [actual result]

## Limitations/blockers
- [none or concrete blocker]

## Next action
- [one smallest safe next step]
```

## Prompt QA cases

Before shipping this prompt, check that it handles:

1. Happy path: the implementation satisfies all acceptance criteria.
2. Edge case: an endpoint/configuration/status differs from the historical context.
3. Failure case: a dependency is unavailable or a security check fails.
```

## Changelog

### v[major.minor] — YYYY-MM-DD

- Initial version / [change].
