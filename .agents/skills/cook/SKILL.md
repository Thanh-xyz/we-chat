---
name: cook
description: Execute an approved WeChat Clone implementation plan step by step during the COOK stage, coordinating the specialist Skills the plan selected, with checkpoints, deviation handling, and verified step status. Use only after PLAN deliverables are approved; not for planning, open-ended exploration, or unapproved fixes.
---

# Cook — Execute an Approved Plan

## Purpose and ownership

COOK turns an approved plan into a scoped, verified change. This Skill owns the execution loop: step order, local checks, checkpoint reviews, deviation handling, and the COOK handoff. The specialist Skills selected in the plan's routing record own the domain rules for each step (for example `spring-boot-engineering`, `frontend-engineering`, `database-migration`, `realtime-engineering`); read and follow each one's `SKILL.md` before its first step.

COOK does not plan, redesign, or test for acceptance. Acceptance verification belongs to TEST (`testing`, `frontend-quality-gate`), and independent review belongs to REVIEW (`continuous-improvement-review`).

## Entry gate

Do not start until all of these hold. If any fails, stop and recommend `plan`:

1. The four PLAN deliverables in `.agents/projects/wechat-clone/WORKFLOW.md` exist and the user approved them: the ordered plan, the specialist routing record, the verification matrix, and the approval gates.
2. The plan still matches the repository. Re-check the files and symbols it names and stop on material drift.
3. `git-workflow` preflight has run: `git status --short --branch`, `git diff`, `git diff --cached`. Record pre-existing changes and preserve them. Create or switch branches only when the user authorized it.
4. A baseline exists. Run the smallest relevant check before the first edit and record its PASS/FAIL. You cannot tell a regression from a pre-existing failure without it.

Review the plan critically before the first edit. Raise any gap, contradiction, or unsafe step before you start.

## Execution loop

For each plan step, in dependency order:

1. **Load the owner.** Read the specialist Skill named for the step if it is not already loaded.
2. **Implement exactly the step.** Touch only the files the step names, plus direct references the change requires (imports, callers, tests). Use the repository's existing conventions.
3. **Check locally.** Run the narrowest check that exercises the step. Examples: a targeted Maven test (`cd wechatbackend && ./mvnw -q test -Dtest=ClassName`), a targeted Vitest file (`cd wechatfrontend && npx vitest run path`), `npm run lint`. Inspect `git diff` for the step.
4. **Record status.** Use exactly one status per step:
   - `DONE`: implemented, and its check ran and passed.
   - `DONE_WITH_CONCERNS`: implemented and checked, with a named doubt.
   - `BLOCKED`: cannot proceed; the reason and evidence are recorded.
   - `NEEDS_CONTEXT`: a decision or information is missing.
   Never mark a step done without the check output from this session.
5. **Checkpoint when planned.** After a contract, schema, authorization, or realtime-semantics step, or the first increment of a cross-layer change, request a `continuous-improvement-review` checkpoint before dependent steps build on it.

When a plan step specifies writing a failing test first, follow [references/test-first.md](references/test-first.md).

## Stop conditions

Stop, record the evidence, and hand back instead of improvising when:

- a step seems wrong, unsafe, or impossible as written: report it and request re-planning, and do not implement your own interpretation;
- the step needs an unapproved contract, schema, permission, dependency, configuration, or file-scope change;
- a checkpoint reports a Critical finding, or a High finding that invalidates the plan;
- the same check fails after two focused fix attempts: switch to `debugging`, and do not stack more guesses;
- an instruction is unclear: ask, and do not guess.

## Deviations

A deviation is any difference between the approved plan and what was done. Record each one with its reason, affected files, and whether it needs approval. Minor mechanical deviations, such as an extra import or a renamed local variable, need only a note. Anything touching behavior, contracts, or scope needs approval before it is kept.

## Fixing review or test findings

When COOK resumes to fix TEST failures or REVIEW findings, follow [references/review-findings.md](references/review-findings.md). Fix only approved, in-scope findings, one at a time, and rerun the affected checks.

## Completion and handoff

Before reporting COOK complete:

- every plan step has a status, with no silent drops;
- the full diff has been inspected, and contains no debug output, secrets, unrelated formatting, or files outside the plan;
- the local checks that ran are listed with real results; unrun checks are labeled `NOT RUN` with a reason.

Report: steps and status, files changed and why, deviations, checks run with PASS/FAIL/SKIPPED/BLOCKED, checkpoint findings, and open risks. Update the `WORKFLOW.md` handoff block.

Then offer exactly these choices:

**A — Proceed to TEST:** verify the acceptance criteria with `testing` (and `frontend-quality-gate` for client changes).

**B — Continue COOK:** finish the remaining or blocked steps once their blockers are resolved.

**C — Return to PLAN:** re-plan when a deviation or finding invalidates the approved plan.

**D — Stop:** keep the current changes uncommitted and report.

Recommend one choice. Do not stage, commit, push, or invoke another Skill automatically.
