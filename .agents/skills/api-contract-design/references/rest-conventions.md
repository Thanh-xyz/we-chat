# REST contract conventions

Read when proposing or reviewing a new or changed endpoint. Follow the current repository's conventions first; these points are review checks, not reasons to rewrite working contracts.

## Current baseline (verify before relying on it)

- **Errors:** `common/exception/ErrorResponse` returns `timestamp`, `status`, `error`, `message`, `path`, and `validationErrors` (field → message). It is produced by `GlobalExceptionHandler`. New errors reuse this shape through `ApiException`. Do not introduce `ProblemDetail` or a second envelope without an approved contract change.
- **Message history and search:** cursor (keyset) pagination with an opaque `cursor` and `limit`. Offset is explicitly rejected. The supporting index is `(conversation_id, created_at desc, id desc) where deleted_at is null` (V17).
- **Request correlation:** the `X-Request-Id` header (`audit/service/RequestContextProvider`).

## Status codes

| Situation | Status |
|---|---|
| Created a resource | `201`; return the created representation, plus `Location` when the resource is addressable |
| Successful action with no body | `204` |
| Malformed input or validation failure | `400` with `validationErrors` (the current convention) |
| Unauthenticated | `401` |
| Authenticated but not permitted | `403`, or `404` when revealing existence would leak private data (for example a non-member probing a conversation). Pick one per resource and keep it consistent. |
| Invalid state transition (accepting an already accepted friend request, editing a deleted message) or a unique conflict | `409` |
| Rate limited | `429` |

Never return stack traces, SQL fragments, internal hostnames, object keys, or library versions in `message`.

## Pagination

- Unbounded collections must be paginated, and `limit` must have a server-side maximum.
- Use keyset cursors for append-heavy, time-ordered data such as messages and notifications. The cursor encodes the full sort key including a unique tie-breaker (`created_at`, `id`), and the client treats it as opaque.
- Sort order must be total and stable, and the index must match the filter and the sort.
- Document whether the newest page comes first and how the client deduplicates rows that arrive over both REST and realtime.

## Write safety

- **Retries and double submits:** decide whether the operation is naturally idempotent (PUT or DELETE semantics) or needs a client-supplied key, such as a client message ID with a unique constraint. Record the decision in the contract.
- **Concurrency:** for state transitions, specify the guard (a conditional update, `SELECT ... FOR UPDATE`, or a unique constraint) and the losing caller's status.
- **Payloads:** bound request sizes, array lengths, and string lengths, and keep them consistent with gateway and Spring limits.

## Compatibility

- Additive changes (a new optional field, a new endpoint) are compatible.
- Removing or renaming a field, changing its type or meaning, changing a status code, or changing pagination semantics is a **breaking** change. It needs a coordinated server and client plan in PLAN, and a transition path when an older client may still be running.
- Map every changed field to its frontend consumer in `wechatfrontend/src/services/api`.
