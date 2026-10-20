# Prompt 27 — R16 Realtime Reconnect, Resynchronization, and Authorization Edge Acceptance

- Version: v1.0
- Date: 2026-10-13
- Project: WeChat Clone
- Primary agent: wechat_project_lead
- Supporting skills: wechat-flow-trace, wechat-frontend-integration, wechat-realtime-contract, wechat-runtime-verification
- Reviewer: wechat_code_reviewer
- Status: Ready for explicit selection; do not execute automatically

## Objective

Verify the authenticated realtime edge contract after Prompt 26. Prove that a browser can lose and regain its STOMP connection without stale subscriptions or duplicate rendering, that persisted messages can be resynchronized after a transient Redis Pub/Sub gap, and that REST/STOMP authorization remains enforced after reconnect and reload.

This is a runtime acceptance and focused regression task. Implement only the smallest reproducible, in-scope, security-preserving fix. Do not claim that Redis Pub/Sub replays events: it is transient and does not provide durable replay or ordering.

## Current context

Prompt 26 established the basic authenticated path through the live gateway:

- the frontend can log in and log out;
- two authenticated browser contexts can open one shared conversation;
- one message can reach the second browser exactly once;
- frontend unit tests, lint, build, and authenticated Playwright coverage pass;
- the `/ws` HTTP handshake is permitted so authentication can occur in the STOMP `CONNECT` frame;
- STOMP authorization remains enforced by `WebSocketAuthChannelInterceptor`;
- the production runtime uses Redis Pub/Sub to bridge events to each replica’s local Simple Broker.

Current source also establishes these important boundaries:

- `WebSocketManager` authenticates every connection in `beforeConnect`, restores subscriptions in `onConnect`, clears subscriptions on socket close, and uses bounded reconnect delays;
- `chatStore.handleRealtimeEvent` reloads latest messages from REST for message events and merges message IDs without duplicates;
- Redis Pub/Sub is best-effort and transient; a client that misses an event must recover from persisted REST state or an explicit refresh path;
- the browser subscribes to `/user/queue/conversation-events` and the user notification destination, while conversation destination authorization is enforced server-side.

Historical results are not current proof. Re-read source, tests, Compose state, and the live gateway before acting.

## Scope

### In scope

- Trace frontend connection state, token acquisition, STOMP `CONNECT`, subscription restoration, reconnect backoff, logout, and conversation resynchronization.
- Trace backend STOMP authentication and destination authorization for `CONNECT`, `SUBSCRIBE`, and `SEND`.
- Use disposable authenticated users and conversations through the live gateway.
- Exercise a real disconnect/reconnect or reload path.
- Prove that a message committed while a receiver is disconnected is recoverable from persisted state after reconnect or reload.
- Prove that the recovered message is rendered once and that subscriptions are not multiplied across reconnects.
- Verify unauthorized REST and STOMP access to a conversation the principal does not belong to.
- Run focused frontend/realtime/backend regressions and the applicable authenticated Playwright checks.
- Produce an evidence-based PASS, FAIL, or BLOCKED report.

### Out of scope

- Replacing Redis Pub/Sub with a transactional outbox, durable queue, or replay service.
- Claiming durable realtime delivery, ordering, or replay that the current architecture does not provide.
- Reworking R11 auth-state caching, Supabase storage, attachment flows, or unrelated frontend features.
- Making a private conversation or topic public; weakening CORS, JWT, STOMP, or REST authorization.
- Editing `.env`, `.env.example`, credentials, lockfiles, or browser security settings.
- Deleting Docker volumes, resetting the database, stopping unrelated services, or purging non-disposable data.
- Treating a skipped test or an unavailable browser/runtime as a pass.

## Safety and evidence rules

- Use only disposable users, conversations, messages, and tokens created for this task.
- Never print passwords, access tokens, cookies, authorization headers, signed URLs, complete environment files, or private object keys.
- Inject credentials only into the current process; never persist `E2E_*` values.
- Preserve existing containers, volumes, databases, Redis state, and unrelated fixtures.
- Clean up only disposable fixtures created by this task where supported. If cleanup is unsafe, document the limitation without exposing identifiers.
- Do not use MinIO or a local-only broker as proof of the live gateway/realtime path.
- Redact message contents in the final report; use fixture labels, counts, hashes, or non-sensitive IDs only when needed.
- If a disconnect mechanism cannot be exercised safely, mark the affected criteria BLOCKED rather than inferring reconnect behavior from unit tests alone.

## Required source-trace phase

Read before changing code or running the full acceptance:

- `.agents/projects/wechat-clone/PROJECT-CONTEXT.md`
- `.agents/projects/wechat-clone/WORKFLOW.md`
- `.agents/projects/wechat-clone/AGENT-BASE-PROMPT.md`
- `.agents/projects/wechat-clone/prompts/README.md`
- `docs/architecture/realtime-redis.md`
- `docs/testing.md`
- `wechatfrontend/package.json` and `wechatfrontend/package-lock.json`
- `wechatfrontend/playwright.config.js` and current authenticated smoke tests
- `wechatfrontend/src/services/websocket/WebSocketManager.js` and tests
- `wechatfrontend/src/features/realtime/realtimeStore.js`
- `wechatfrontend/src/features/conversations/chatStore.js` and tests
- frontend auth/token storage, login/logout, chat-page lifecycle, and API clients
- backend `WebSocketConfig`, `WebSocketAuthChannelInterceptor`, `RealtimeDestinationPolicy`, `RedisRealtimeBroker`, `RedisRealtimeListener`, `RealtimeEventPublisher`, and relevant message/conversation services
- `WebSocketAuthChannelInterceptorTest`, `RealtimeDestinationPolicyTest`, Redis realtime tests, and runtime/Compose configuration

Use `rg` to verify actual class names, destinations, endpoint paths, token behavior, backoff limits, merge/deduplication rules, authorization checks, and available test fixtures. Do not invent endpoints, DTOs, browser hooks, or environment variables.

Record which guarantees are source-only, unit-tested, or observed through the live gateway. In particular, distinguish:

- live event delivery while both clients are connected;
- event loss during a Redis Pub/Sub or socket gap;
- REST-based resynchronization after that gap;
- durable persistence of the message in PostgreSQL.

## Phase 1 — Baseline and environment diagnosis

Run read-only checks first:

```bash
git status --short --untracked-files=all
git branch --show-current
git diff --check
docker compose --env-file .env -f docker/docker-compose.yml config -q
docker compose --env-file .env -f docker/docker-compose.yml ps
```

Confirm the supported gateway, frontend origin, Redis, PostgreSQL, frontend, gateway, and two backend replicas are healthy. Do not print `.env`.

Use the repository’s package manager and lockfile. In the same login-shell form used for frontend checks, record:

```bash
bash -lic '
command -v node
command -v npm
node --version
npm --version
printf "%s\\n" "$PATH"
'
```

Inspect `package.json` scripts and lockfiles before installing anything. Do not run `npm install`; use existing dependencies or the authoritative lockfile command only if installation is genuinely required.

Run the baseline and record each result separately:

```bash
bash -lic 'npm test'
bash -lic 'npm run lint'
bash -lic 'npm run build'
cd wechatbackend && mvn -Dtest=WebSocketAuthChannelInterceptorTest,RealtimeDestinationPolicyTest test
git diff --check
```

## Phase 2 — Disposable fixture and connection setup

Create the minimum disposable fixture supported by the current API:

- User A: sender and member of the shared conversation.
- User B: receiver and member of the shared conversation.
- User C or an existing disposable non-member conversation: unauthorized principal for negative authorization checks.

Create one shared conversation for A and B and one conversation/resource that C is not allowed to access, using supported application APIs. Do not reuse personal credentials or existing user data.

Set only process-scoped values required by the current Playwright script, such as `E2E_BASE_URL`, `E2E_USER_A_*`, `E2E_USER_B_*`, and `E2E_CONVERSATION_ID`. Use the exact variables found in current source.

Do not print credentials or tokens. Record only redacted fixture labels, result categories, and safe IDs where necessary.

## Phase 3 — Reconnect and resynchronization acceptance

Use two authenticated browser contexts through the live gateway.

### A. Baseline connection

1. Log in User A and User B.
2. Open the same conversation in both contexts.
3. Confirm each browser reaches the authenticated chat shell and the realtime state becomes `connected`.
4. Send one baseline message from A and verify B receives it once.
5. Record the visible message count in the message panel, not sidebar previews or other duplicated DOM labels.

### B. Receiver disconnect and persisted-message recovery

Use a supported browser/context operation or the existing application controls to disconnect User B without deleting the conversation or message data. If only a socket-level disconnect is possible, document the exact safe mechanism.

While B is disconnected:

1. Send one new message from A.
2. Verify the REST send succeeds and the message is persisted through the normal API path.
3. Do not treat the absence of a live frame as a backend failure; Redis Pub/Sub is transient.

Reconnect B or reload its authenticated conversation page. Verify:

- the frontend reacquires the current access token for the new STOMP `CONNECT`;
- the connection returns to `connected` or reports a bounded, actionable `offline` state;
- the conversation’s REST load/resynchronization includes the missed message;
- the missed message appears exactly once in the message panel;
- reconnect does not create duplicate subscriptions or duplicate message loads that render duplicate rows;
- existing messages remain ordered according to the current API/store contract;
- the UI does not claim guaranteed replay for the transient realtime gap.

If the current UI has no explicit replay indicator, verify the observable persisted-state recovery and report the limitation rather than inventing one.

### C. Reload and logout cancellation

1. Reload an authenticated conversation page and confirm it reconnects once and restores the expected subscriptions.
2. Log out while connected and, if safely reproducible, while a reconnect is pending.
3. Verify logout clears client authentication state, stops further reconnect attempts, closes the socket, and does not allow a stale callback to repopulate conversation data.
4. Verify a subsequent login starts a clean connection with no subscriptions or messages from the prior principal.

### D. Bounded reconnect behavior

Use source/unit evidence and, where safe, runtime observation to verify that repeated failures use the configured bounded backoff and transition to `offline` rather than retrying forever without user-visible state. Do not create an uncontrolled outage or alter production Redis/network configuration merely to force this case.

## Phase 4 — Authorization after reconnect and negative cases

As the unauthorized principal, test every supported current path identified during source tracing:

- REST conversation/message read;
- REST message send;
- STOMP `SUBSCRIBE` to the non-member conversation destination;
- STOMP `SEND` to the non-member conversation destination;
- user-queue subscription that does not belong to the authenticated principal.

For each path, record only the status/error category and confirm no private conversation or message data is returned.

Repeat the relevant STOMP authorization checks after a disconnect/reconnect or fresh login. Confirm that a previously authorized browser does not retain access after logout or principal change.

As an authorized member, confirm the shared conversation still works after reconnect. If a token is expired, revoked, or unavailable, verify that the new `CONNECT` is rejected or transitions safely to the documented offline state; never bypass authentication to keep the socket alive.

## Phase 5 — Focused regressions and optional implementation

Run the focused frontend tests for:

- fresh token use in `beforeConnect`;
- subscription restoration and subscription cleanup;
- bounded backoff and offline state;
- malformed frame handling;
- message-event REST resynchronization and ID-based deduplication;
- logout/disconnect lifecycle if covered by current tests.

Run focused backend tests for:

- STOMP `CONNECT` authentication;
- conversation/user destination authorization;
- unauthorized subscription/send rejection;
- Redis envelope validation and duplicate suppression;
- after-commit publication behavior.

Implement a fix only when the defect is reproducible, directly in this scope, security-preserving, and not a test-only bypass. Add or update a focused regression test, then rerun the affected test and the related frontend/backend regressions. Do not edit `.env`, Playwright configuration, or browser security flags to hide a failure.

## Acceptance matrix

| ID | Acceptance | Required evidence |
|---|---|---|
| R16-R1 | Current source and runtime contracts are traced | file paths, destinations, lifecycle and authorization summary |
| R16-R2 | Baseline authenticated realtime path remains green | two-browser baseline result |
| R16-R3 | Receiver disconnect/reconnect is exercised safely | exact mechanism and connection-state evidence |
| R16-R4 | Message committed during the gap is recovered from persisted state | REST send plus post-reconnect/reload message evidence |
| R16-R5 | Recovered message renders exactly once | message-panel count and deduplication evidence |
| R16-R6 | Reconnect restores only the intended subscriptions | subscription source/test/runtime evidence |
| R16-R7 | Reload and logout terminate stale realtime state | browser state and socket lifecycle evidence |
| R16-R8 | Reconnect attempts are bounded and observable | configured backoff and state evidence |
| R16-R9 | Unauthorized REST access remains denied | status/error matrix and no-data evidence |
| R16-R10 | Unauthorized STOMP SUBSCRIBE/SEND remains denied after reconnect | focused backend test and/or live result |
| R16-R11 | Redis Pub/Sub limitations are reported honestly | no replay/durability claim; persistence/resync distinction |
| R16-R12 | Frontend/backend regressions and runtime safety remain green | test outputs, Compose health, diff/status |

Use `PASS` only when the required criterion has direct evidence. Use `FAIL` when the system ran and behavior was incorrect. Use `BLOCKED` when a safe prerequisite or disconnect mechanism was unavailable. A unit test cannot by itself prove live cross-replica recovery, and a successful reconnect cannot prove durable replay.

## Failure policy

For every failure or blocker:

1. classify it as environment, browser runner, fixture, frontend lifecycle, REST, STOMP, Redis, authorization, persistence, or test failure;
2. record the exact command/action and observable result without secrets;
3. distinguish a lost transient frame from failed persistence or failed resynchronization;
4. do not call skipped coverage passed;
5. implement only a minimal authorized fix with a focused regression test;
6. rerun the affected check and related regressions;
7. leave unrelated R-items and runtime services unchanged.

## Regression commands

Adapt paths only after confirming the current repository layout:

```bash
bash -lic 'npm test'
bash -lic 'npm run lint'
bash -lic 'npm run build'
bash -lic 'npm run test:e2e'
cd wechatbackend && mvn -Dtest=WebSocketAuthChannelInterceptorTest,RealtimeDestinationPolicyTest,RedisRealtimeListenerTest,RealtimeEventPublisherTest test
docker compose --env-file .env -f docker/docker-compose.yml ps
git diff --check
git status --short --untracked-files=all
```

If authenticated Playwright variables are unavailable, provision disposable fixtures as described. Do not use personal credentials. If Chromium or the gateway is unavailable, classify the result as BLOCKED and preserve the source/test evidence separately.

## Required final report

```markdown
# Verdict

PASS / FAIL / BLOCKED

## Environment and topology

- Branch and initial working-tree state:
- Node/npm shell and versions:
- Gateway and backend replica state:
- Redis/PostgreSQL/frontend state:

## Source-trace summary

- Frontend connection/token lifecycle:
- STOMP destinations and authorization:
- Persistence/resynchronization path:
- Redis Pub/Sub guarantee and limitation:

## Fixture and cleanup

- Disposable fixture labels:
- Shared conversation:
- Unauthorized fixture:
- Cleanup result:

## Acceptance matrix

| ID | Status | Evidence |
|---|---|---|
| R16-R1 | PASS/FAIL/BLOCKED | ... |
| R16-R2 | PASS/FAIL/BLOCKED | ... |
| R16-R3 | PASS/FAIL/BLOCKED | ... |
| R16-R4 | PASS/FAIL/BLOCKED | ... |
| R16-R5 | PASS/FAIL/BLOCKED | ... |
| R16-R6 | PASS/FAIL/BLOCKED | ... |
| R16-R7 | PASS/FAIL/BLOCKED | ... |
| R16-R8 | PASS/FAIL/BLOCKED | ... |
| R16-R9 | PASS/FAIL/BLOCKED | ... |
| R16-R10 | PASS/FAIL/BLOCKED | ... |
| R16-R11 | PASS/FAIL/BLOCKED | ... |
| R16-R12 | PASS/FAIL/BLOCKED | ... |

## Tests actually run

- `command` — actual result
- `command` — actual result

## Files changed

- `path` — reason
- `none` if no files changed

## Limitations and blockers

- Explicit skipped or unavailable cases
- None, if applicable

## Recommended next action

- One smallest concrete next step.
```

## Definition of Done

This task is complete only when:

- the current frontend, STOMP, Redis, persistence, and authorization contracts are traced;
- a real authenticated disconnect/reconnect or reload path is exercised safely;
- a message committed during a transient connection gap is recovered from persisted state;
- the recovered message renders exactly once;
- subscription cleanup, bounded reconnect, logout cancellation, and principal isolation are verified;
- unauthorized REST and STOMP access remain denied;
- Redis Pub/Sub’s lack of replay/durability is reflected accurately in the report;
- focused frontend/backend regressions and Compose health are recorded;
- fixtures are cleaned up or the limitation is documented;
- every acceptance item has direct PASS, FAIL, or BLOCKED evidence.

## QA cases

### Happy path

- Two authenticated users share a conversation.
- User B disconnects.
- User A sends a message that is committed successfully.
- User B reconnects or reloads.
- REST resynchronization displays the message once.
- A later realtime message is delivered once through the restored subscription.

### Edge cases

- socket closes during reconnect backoff;
- browser reload after a message event;
- repeated reconnect does not multiply subscriptions;
- logout while reconnecting;
- login as a different principal after logout;
- malformed realtime frame;
- duplicate event ID;
- missed Redis Pub/Sub event followed by REST resynchronization.

### Failure/blocker cases

- gateway or Redis unavailable;
- browser cannot safely force a disconnect;
- stale or revoked token on a new STOMP `CONNECT`;
- unauthorized subscription/send attempt;
- REST persistence succeeds but resynchronization fails;
- Playwright credentials or disposable fixture provisioning unavailable.

## Changelog

### v1.0 — 2026-10-13

- Created the post-Prompt-26 R16 edge-acceptance brief.
- Scoped reconnect, reload, logout cancellation, persisted resynchronization, duplicate suppression, and post-reconnect authorization.
- Added an explicit evidence boundary for transient Redis Pub/Sub and non-durable realtime delivery.
