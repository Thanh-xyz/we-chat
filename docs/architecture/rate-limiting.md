# Distributed rate limiting (R6)

Rate limiting keeps the existing application policies and moves their shared state from a JVM-local map to Redis in distributed mode.

## Policies

| Operation | Default limit | Identity/key | Scope | Redis outage behavior |
| --- | --- | --- | --- | --- |
| `auth-login` | 5 / 1 minute | normalized client IP | HTTP POST `/api/auth/login` | bounded local fallback: 1 / 1 minute |
| `auth-register` | 5 / 1 minute | normalized client IP | HTTP POST `/api/auth/register` | bounded local fallback: 1 / 1 minute |
| `auth-refresh` | 20 / 1 minute | normalized client IP | HTTP POST `/api/auth/refresh` and `/refresh-token` | bounded local fallback: 1 / 1 minute |
| `auth-resend-verification` | 3 / 15 minutes | normalized client IP | HTTP POST `/api/auth/resend-verification` | bounded local fallback: 1 / 1 minute |
| `ws-connect` | 20 / 1 minute | server-resolved WebSocket peer IP | STOMP `CONNECT` | bounded local fallback: 1 / 1 minute |
| `ws-message-send` | 60 / 1 minute | validated WebSocket `userId` | STOMP SEND to a conversation | bounded local fallback: 1 / 1 minute |

Capacity and refill variables remain configurable under `app.rate-limit.*`; the defaults above are the existing values. The HTTP refresh policy remains IP-based because that is the pre-authenticated policy that existed before R6. Password reset, email verification, and REST message creation were not rate-limited by the existing implementation and are unchanged.

## Key and storage design

Redis keys have an independent namespace:

```text
webchat:ratelimit:<environment>:<operation>:ip:<normalized-ip>
webchat:ratelimit:<environment>:<operation>:user:<uuid>
```

The namespace is separate from the R5 Pub/Sub channel and is configurable with `RATE_LIMIT_NAMESPACE` and `RATE_LIMIT_ENVIRONMENT`. Values contain only token count and the next refill timestamp. Every key receives a TTL ending at the next refill boundary; no cleanup job is required.

The Redis implementation uses one Lua script for read, interval refill, consume, and `PEXPIRE`. It uses Redis server `TIME`, so concurrent backend instances cannot race through stale JVM-local state and do not depend on different JVM clocks.

Raw JWT is never used as rate-limit identity or key. The `Authorization` header, password, email, message body, and arbitrary request identifiers are not part of rate-limit state. The limiter never parses `X-Forwarded-For`, `X-Real-IP`, or `Forwarded`; HTTP uses the server-resolved remote address after the trusted R19 proxy layer, and WebSocket CONNECT captures the server-resolved peer address during the handshake.

## Runtime modes and failure behavior

- Local/test mode uses Bucket4j in memory. Its cache has a maximum size and evicts entries after their refill interval.
- Production/Compose sets `RATE_LIMIT_DISTRIBUTED_ENABLED=true`; the production guard fails startup if it is disabled.
- Redis is mandatory infrastructure for the production policy and `rateLimitRedis` is part of production readiness. Liveness is not tied to Redis, so a temporary Redis outage does not create a restart loop.
- During a Redis outage, the limiter does not fail open. It uses the explicit conservative local fallback (`RATE_LIMIT_FALLBACK_CAPACITY`, `RATE_LIMIT_FALLBACK_REFILL_MINUTES`, and bounded `RATE_LIMIT_FALLBACK_MAX_ENTRIES`). This is degraded protection, not equivalent to normal distributed operation; each JVM has its own bounded fallback state until Redis recovers.

Metrics are low-cardinality and use operation/result/backend labels only: `rate_limit_allowed_total`, `rate_limit_rejected_total`, `rate_limit_errors_total`, `rate_limit_redis_failures_total`, and `rate_limit_fallback_total`. Logs contain operation, fallback result, and exception class, never the key or credential.

## Verification

The Redis integration profile exercises two independent limiter clients against one Redis server. It verifies atomic capacity sharing, operation isolation, positive TTL, and that generated keys do not contain a raw-token marker or authorization material. Docker runtime verification should send the same login identity/IP through both backend replicas and observe the configured global limit rather than a per-replica limit.
