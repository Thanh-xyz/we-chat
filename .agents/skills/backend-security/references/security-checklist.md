# Backend security checklist

Read when a change crosses an identity, authorization, token, rate-limit, attachment, or gateway boundary. Use only the sections the change touches, and record each item checked as **holds**, **violated**, or **not verified**, with its evidence.

## Authorization

- Check at the **resource** level, not only by route or role. Every query that returns or mutates conversation data includes the caller's membership or ownership in its predicate or a preceding guard.
- Deny by default. New endpoints and STOMP destinations are unreachable until explicitly allowed (`SecurityConfig`, `WebSocketAuthChannelInterceptor`, and the destination policy).
- REST, STOMP `SUBSCRIBE`/`SEND`, and attachment download apply the **same** rule for the same resource.
- Identifiers from the client (conversation, message, attachment, or user IDs) are never trusted as proof of access.
- Client-side hiding is not authorization.

## Tokens and sessions

- Validate the JWT signature, issuer, expiry, and the expected token type (access vs refresh) on every authenticated path.
- Refresh tokens rotate on use. A reused or revoked refresh token is rejected. Logout, password change, or deactivation invalidates outstanding tokens (token-version and auth-state checks, including cache staleness and the behavior when Redis is unavailable).
- Cookies carrying refresh tokens use `HttpOnly`, `Secure` (outside local development), and an explicit `SameSite`.
- Never log tokens, password-reset or verification tokens, `Authorization` headers, or presigned URLs.

## Input, errors, and data exposure

- Server-side validation exists for every field, and size limits are bounded.
- Error responses do not reveal whether a private resource exists to a non-member (see the 403/404 rule in `api-contract-design`).
- DTOs return only what the caller may see. Check for accidental exposure of emails, internal flags, or other users' data.
- Use parameterized SQL only. Never concatenate request values into JDBC SQL, including `ORDER BY` columns: map those through an allow-list.

## Abuse and concurrency

- Authentication, verification-email, password-reset, upload, and message-send paths have rate limits. Production uses the Redis-backed limiter; check the fallback behavior.
- Races on state transitions (friend requests, membership changes, attachment finalize) are guarded by constraints or locking, not by a read-then-write in application code.

## Attachments

- Uploads are validated server-side (size, type, finalize state). Only `READY` objects are downloadable, and only by authorized members.
- Presigned URLs are short-lived credentials. Never persist them, log them, or return them to a non-member.

## Gateway and headers (with `deployment-operations`)

- CORS and WebSocket origins are exact lists, never `*`.
- `/actuator/*` is deny-by-default except for the approved paths.
- Security headers are considered at the gateway: `X-Content-Type-Options: nosniff`, `Referrer-Policy`, `X-Frame-Options` or CSP `frame-ancestors`, and HSTS when TLS terminates there.

## Supply chain and secrets

- A new dependency needs a reason and a pinned version.
- Scan the diff for secrets before handoff, for example: `git diff | grep -nEi '(api[_-]?key|secret|password|token|BEGIN [A-Z ]*PRIVATE KEY)'`. Report the file and line only, never the value.
