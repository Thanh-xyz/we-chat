---
name: debugging
description: Find and prove the root cause of a WeChat Clone bug, test failure, or runtime error across Spring Boot, PostgreSQL, Redis/STOMP, storage, gateway, or React before any fix is written. Use for unexplained failures and after repeated failed fix attempts; not for planned feature work.
---

# Debugging — Root Cause First

## Iron law

**No fix without a proven root cause.** A fix for a symptom whose cause is unknown is a guess, and a guess can hide the real defect or create a new one.

This Skill investigates and proves. It may add temporary diagnostics, but the fix itself goes through COOK (`cook` plus the owning specialist) unless the user authorized fixing as part of this task. Run `git status --short` first and preserve existing changes. Do not print secrets, tokens, object keys, or signed URLs in evidence.

## Phase 1 — Investigate

1. **Read the failure completely:** the full stack trace, the assertion message, the HTTP status and error body, the STOMP ERROR frame, the browser console and network tab. Do not skim past the first line.
2. **Reproduce it.** Find the smallest reliable reproduction: one test, one request, one UI action. If it is intermittent, record its frequency and conditions. An unreproduced bug cannot be shown to be fixed.
3. **Check what changed:** `git log -p` for the affected paths, recent dependency, configuration, and migration changes, and environment differences between the failing and passing setups.
4. **Trace the boundary.** Follow the data through each component (controller → service → repository/storage → transaction → event → client) and observe the value at each boundary until it is wrong. Fix the source of the bad value, not the place it surfaced. For stack-specific places to look, read [references/wechat-diagnostics.md](references/wechat-diagnostics.md).

## Phase 2 — Compare

Find a similar path that works: another endpoint, the same flow for another user, a passing test, or a previous commit. List every difference between the working and broken paths, including ones that look irrelevant. Use `git bisect` when a known good commit exists.

## Phase 3 — Hypothesize and test

1. State a single falsifiable hypothesis: "X is the root cause because Y; if true, then Z will be observed."
2. Test it with the smallest change or observation, and change one variable at a time.
3. If it is disproved, record it and form a new hypothesis. Do not stack changes.

A root cause is **proven** only when:

- it explains **all** observed symptoms, not just the first one;
- the evidence shows causation, not just correlation;
- something could have disproved it and did not;
- removing the cause makes the reproduction stop.

## Phase 4 — Hand off the fix

Deliver a failing regression test (or an exact reproduction command) that fails because of the root cause, the proven cause with its evidence, and the minimal fix direction. The fix must make that test pass without weakening any assertion, authorization check, or validation.

## Escalation

If **three** fix attempts have failed, or each fix reveals a new failure somewhere else, stop. The problem is probably structural: a wrong assumption in the design, contract, or concurrency model. Report this and recommend `RETURN TO PLAN` (or `backend-architecture` or `frontend-architecture`) instead of a fourth attempt.

## Red flags — stop and return to Phase 1

- "Quick fix for now, investigate later."
- "Just try changing X and see."
- "It should work now," said without a fresh run.
- Adding a retry, sleep, null check, or catch-all that hides the error instead of explaining it.
- Changing a test's expectation to match the observed output.

## Report

Report:

- symptom and reproduction;
- evidence gathered, with commands actually run and their results;
- hypotheses tested and their outcomes;
- the root cause, labeled `CONFIRMED` or `LIKELY` (with what is missing to confirm it);
- the regression test or reproduction;
- the recommended fix and its owner Skill;
- temporary diagnostics added (they must be removed before COOK completes).

Then offer exactly these choices:

**A — Fix via COOK:** implement the minimal fix with the regression test under an approved scope.

**B — Investigate further:** gather the missing evidence for a `LIKELY` cause.

**C — Return to PLAN:** the cause is structural or out of scope.

**D — Stop:** keep the findings only.

Recommend one choice. Never invoke another Skill automatically.
