# Data retention and cleanup

R14 adds bounded, periodic cleanup for terminal authentication tokens, user-visible notifications, and audit data. Retention starts only after a row is no longer active; it does not change JWT, refresh-token, password-reset, or email-verification TTLs.

## Default policies

| Entity | Cleanup condition | Retention after terminal timestamp | Batch size / run cap | Fixed delay |
| --- | --- | --- | --- | --- |
| `refresh_tokens` | `expires_at` is old, or `revoked_at` is old | 30 days | 500 / 20 batches | 1 hour |
| `password_reset_tokens` | `expires_at` is old, or `used_at` is old | 1 day | 500 / 20 batches | 1 hour |
| `email_verification_tokens` | `expires_at` is old, or `used_at` is old | 7 days | 500 / 20 batches | 1 hour |
| `notifications` | Soft-deleted at an old `deleted_at`, or visible/read with an old `read_at` | 180 days | 500 / 40 batches | 1 day |
| `audit_logs` (standard) | Non-security action with old `created_at` | 180 days | 500 / 100 batches | 1 day |
| `audit_logs` (security/admin) | Configured security action with old `created_at` | 365 days | Shared audit cap | 1 day |

All comparisons are strict (`timestamp < cutoff`). An unread, non-deleted notification is never eligible based only on its age. Deleting a notification also deletes its `notification_delivery` children through the existing foreign-key cascade.

The refresh retention defaults to the current 30-day refresh-token lifetime. This retains revoked/rotated records through the period in which the original token could otherwise still be presented, while active refresh tokens remain untouched.

## Index strategy

Existing indexes on token `expires_at` and audit `(created_at)` / `(action, created_at)` are reused. Flyway V16 adds only the missing terminal-state indexes: partial indexes for token `revoked_at`/`used_at`, notification `deleted_at`, and visible read-notification `read_at`. Cleanup predicates compare these columns directly to bind parameters, allowing PostgreSQL to use index scans or bitmap index scans without applying functions to indexed values.

## Configuration

Every policy has environment-overridable `ENABLED`, `RETENTION`, `BATCH_SIZE`, `MAX_BATCHES_PER_RUN`, `FIXED_DELAY`, and `INITIAL_DELAY` settings in `.env.example`. Durations use ISO-8601 values such as `PT1H`, `P30D`, and `P180D`.

Audit cleanup additionally supports:

- `CLEANUP_AUDIT_LOGS_SECURITY_RETENTION`
- `CLEANUP_AUDIT_LOGS_SECURITY_ACTIONS`

The configured security retention cannot be shorter than standard audit retention. Retention, delays, batch sizes, and run caps fail startup validation when they are zero, negative, or outside the documented bounds; fixed delays shorter than one minute are rejected to prevent a busy loop. Set an individual `*_ENABLED=false` value to disable that job without disabling the other cleanup policies.

## Execution safety

- Each repository call deletes at most one configured batch and runs in a `REQUIRES_NEW` transaction. A successful batch commits before the next batch starts.
- A job stops after a short final batch or its configured run cap, preventing an unbounded catch-up loop.
- Cleanup queries compare indexed timestamp columns directly; they do not wrap those columns in date functions.
- Jobs are idempotent. If a later batch fails, committed earlier batches remain valid and the next scheduled execution continues with remaining eligible rows.
- Job exceptions are contained and do not terminate the application or trigger a busy retry loop.
- An in-memory guard prevents the same job from overlapping inside one application instance.

The in-memory guard is single-instance only. A multi-instance deployment needs distributed scheduler coordination/locking or external job execution; R14 intentionally does not add Redis or another distributed lock.

## Observability

Each enabled execution logs only the low-cardinality job name, batch count, deleted-row count, duration, and failure exception class. Token values, token hashes, user identifiers, SQL bind values, and secrets are not logged.

Micrometer exports:

- `cleanup_runs_total{job="..."}`
- `cleanup_deleted_rows_total{job="..."}`
- `cleanup_failures_total{job="..."}`
- `cleanup_skipped_total{job="..."}` for overlapping invocations
- `cleanup_duration_seconds{job="..."}`

The only metric label is the fixed job name; user, token, notification, conversation, and audit identifiers are never labels.

## Audit policy warning

The 180/365-day defaults are a technical operational policy based on the existing project guidance, not a compliance, legal-hold, or archival guarantee. Before production use, the system owner must confirm applicable retention obligations and configure longer retention or disable audit deletion when archive/export or legal hold is required. This job does not create cold storage or certify regulatory compliance.
