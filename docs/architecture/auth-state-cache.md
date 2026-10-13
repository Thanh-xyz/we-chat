# Authentication-state cache (R11)

The REST JWT filter and STOMP `CONNECT` interceptor share `UserAuthStateService`. It checks a Redis-backed cache before reading the authoritative `users` row and evaluates only the minimal current authorization state:

```json
{"userId":"...","tokenVersion":3,"active":true}
```

JWT roles and permissions remain claims. They are not copied into Redis, and the cache never stores access tokens, refresh tokens, password material, attachment data, or presigned URLs.

## Key and TTL contract

Keys have the form:

```text
{namespace}:{environment}:user:{userUuid}
```

`app.auth-state.namespace` and `app.auth-state.environment` are validated at startup. `app.auth-state.ttl` defaults to `PT30S` and accepts only 30–60 seconds. There is no unbounded local cache.

## Failure and invalidation semantics

- A hit avoids the database lookup.
- A miss reads PostgreSQL and refreshes the Redis entry.
- Redis errors and malformed values are treated as cache misses and fall back to PostgreSQL; authorization never fails open.
- `token_version` and account-status writes invalidate the user key after the surrounding transaction commits. If a repository method is called outside a transaction, invalidation runs immediately.
- Redis Pub/Sub availability is not required for the cache contract; this cache uses ordinary Redis string commands and is shared by backend replicas.

Because the cache TTL is bounded, an external state change can remain visible on a replica for at most the configured TTL if its invalidation command is unavailable. Successful database mutations invalidate the shared key after commit.

The bounded Micrometer counters are `auth_state_cache_lookups` with the fixed `result` values `hit`, `miss`, `redis_error`, `malformed`, and `disabled`, plus `auth_state_cache_writes`, `auth_state_cache_invalidations`, `auth_state_cache_errors`, `auth_state_cache_invalidation_errors`, `auth_state_cache_malformed_values`, and `auth_state_database_lookups`. They do not carry user IDs, tokens, or other high-cardinality labels.

## Evidence expectations

Focused tests cover REST stale-token rejection, STOMP stale-token rejection, malformed values, deferred invalidation, TTL/key safety, and DB fallback. `AuthStateCacheRedisIT` uses two independent Redis clients to verify cross-replica visibility and invalidation. Run it with the `redis-integration` Maven profile when Docker is available.
