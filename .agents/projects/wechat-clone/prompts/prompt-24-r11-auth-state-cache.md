# Prompt 24 — R11 Redis Auth State Cache and Cross Replica Revocation

Version: v1.0  
Date: 2026-10-13  
Project: WeChat Clone / Real-Time Web Chat System  
Primary agent: `wechat_project_lead`  
Supporting skills: `wechat-flow-trace`, `wechat-spring-data-change`, `wechat-realtime-contract`, `wechat-runtime-verification`  
Reviewer: `wechat_code_reviewer`

## Objective

Implement and verify a bounded Redis-backed cache for the minimal user authentication state used by REST JWT authentication and STOMP `CONNECT`, reducing the current per-request `users` lookup while preserving token-version revocation, account-status checks, cross-replica consistency, and fail-closed behavior.

If Redis, the Compose stack, or required test fixtures are unavailable, report the affected criteria as `BLOCKED`; do not infer a production result from source inspection or a skipped test.

## Current context

Read before changing anything:

- `AGENTS.md`
- `.agents/projects/wechat-clone/PROJECT-CONTEXT.md`
- `.agents/projects/wechat-clone/WORKFLOW.md`
- `wechatbackend/src/main/java/main/com/chat/wechat/common/security/JwtAuthenticationFilter.java`
- `wechatbackend/src/main/java/main/com/chat/wechat/realtime/security/WebSocketAuthChannelInterceptor.java`
- `wechatbackend/src/main/java/main/com/chat/wechat/common/security/JwtTokenService.java`
- `wechatbackend/src/main/java/main/com/chat/wechat/common/security/JwtProperties.java`
- `wechatbackend/src/main/java/main/com/chat/wechat/user/repository/UserRepository.java`
- every service path that increments `token_version`, changes account status, changes roles, changes password, logs out all sessions, or handles refresh-token reuse
- existing Redis configuration, R5 realtime bridge, R6 rate limiter, and their integration tests
- `docs/deployment.md`, `docs/architecture/realtime-redis.md`, `docs/architecture/rate-limiting.md`, and `docs/testing.md`

The current source shows that both `JwtAuthenticationFilter` and `WebSocketAuthChannelInterceptor` perform a `UserRepository.findById` check. `UserRepository` increments `token_version` for account-status, role, logout-all, refresh-token-reuse, and password changes. Redis is already an application dependency for distributed realtime and rate limiting. The deployment documentation currently records Redis-backed authentication sessions and caches as future work.

The older `promote.txt` audit is historical input, not proof of the current state. Re-check the source and tests before reopening older findings such as R1, R2, R3, R4, R17, or R19. Prompt 22's explicit `.env` exception applies only to that Supabase migration and does not carry forward to this task.

## Scope

### In scope

- A small, server-owned Redis auth-state representation containing only the fields required to decide whether a JWT's user is still active and its `token_version` still matches.
- Cache lookup on both the REST JWT filter and STOMP `CONNECT` path.
- Configurable, bounded TTL in the documented 30–60 second range unless the current source establishes a safer compatible value.
- Shared key namespace that includes the deployment/environment boundary and user UUID; it must never include a raw JWT, refresh token, password, presigned URL, or authorization header.
- Cache-miss database lookup, safe serialization, bounded metrics, and Redis-error fallback behavior.
- Invalidation after successful database commit for every token-version or account-status mutation path, including cross-replica behavior.
- Focused unit tests, Redis integration tests, security regression tests, and documentation of the resulting guarantee and limitation.

### Out of scope

- Changing JWT format, signing algorithm, access-token claims, refresh-token schema, or RBAC/ABAC policy.
- Caching roles or permissions separately from the JWT contract.
- Redis Pub/Sub replay, durable outbox work, notification delivery guarantees, attachment storage, MinIO backfill, frontend UI, or the later two-user chat E2E prompt.
- Broad database schema refactoring or a new Flyway migration unless the source proves it is required.
- Editing `.env`, deleting volumes, changing Supabase credentials, or changing deployment secrets. `.env.example` may only be changed with an explicit, secret-free configuration addition that is necessary for this prompt.

## Safety and security constraints

- Preserve server-side authorization and the existing `401`/WebSocket rejection behavior.
- Never fail open because Redis is unavailable, a cache value is malformed, or a cache lookup times out.
- On Redis failure, use a bounded, explicit database fallback or another fail-closed path supported by the source; do not create an unbounded JVM cache and do not accept a token solely from stale local state.
- Do not invalidate before a transaction is committed. A rolled-back status/password/role change must not leave a misleading cache state.
- Do not log cache keys, token values, Redis credentials, request authorization headers, or full serialized values.
- Do not use raw JWTs as Redis keys or rate-limit/cache identities.
- Preserve existing Redis namespaces and R5/R6 behavior. Do not reuse the realtime Pub/Sub channel for cache data.
- Do not run `docker compose down -v`, delete PostgreSQL/Redis volumes, expose internal ports, or publicize private storage.
- Preserve all existing user changes and report only commands actually run.

## Phase 1 — Trace the current contract

Use `wechat-flow-trace` first and produce an internal table with exact file references for:

1. REST bearer-token entry, JWT validation, user-state lookup, principal creation, and failure behavior.
2. STOMP `CONNECT` entry, JWT validation, user-state lookup, principal creation, and rejection behavior.
3. `User.active()` semantics and the exact database fields that determine whether a user is accepted.
4. Every call site that increments `token_version` or changes account status, including role changes, password changes, logout-all, refresh-token reuse, and admin status changes.
5. Transaction boundaries around each mutation and the safest after-commit invalidation point.
6. Existing Redis serializers, timeouts, namespaces, health indicators, and integration-test conventions.

Do not invent a repository method, configuration key, cache format, or invalidation event before confirming the current implementation.

## Phase 2 — Baseline

Before mutation:

```bash
git status --short
git diff --check
cd wechatbackend && ./mvnw test
cd wechatbackend && ./mvnw verify -Predis-integration
```

Run only commands supported by the current checkout. If Docker or the Redis integration profile is unavailable, record the exact blocker as `BLOCKED` and continue with safe focused tests where possible.

## Phase 3 — Design and implement minimally

1. Define an explicit auth-state contract. At minimum it must support comparing the JWT `tokenVersion` with the current server value and rejecting inactive/deleted users. Do not copy the complete `User` row into Redis.
2. Add configuration with validation for the cache TTL, key namespace/environment, and finite Redis operation timeouts. Defaults must be safe and documented.
3. Implement one reusable auth-state lookup boundary used by both the REST filter and the STOMP interceptor, or prove why two implementations cannot share one boundary without changing behavior.
4. On a cache miss, read the authoritative state from PostgreSQL and populate Redis only after a valid result is available. A missing/deleted user must not be cached as an authenticated success.
5. On a cache hit, verify the JWT token version and active state exactly as the current database path does. Claims remain the source for the existing principal authorities; do not silently broaden permissions.
6. Add after-commit invalidation or equivalent write-through behavior for every mutation path discovered in Phase 1. Prove that an invalidation performed by one backend replica is visible to the other through the shared Redis instance.
7. Add bounded counters/diagnostics for hits, misses, database fallback, Redis errors, malformed values, and invalidation failures. Keep labels low-cardinality and keep secrets out of metrics and logs.
8. Update the relevant architecture/deployment documentation with the TTL, key namespace, fallback semantics, revocation timing, and the remaining bounded-staleness limitation.

## Phase 4 — Acceptance criteria

Every criterion must be reported as `PASS`, `FAIL`, or `BLOCKED` with direct evidence.

| ID | Criterion | Required evidence |
|---|---|---|
| A1 | Current REST and STOMP auth flows are traced | Exact entry points, state fields, mutation paths, and tests are listed |
| A2 | Cache hit avoids the per-request user-row lookup | Focused test or instrumented repository verification showing no `findById` on a valid hit |
| A3 | Cache miss is authoritative and safe | PostgreSQL lookup populates Redis; inactive/deleted/missing users cannot authenticate |
| A4 | TTL and namespace are bounded and configurable | Property validation plus Redis `TTL`/key inspection with redacted key names |
| A5 | Token-version revocation works through REST | A token accepted before mutation is rejected after logout-all/password/status/role/reuse revocation according to current contract |
| A6 | Token-version revocation works through STOMP | The same stale token is rejected on a new `CONNECT` after revocation |
| A7 | Every mutation path invalidates or refreshes state | Call-site matrix and regression coverage for all paths found in Phase 1 |
| A8 | Cross-replica visibility is proven | Two independent backend clients/instances share Redis state; a mutation on one makes the stale token fail on the other |
| A9 | Redis failure never authenticates from unsafe stale state | Focused failure test proves bounded database fallback or fail-closed rejection, with no unbounded local growth |
| A10 | Cache data does not leak secrets or authorities | Tests/log inspection show no raw token, password, credential, presigned URL, or accidental role/permission cache payload |
| A11 | Existing auth, realtime, rate-limit, and data regressions remain green | Actual focused and related test commands with pass/fail/skipped outcomes |
| A12 | Documentation states the real guarantee | TTL, fallback, invalidation timing, cross-replica behavior, and Pub/Sub/cache limitations are documented |

## Required edge and failure cases

The implementation or tests must cover at least:

- first request after cache expiry;
- malformed or incompatible Redis cache value;
- Redis timeout/unavailability during a REST request;
- Redis timeout/unavailability during STOMP `CONNECT`;
- transaction rollback after a token-version mutation attempt;
- role change followed by REST and STOMP use of the old token;
- account disable/delete followed by REST and STOMP use of the old token;
- concurrent requests during invalidation;
- a key/value inspection proving that the raw JWT and refresh token are absent;
- a two-replica or two-client shared-Redis test, or an honest `BLOCKED` result when the runtime cannot be made available safely.

## Failure policy

If a check fails:

1. classify the cause as environment, contract, implementation, data, security, or test-fixture;
2. record the exact reproduction and affected criterion;
3. make the smallest in-scope fix only when the defect belongs to R11;
4. add or update a regression test;
5. rerun the affected test and related suite;
6. keep unrelated R-items unchanged.

A skipped test, successful compilation, Redis liveness alone, or a historical prompt claim is not a `PASS` for an acceptance criterion.

## Regression commands

Run the relevant commands supported by the repository and report each separately:

```bash
cd wechatbackend && ./mvnw test
cd wechatbackend && ./mvnw verify -Predis-integration
cd wechatbackend && ./mvnw verify -Ppostgres-integration
git diff --check
```

Run frontend checks only if the implementation changes a browser-visible authentication or STOMP contract. Do not change the frontend merely to make this prompt pass.

## Final report format

```markdown
# Verdict
PASS / FAIL / BLOCKED

## Scope result
- R11 auth-state cache: PASS/FAIL/BLOCKED
- Code changes: yes/no

## Acceptance matrix
| ID | Criterion | Status | Evidence |
|---|---|---|---|
| A1 | ... | ... | ... |

## Cache contract
- Key namespace:
- Cached fields:
- TTL:
- Redis failure behavior:
- Invalidation timing:

## Cross-replica evidence
- Replica/client A:
- Mutation:
- Replica/client B stale-token result:
- REST result:
- STOMP CONNECT result:

## Files inspected and changed
- `path` — reason

## Tests actually run
- `[command]` — actual result

## Limitations and blockers
- [none or concrete item]

## Next action
- [one smallest safe next step]
```

## Definition of Done

R11 is `PASS` only when the current auth contract is preserved, cache hits demonstrably remove the normal user-row lookup, cache misses remain authoritative, TTL/key safety is proven, every token-version mutation path invalidates correctly, stale tokens fail through both REST and STOMP, shared Redis behavior is shown across two clients/replicas, Redis failure is fail-closed or uses a bounded authoritative fallback, and all related tests have fresh evidence.

## Prompt QA cases

Before activating this prompt, verify that it handles:

1. **Happy path:** a valid token authenticates from a populated cache without a database lookup and works through REST and STOMP.
2. **Edge case:** a role/password/status mutation changes `token_version`; an old token fails on another backend after invalidation while a newly issued token works.
3. **Failure case:** Redis is unavailable or contains malformed data; the system does not fail open, does not grow an unbounded local cache, and reports the result honestly as `PASS`, `FAIL`, or `BLOCKED`.

## Changelog

### v1.0 — 2026-10-13

- Created the next task brief after the Supabase migration/backfill prompts.
- Scoped R11 to shared auth-state caching and cross-replica revocation.
- Added REST/STOMP, Redis failure, invalidation coverage, and evidence requirements.
