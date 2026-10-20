# Prompt 28 — R16 Realtime Redis Failure, Backpressure, and Observability Acceptance

- Version: v1.0
- Date: 2026-10-13
- Project: WeChat Clone
- Primary agent: wechat_project_lead
- Supporting skills: wechat-flow-trace, wechat-realtime-contract, wechat-runtime-verification
- Reviewer: wechat_code_reviewer
- Status: Ready for explicit selection; do not execute automatically

## Objective

Verify the production realtime bridge under bounded Redis failure, executor backpressure, malformed/duplicate event input, and recovery. Prove that the application remains authenticated and fails safely, that readiness and metrics describe the dependency state, that logs do not expose tokens or payloads, and that recovery does not create duplicate browser delivery.

This is an evidence and failure-acceptance task. Redis Pub/Sub is a transient transport: do not claim durable replay, guaranteed delivery, or durable ordering. A committed message that misses realtime delivery must remain recoverable through persisted application state and the explicit resynchronization path already verified by Prompt 27.

## Current context

Prompts 25–27 established or exercised:

- private Supabase attachment access across backend replicas;
- authenticated browser login/logout and two-user realtime messaging;
- reconnect, reload-based resynchronization, duplicate suppression, and REST/STOMP authorization;
- two healthy backend replicas connected through the gateway;
- Redis Pub/Sub bridging each replica’s local Simple Broker.

Current source describes additional boundaries that require direct verification:

- Redis health is part of production readiness;
- realtime publisher and listener executors have bounded queues and rejection metrics;
- malformed, oversized, unsupported, and duplicate envelopes are rejected or suppressed;
- publish failures are logged with event type, instance, and exception class only;
- Redis Pub/Sub has no replay or durable ordering;
- after-commit publication must not occur for rolled-back database transactions.

Historical prompt output is not current runtime proof. Re-read source, tests, Compose state, and the active gateway before acting.

## Scope

### In scope

- Trace the transactional writer → after-commit publisher → Redis channel → listener → local Simple Broker → STOMP client path.
- Verify Redis health/readiness, bounded publisher/listener executors, queue metrics, rejection counters, and safe failure logging.
- Exercise one bounded Redis failure and recovery path in an isolated or explicitly authorized runtime.
- Verify that malformed, oversized, unsupported, and duplicate envelopes do not reach local browser delivery.
- Verify that database rollback does not publish a realtime event.
- Verify that a committed message remains available through REST after a transient realtime failure.
- Confirm authenticated browser delivery and authorization remain intact after Redis recovery.
- Run focused frontend, backend, and runtime regressions.
- Produce a direct-evidence PASS, FAIL, or BLOCKED report.

### Out of scope

- Replacing Redis Pub/Sub with an outbox, durable queue, or replay service.
- Claiming replay, durable ordering, or guaranteed realtime delivery.
- Changing R11 auth-state caching, Supabase storage, attachment behavior, or unrelated APIs.
- Making Redis, WebSocket, conversations, or storage public.
- Editing `.env`, `.env.example`, credentials, lockfiles, or production security settings.
- Stopping shared Redis or backend services when unrelated users or tasks may be using them.
- Deleting Docker volumes, resetting databases, pruning containers, or purging non-disposable data.

## Safety and evidence rules

- Use an isolated disposable Compose project, test profile, or explicitly authorized maintenance window for destructive dependency-failure simulation.
- If isolated failure simulation is unavailable, mark the live-failure criteria BLOCKED; do not stop the shared Redis service merely to obtain a result.
- Never print passwords, access tokens, cookies, authorization headers, complete environment files, message payloads, or signed URLs.
- Use process-scoped credentials and disposable fixtures only.
- Preserve existing containers, volumes, databases, Redis data, and unrelated fixtures.
- Redact instance IDs, object keys, user identifiers, and message content in the final report.
- Do not add debug endpoints, public metrics, permissive CORS, or test-only authentication bypasses.
- Distinguish source evidence, unit-test evidence, isolated failure evidence, and shared-runtime evidence.

## Required source-trace phase

Read before changing code or running the full acceptance:

- `.agents/projects/wechat-clone/PROJECT-CONTEXT.md`
- `.agents/projects/wechat-clone/WORKFLOW.md`
- `.agents/projects/wechat-clone/AGENT-BASE-PROMPT.md`
- `.agents/projects/wechat-clone/prompts/README.md`
- `docs/architecture/realtime-redis.md`
- `docs/architecture/rate-limiting.md`
- `docs/deployment.md`
- `docs/testing.md`
- current Compose and gateway files
- `RealtimeEventPublisher`, `RedisRealtimeBroker`, `RedisRealtimeListener`, `RealtimeEnvelopeFactory`, `RecentRealtimeEventIds`, `RealtimeDestinationPolicy`, and realtime configuration/properties
- message/conversation transactional services and after-commit publication code
- `RedisRealtimeBridgeIT`, `RealtimeEventPublisherTest`, `RedisRealtimeListenerTest`, `RealtimeDestinationPolicyTest`, and relevant readiness/configuration tests
- frontend `WebSocketManager`, realtime store, chat store, and their tests

Record exact source paths for:

- transaction boundary and after-commit hook;
- publisher executor and queue limits;
- Redis timeout/retry/failure behavior;
- listener validation, deduplication, and delivery boundary;
- readiness indicator and metrics names;
- frontend reconnect and REST resynchronization behavior;
- destination and membership authorization.

## Phase 1 — Baseline and topology

Run read-only checks using the actual current repository:

```bash
git status --short --untracked-files=all
git branch --show-current
git diff --check
docker compose --env-file .env -f docker/docker-compose.yml config -q
docker compose --env-file .env -f docker/docker-compose.yml ps
```

Confirm the gateway, frontend, PostgreSQL, Redis, and two backend replicas are healthy. Confirm the active production adapter is the Redis bridge and that local/test mode is not being used as proof of distributed behavior.

Run the supported baseline regressions before any failure injection:

```bash
cd wechatbackend && mvn -Dtest=RealtimeEventPublisherTest,RedisRealtimeListenerTest,RealtimeDestinationPolicyTest,WebSocketAuthChannelInterceptorTest test
cd wechatfrontend && bash -lic 'npm test && npm run lint && npm run build'
git diff --check
```

Record actual output. Do not copy historical counts from context or earlier prompts.

## Phase 2 — Normal-path and observability baseline

Through the supported gateway and disposable users:

1. Establish one authenticated two-browser conversation.
2. Send one message and verify one recipient delivery.
3. Confirm the message is persisted through the normal REST path.
4. Capture only redacted health, readiness, metrics, and log evidence.

Verify, using currently exposed operational surfaces:

- readiness includes the realtime Redis indicator;
- liveness is distinguished from dependency readiness;
- publisher/listener queue depth and remaining capacity are bounded;
- rejection/failure/duplicate/drop counters use stable safe labels;
- logs contain no JWT, refresh token, cookie, message body, or Redis payload;
- the browser remains authenticated through the normal STOMP `CONNECT` contract.

Do not expose an internal metrics port or add a debug endpoint just to obtain evidence. If metrics are not safely reachable, use source and test evidence and mark runtime observability as limited.

## Phase 3 — Controlled failure and recovery

Use only an isolated disposable runtime or an explicitly authorized bounded test window. Do not disrupt the shared project stack by default.

### A. Redis unavailable before publish

With one disposable authenticated conversation:

1. Establish a healthy baseline.
2. Isolate Redis using the least invasive supported mechanism.
3. Attempt one normal message transaction.
4. Record REST/database behavior, readiness transition, realtime delivery result, and safe logs/metrics.
5. Restore Redis and verify readiness recovers within the documented or measured bound.
6. Verify the committed message is available through REST even if the transient realtime event was lost.

The result must distinguish database transaction failure, successful database commit with failed realtime publish, client disconnect/reconnect, and successful REST resynchronization after recovery.

### B. Redis unavailable for listener/recovery

Where safely possible, isolate subscriber connectivity or restart only the disposable backend process. Verify:

- the remaining process does not fail open or expose unauthorized data;
- the listener reconnects or reports a bounded failure;
- duplicate subscriptions or duplicate browser rows do not appear after recovery;
- a new post-recovery message is delivered once;
- a missed event is recovered only through the documented persisted-state path.

If the exact subscriber failure cannot be isolated safely, report it as BLOCKED rather than treating Redis liveness as proof.

### C. Envelope validation and duplicate suppression

Use existing unit/integration harnesses or a disposable test channel, never a new public endpoint, to exercise empty payload, oversized payload, malformed JSON, unsupported version, unsupported destination, unsupported event type, null/missing payload, repeated event ID, and one valid event.

Verify rejected or duplicate envelopes do not reach local delivery, safe counters increment, and logs do not contain full payloads.

### D. Transaction rollback

Use existing test support or a focused integration test to prove that a rolled-back message/conversation transaction produces no realtime publication. Do not manufacture a production failure by corrupting shared data.

## Phase 4 — Security and frontend boundary

Verify from source and, where safe, runtime evidence:

- Redis credentials and internal hostnames do not reach the browser bundle;
- browser STOMP `CONNECT` still carries only the intended bearer token;
- unauthorized conversation/user destinations remain denied;
- a Redis failure does not bypass REST or STOMP authorization;
- reconnect obtains the current token and restores only allowed subscriptions;
- a committed message remains retrievable through authorized REST after a transient realtime miss;
- no error response or log exposes provider details, tokens, or private payloads.

## Minimal implementation policy

Implement a fix only when the failure is reproducible, directly in this scope, security-preserving, and authorized by the task. Add or update a focused regression test and rerun the affected checks.

Do not alter Redis failure semantics, auth, storage, or frontend behavior merely to force a PASS. Do not convert a test harness limitation into a product conclusion.

## Acceptance matrix

| ID | Acceptance | Required evidence |
|---|---|---|
| R16-Q1 | Current distributed realtime path is traced | source paths and transaction/delivery summary |
| R16-Q2 | Healthy two-replica baseline is green | Compose, readiness, and browser evidence |
| R16-Q3 | Readiness/liveness distinguish Redis dependency state | health endpoint and configuration evidence |
| R16-Q4 | Publisher/listener bounds and metrics are present | properties, executor metrics, focused tests |
| R16-Q5 | Redis publish failure is bounded and safe | isolated runtime or honest BLOCKED result |
| R16-Q6 | Redis/listener recovery does not duplicate delivery | recovery evidence and message count |
| R16-Q7 | Committed data survives transient realtime loss | REST/database plus resynchronization evidence |
| R16-Q8 | Malformed/oversized/unsupported envelopes are dropped | focused tests and safe counters/logs |
| R16-Q9 | Duplicate event IDs are suppressed per documented scope | focused test and no duplicate browser row |
| R16-Q10 | Rollback does not publish realtime | transaction/after-commit test evidence |
| R16-Q11 | Auth, payload, and credential boundaries remain safe | source, bundle/log, and denial evidence |
| R16-Q12 | Regressions and runtime safety remain green | command output, Compose state, diff/status |

Use `PASS` only with direct evidence for the required criterion. Use `FAIL` when the system ran and behavior was incorrect. Use `BLOCKED` when safe isolated failure injection or a required operational surface is unavailable. Redis liveness alone is not proof of failure recovery.

## Failure policy

For each failure or blocker:

1. classify it as environment, isolation, Redis, transaction, executor/backpressure, frontend, authorization, observability, or test failure;
2. record the exact safe command/action and observable outcome without secrets;
3. distinguish lost transient delivery from lost database state;
4. do not call skipped tests or unavailable failure injection a pass;
5. implement only a minimal authorized fix with regression coverage;
6. rerun affected tests and restore the expected runtime topology;
7. leave unrelated services, volumes, and R-items unchanged.

## Regression commands

Adapt paths only after confirming the current repository:

```bash
cd wechatbackend && mvn -Dtest=RealtimeEventPublisherTest,RedisRealtimeListenerTest,RealtimeDestinationPolicyTest,WebSocketAuthChannelInterceptorTest test
cd wechatbackend && mvn verify -Ppostgres-integration
cd wechatfrontend && bash -lic 'npm test && npm run lint && npm run build'
docker compose --env-file .env -f docker/docker-compose.yml ps
git diff --check
git status --short --untracked-files=all
```

Run provider-failure or Redis integration profiles only when their prerequisites and isolation are confirmed from source. Do not use MinIO or local realtime mode as a substitute for the distributed Redis acceptance.

## Required final report

```markdown
# Verdict

PASS / FAIL / BLOCKED

## Environment and topology

- Branch and initial working-tree state:
- Gateway and replicas:
- Redis/PostgreSQL/frontend state:
- Isolation method:

## Source-trace summary

- Transaction/after-commit path:
- Redis publisher/listener path:
- Bounds, health, and metrics:
- Frontend recovery boundary:

## Acceptance matrix

| ID | Status | Evidence |
|---|---|---|
| R16-Q1 | PASS/FAIL/BLOCKED | ... |
| R16-Q2 | PASS/FAIL/BLOCKED | ... |
| R16-Q3 | PASS/FAIL/BLOCKED | ... |
| R16-Q4 | PASS/FAIL/BLOCKED | ... |
| R16-Q5 | PASS/FAIL/BLOCKED | ... |
| R16-Q6 | PASS/FAIL/BLOCKED | ... |
| R16-Q7 | PASS/FAIL/BLOCKED | ... |
| R16-Q8 | PASS/FAIL/BLOCKED | ... |
| R16-Q9 | PASS/FAIL/BLOCKED | ... |
| R16-Q10 | PASS/FAIL/BLOCKED | ... |
| R16-Q11 | PASS/FAIL/BLOCKED | ... |
| R16-Q12 | PASS/FAIL/BLOCKED | ... |

## Tests actually run

- `command` — actual result

## Files changed

- `path` — reason
- `none` if no files changed

## Limitations and blockers

- Explicit unavailable or unsafe failure cases
- None, if applicable

## Recommended next action

- One smallest concrete next step.
```

## Definition of Done

The task is complete only when the current Redis realtime path is traced; healthy and failure behavior are separated; at least one safe failure/recovery path is directly exercised or honestly marked BLOCKED; malformed and duplicate handling is tested; transaction rollback, authorization, metrics, logs, frontend recovery, regression, and cleanup evidence are recorded; and every criterion has a clear PASS, FAIL, or BLOCKED result.

## QA cases

### Happy path

- Two replicas and Redis are healthy.
- A committed message is published after commit and delivered once.
- Metrics and readiness show healthy dependencies.

### Edge cases

- Redis reconnect after a transient outage;
- committed message with lost realtime frame recovered through REST;
- duplicate event ID;
- malformed/oversized envelope;
- bounded publisher/listener queue;
- logout/reconnect after dependency recovery.

### Failure/blocker cases

- Redis cannot be isolated safely;
- readiness does not recover;
- publish failure loses database state;
- unauthorized event is delivered;
- logs expose payload or credentials;
- metrics/health surface is unavailable.

## Changelog

### v1.0 — 2026-10-13

- Created the post-Prompt-27 Redis realtime failure and observability brief.
- Added bounded failure, recovery, envelope validation, rollback, metrics, logging, and security acceptance.
