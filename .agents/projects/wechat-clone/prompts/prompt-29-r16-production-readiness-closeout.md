# Prompt 29 — R16 Production-Readiness Evidence Closeout

- Version: v1.0
- Date: 2026-10-13
- Project: WeChat Clone
- Primary agent: wechat_project_lead
- Supporting skills: wechat-flow-trace, wechat-frontend-integration, wechat-realtime-contract, wechat-storage-acceptance, wechat-runtime-verification
- Reviewer: wechat_code_reviewer
- Status: Ready for explicit selection; do not execute automatically

## Objective

Produce the final current-state R16 production-readiness decision from fresh, traceable evidence across private storage, authentication, frontend, realtime, authorization, failure handling, and the Compose deployment boundary.

This is a closeout and go/no-go task, not permission to repeat historical claims or perform broad refactoring. Revalidate the smallest evidence set that proves each current criterion. If a prerequisite is stale, missing, or contradicted by source, rerun the relevant acceptance or mark the criterion BLOCKED.

## Preconditions and evidence boundary

Use the completed briefs as scope references only:

- Prompt 25 — Supabase storage cross-replica runtime acceptance;
- Prompt 26 — authenticated frontend and realtime runtime acceptance;
- Prompt 27 — realtime reconnect, resynchronization, and authorization edge acceptance;
- Prompt 28 — Redis realtime failure, backpressure, and observability acceptance, when executed.

Prompt text and prior conversation output are not sufficient evidence. Every PASS in this closeout must point to a current command, source path, runtime observation, test output, or an explicitly dated artifact whose source and runtime state are still unchanged. Do not silently downgrade missing evidence to historical PASS.

## Scope

### In scope

- Inventory current branch, working tree, source changes, prompt evidence, and known blockers.
- Reconfirm gateway, frontend, PostgreSQL, Redis, two backend replicas, and private Supabase runtime topology.
- Revalidate the smallest current Supabase upload/finalize/download and cross-replica authorization path where prior evidence is stale or incomplete.
- Revalidate auth-state revocation through REST and STOMP when the current branch includes the R11 implementation.
- Revalidate authenticated frontend login/logout, two-user realtime delivery, reconnect/resynchronization, duplicate suppression, and logout cancellation.
- Reconcile Redis failure/observability evidence and clearly separate live, isolated, integration, unit, and source-only guarantees.
- Run final frontend/backend regressions and security/diff checks.
- Produce a single R16 go/no-go report with explicit PASS, FAIL, and BLOCKED criteria.

### Out of scope

- New architecture, durable realtime replay, transactional outbox, or storage redesign.
- Making Supabase buckets, APIs, WebSockets, or metrics public.
- Editing `.env`, credentials, lockfiles, or deployment secrets without explicit separate authorization.
- Destructive Docker cleanup, volume deletion, database reset, broad fixture purge, or unrelated service changes.
- Calling R16 complete because earlier prompts reported completion.
- Performance tuning without a measured acceptance target and separate authorization.

## Safety and evidence rules

- Preserve unrelated user changes and establish the initial working-tree state before acting.
- Never print passwords, tokens, cookies, authorization headers, signed URLs, private object keys, complete environment files, or message bodies.
- Use disposable users, conversations, messages, and attachment fixtures only.
- Use the gateway as the supported browser/API entry point; do not substitute MinIO or local-only realtime mode for live production-boundary claims.
- Do not stop shared services unless the current task explicitly authorizes a reversible operation and no unrelated users depend on them. Prefer isolated failure evidence from Prompt 28.
- Report current evidence timestamps and the source/branch relationship for every reused artifact.
- If one criterion cannot be safely or honestly proven, mark it BLOCKED and keep the final verdict from being PASS unless the project lead explicitly accepts that boundary.

## Required source and evidence review

Read first:

- `.agents/projects/wechat-clone/PROJECT-CONTEXT.md`
- `.agents/projects/wechat-clone/WORKFLOW.md`
- `.agents/projects/wechat-clone/AGENT-BASE-PROMPT.md`
- `.agents/projects/wechat-clone/prompts/README.md`
- Prompt 25, Prompt 26, Prompt 27, and Prompt 28 when present
- `docs/architecture/storage.md`
- `docs/architecture/realtime-redis.md`
- `docs/deployment.md`
- `docs/testing.md`
- current Compose, gateway, backend security, auth-state, attachment/storage, realtime, and frontend session files
- current focused tests for storage, auth-state, STOMP authorization, Redis bridge, frontend WebSocket, chat resynchronization, and Playwright runtime coverage

Create an evidence inventory with these columns before deciding the verdict:

```text
criterion | evidence type | source/runtime date | branch/commit relation | command or path | freshness | status
```

Do not merge evidence from different branches or after unrelated application changes without explaining why it remains valid.

## Phase 1 — Current topology and source integrity

Run read-only checks:

```bash
git status --short --untracked-files=all
git branch --show-current
git diff --check
docker compose --env-file .env -f docker/docker-compose.yml config -q
docker compose --env-file .env -f docker/docker-compose.yml ps
```

Confirm:

- gateway is the supported public entry point;
- two backend replicas are running and healthy;
- PostgreSQL and Redis are healthy;
- frontend is healthy;
- private Supabase is the active attachment adapter;
- MinIO, if running, is not being used as current Supabase proof;
- no unexpected application/environment changes invalidate earlier evidence.

## Phase 2 — Cross-boundary revalidation

Use the least expensive fresh checks that cover all required boundaries. Do not skip a criterion merely because a previous prompt named it.

### Storage and authorization

When Prompt 25 evidence is stale or incomplete, use disposable User A/User B and a valid small fixture to verify:

- User A presigns and uploads through the supported path;
- completion validates the object and marks it READY;
- another backend replica authorizes and downloads matching bytes;
- the original replica can be unavailable while the remaining replica serves the object;
- User B is denied download/deletion and no private bytes are exposed;
- invalid/missing objects do not become READY;
- cleanup follows the documented contract.

### Authentication and revocation

When the current branch includes R11, verify with focused source/tests and, when available, two clients/replicas:

- cache hit/miss and TTL behavior;
- malformed/expired cache handling;
- Redis failure behavior;
- token-version mutation invalidates old REST and STOMP use;
- role/status/password/account changes do not leave stale access;
- raw JWT and refresh tokens are absent from Redis keys/values and logs.

### Frontend and realtime

Use disposable users through the live gateway to verify:

- login, authenticated chat shell, and logout;
- two-browser shared conversation and one realtime message;
- forced close/reconnect or reload recovery for a message committed during a transient gap;
- exactly-once visible rendering for the tested message;
- subscription cleanup/restoration and bounded reconnect;
- logout cancellation and principal isolation;
- unauthorized REST and STOMP conversation/user destinations.

### Failure and observability

Reuse Prompt 28 evidence only when its isolation, branch, and runtime relation remain valid. Otherwise rerun the smallest safe checks for:

- Redis readiness and liveness distinction;
- bounded executor/queue and failure metrics;
- safe logs with no payloads/tokens;
- malformed/duplicate event handling;
- committed database state versus transient realtime loss.

## Phase 3 — Regression and security closeout

Run the supported current commands and record each result separately:

```bash
cd wechatbackend && mvn test
cd wechatbackend && mvn verify -Ppostgres-integration
cd wechatbackend && mvn verify -Ps3-integration
cd wechatfrontend && bash -lic 'npm test'
cd wechatfrontend && bash -lic 'npm run lint'
cd wechatfrontend && bash -lic 'npm run build'
cd wechatfrontend && bash -lic 'npm run test:e2e'
git diff --check
git status --short --untracked-files=all
```

Adapt or omit profiles only when source confirms they are unavailable; classify missing infrastructure as BLOCKED, not PASS. Authenticated E2E variables must be disposable and process-scoped.

Inspect the final diff for:

- `.env` or secret changes;
- public bucket/CORS/WebSocket changes;
- debug endpoints or exposed internal ports;
- temporary test bypasses;
- unrelated source modifications;
- generated reports or credentials accidentally added to the worktree.

## Decision rules

- `PASS`: current direct evidence satisfies every required production-boundary criterion.
- `FAIL`: the current system ran and a required behavior was incorrect or insecure.
- `BLOCKED`: a required dependency, safe runtime path, isolated failure mechanism, or fresh evidence is unavailable.
- Overall R16 is `PASS` only when no required criterion is `FAIL` or `BLOCKED` and the evidence inventory is complete.
- A skipped test, source-only claim, MinIO result, or historical prompt report cannot close a live Supabase/cross-replica/browser criterion by itself.

## Acceptance matrix

| ID | Acceptance | Required evidence |
|---|---|---|
| R16-C1 | Evidence inventory is current and branch-consistent | evidence table with dates and paths |
| R16-C2 | Compose gateway topology is healthy | config, two replicas, readiness, health |
| R16-C3 | Private Supabase storage works cross-replica | upload, READY, matching download, authorization |
| R16-C4 | Storage survives original-replica unavailability | failover download and metadata |
| R16-C5 | Invalid/missing storage objects are bounded safely | negative matrix and cleanup |
| R16-C6 | Auth-state cache and revocation contract is current | REST/STOMP stale-token evidence or explicit scope status |
| R16-C7 | Authenticated frontend login/logout is current | browser result with disposable fixture |
| R16-C8 | Two-browser realtime and reconnect/resync are current | live browser evidence and message counts |
| R16-C9 | REST/STOMP authorization remains enforced | denial matrix after reconnect/reload |
| R16-C10 | Redis failure/observability guarantees are current | isolated failure or honest BLOCKED evidence |
| R16-C11 | Regressions and secret/security inspection are green | test output and final diff/status |
| R16-C12 | Fixtures and unrelated runtime state are preserved | cleanup and Compose evidence |
| R16-C13 | Final go/no-go decision is reproducible | evidence map and smallest next action |

## Final report format

```markdown
# Verdict

PASS / FAIL / BLOCKED

## Decision summary

- R16 go/no-go:
- Branch:
- Evidence cutoff:
- Required blockers:
- Smallest next action:

## Evidence inventory

| Criterion | Evidence type | Date/branch relation | Command/path | Status |
|---|---|---|---|---|
| ... | ... | ... | ... | PASS/FAIL/BLOCKED |

## Acceptance matrix

| ID | Status | Direct evidence |
|---|---|---|
| R16-C1 | PASS/FAIL/BLOCKED | ... |
| R16-C2 | PASS/FAIL/BLOCKED | ... |
| R16-C3 | PASS/FAIL/BLOCKED | ... |
| R16-C4 | PASS/FAIL/BLOCKED | ... |
| R16-C5 | PASS/FAIL/BLOCKED | ... |
| R16-C6 | PASS/FAIL/BLOCKED | ... |
| R16-C7 | PASS/FAIL/BLOCKED | ... |
| R16-C8 | PASS/FAIL/BLOCKED | ... |
| R16-C9 | PASS/FAIL/BLOCKED | ... |
| R16-C10 | PASS/FAIL/BLOCKED | ... |
| R16-C11 | PASS/FAIL/BLOCKED | ... |
| R16-C12 | PASS/FAIL/BLOCKED | ... |
| R16-C13 | PASS/FAIL/BLOCKED | ... |

## Tests and runtime checks actually run

- `command` — actual result

## Files changed

- `path` — reason
- `none` if no files changed

## Limitations and blockers

- Explicit stale, skipped, unavailable, or unsafe evidence
- None, if applicable

## Handoff

- One smallest concrete next action.
```

## Definition of Done

The closeout is complete only when every R16 boundary has a current evidence entry; storage, auth, frontend, realtime, authorization, failure, security, and runtime criteria are independently classified; no historical or emulator-only result is mislabeled as current proof; the worktree and Compose state are accounted for; and the final go/no-go decision identifies one concrete next action or states that no further R16 work remains.

## QA cases

### Happy path

- Current private Supabase upload/download works across replicas.
- Authenticated frontend and realtime flows pass.
- Reconnect/resynchronization and authorization remain safe.
- Redis health/failure evidence is current.

### Edge cases

- evidence from an earlier branch;
- one replica unavailable;
- missed transient realtime event recovered from persistence;
- expired/revoked token after reconnect;
- invalid storage object;
- duplicate or malformed realtime envelope;
- partial fixture cleanup.

### Failure/blocker cases

- Supabase prerequisite unavailable;
- safe Redis failure isolation unavailable;
- authenticated browser credentials unavailable;
- current source contradicts a historical report;
- secret/environment diff detected;
- any required criterion remains FAIL or BLOCKED.

## Changelog

### v1.0 — 2026-10-13

- Created the final R16 production-readiness evidence closeout brief.
- Added branch-consistent evidence inventory, cross-boundary revalidation, decision rules, and go/no-go reporting.
