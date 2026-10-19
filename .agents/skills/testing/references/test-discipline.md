# Test discipline

Read when choosing or judging tests for a change, especially bug fixes, realtime or async behavior, and mocks.

## Verification gate

**Do not claim a check passes unless it was run in this session and its output was read.**

Before any "passes", "fixed", or "done" claim:

1. Identify the command that proves the claim.
2. Run it in full.
3. Read the exit status and the failure count.
4. Compare the result with the claim.
5. Make the claim only if they match.

| Claim | Requires | Not sufficient |
|---|---|---|
| Tests pass | test command output with 0 failures | a previous run, "should pass" |
| Bug fixed | the original symptom's reproduction now passes | the code was changed |
| Regression test works | it fails without the fix and passes with it | it passes once |
| Build succeeds | the build command exited 0 | lint passed |
| Requirements met | each acceptance criterion mapped to evidence | the tests are green |

Words such as "should", "probably", or "seems to" in a result report signal a missing check.

## Bug-fix regression tests

A regression test must be shown to fail without the fix:

```text
run new test on unfixed code → FAIL (for the bug's reason)
apply or restore fix → PASS
```

Report both outputs. If the reverse check cannot be run safely, label the test `UNPROVEN`.

## Coverage by risk

For each changed endpoint or flow, cover:

- the happy path;
- invalid input;
- an unauthorized or non-member caller;
- a duplicate or concurrent request where the operation is not naturally idempotent;
- boundary values: 0, 1, the maximum limit, and the maximum + 1.

Arbitrate by risk: a coverage gap on a security or data path outranks flakiness, which outranks assertion style.

## Mock discipline

- Test real behavior, not mock behavior. An assertion that a mock was called is not evidence that the feature works.
- Do not add test-only methods or setters to production classes.
- Mock only at a boundary you understand, and mock its complete real shape (the full DTO or frame, not a partial object).
- If mock setup is most of the test, use a real boundary instead: Testcontainers PostgreSQL or Redis, or an in-memory STOMP client. H2 does not stand in for PostgreSQL behavior.

## Async and realtime tests

- Wait for a condition (a frame received, a store updated, a row visible) with a bounded timeout. Never use a fixed `sleep`.
- Assert on deduplication and order when the same event can arrive over REST and STOMP.
- Isolate state between tests (database rows, Redis channels, the store) so the order of tests does not matter.

## Never

- Weaken an assertion, skip a test, or widen a timeout to get a green run without reporting it.
- Report a skipped, blocked, or planned check as passing.
