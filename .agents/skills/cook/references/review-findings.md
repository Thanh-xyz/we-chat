# Responding to review and test findings

Read when COOK resumes to address `continuous-improvement-review` or `code-review` findings, TEST failures, or reviewer feedback.

Findings are claims to verify against the code, not orders to follow.

## Protocol

1. **Read all findings first.** Do not start fixing until the whole set is understood.
2. **Clarify.** If any finding is unclear, ask about it before implementing anything. Findings are often related, and a partial understanding produces wrong fixes.
3. **Verify each finding against current source.** Does the scenario actually occur? Is the cited file and line still accurate? Is the finding in scope?
4. **Decide per finding:**
   - **Fix:** the finding is correct, in scope, and approved.
   - **Defend:** the finding is incorrect. Give technical evidence: a file and symbol, a test, or the plan's acceptance criterion. Do not argue from preference.
   - **Escalate:** fixing it would change scope, contract, schema, or security boundaries. Recommend `RETURN TO PLAN`.
   - **Check usage before adding.** If a finding asks for a new abstraction or a "proper" implementation of something, check that it is actually used. Unused scope is not a reason to build more.
5. **Fix in order:** Critical/High first, then simple fixes, then complex ones. Make one fix at a time and rerun the affected check after each.
6. **Report factually.** For each finding, say what changed and the evidence: "CIR-3 fixed: added membership predicate in `MessageRepository.findPage`; `MessageServiceTest#nonMemberDenied` PASS." Do not write praise or agreement phrases.

## Status values

Use the finding statuses from `continuous-improvement-review`: `open`, `fixed`, `partially fixed`, `unverified`, `accepted risk`. Add these:

- `disputed`: defended with evidence, awaiting the reviewer's or the user's decision.
- `deferred`: valid but out of scope, recorded as a follow-up.

The user arbitrates disputed findings. Do not mark a finding fixed without a check run after the fix.
