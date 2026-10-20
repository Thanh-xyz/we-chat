# Test-first steps

Read when a plan step says to write a failing test first, or when fixing a confirmed bug.

## Red → green → refactor

1. **Red.** Write one test for the next required behavior. Run it and confirm that it **fails**, and that it fails for the expected reason (missing behavior) rather than a compile error, typo, or fixture problem.
   - A test that passes immediately is testing existing behavior. Fix the test before writing production code.
2. **Green.** Write the minimum production code that makes the test pass. Do not add features, refactor other code, or "improve" anything beyond what the test requires.
3. **Verify.** Run the test and the closest related tests, and confirm that all of them pass.
4. **Refactor.** Optionally clean up with the tests still green, then rerun them.

## Bug fixes: prove the test catches the bug

For a regression test, show both directions:

```text
write test → run (FAIL on current code) → apply fix → run (PASS)
```

If the fix already exists, temporarily revert only the fix, confirm that the test fails, and restore the fix. Report both outputs. A regression test that never failed proves nothing.

## Stack notes

- Backend: JUnit 5 under `wechatbackend/src/test`. For a PostgreSQL-specific behavior (constraints, trigram search, keyset pagination, Flyway), H2 is not evidence. Use the Testcontainers profile `-Ppostgres-integration` when the environment allows it, and otherwise label the claim unverified.
- Frontend: Vitest with jsdom and Testing Library under `wechatfrontend/src`. Prefer asserting rendered behavior and store state over implementation details.
- Async and realtime tests: wait on an observable condition (a received frame, a store update, a row present) with a bounded timeout. Never use a fixed sleep.

## Rationalizations to reject

| Thought | Response |
|---|---|
| "Too simple to need a test" | Simple code still breaks; the test takes minutes. |
| "I'll add tests after" | A test written after the code passes immediately and proves nothing. |
| "I already tested it manually" | Manual checks are not repeatable evidence. |
| "Mocking everything is faster" | Mocks of JDBC or STOMP can hide the real contract. Prefer real boundaries where the plan requires them. |
