# Search architecture

Search is split by domain rather than combining messages, users, and conversations into one database query:

- `GET /api/conversations/{conversationId}/messages/search` searches visible message content and sender username, email, or display name. It first runs a content branch and a sender branch, each with the conversation, deletion, recall, and current-user visibility predicates in SQL. The application merges both bounded result sets by message id and sorts them by `(created_at desc, id desc)`, preserving the message keyset cursor contract without an `OR` spanning message and joined-user columns.
- `GET /api/users/search` searches active, non-deleted users and applies the existing relationship/block visibility rules.
- `GET /api/conversations/search` searches group names and visible member identity fields for conversations in which the actor is an active member. Its offset pagination contract is unchanged.

All search queries are parameter-bound, normalize case with a locale-independent rule, return an empty result for blank input, and reject input longer than 100 characters. Message search keeps the existing membership authorization check before reading results; user and conversation visibility filtering remains in their service/repository paths rather than in a post-search authorization shortcut.

## PostgreSQL indexes

The indexed expressions match the predicates used by the queries:

- `idx_messages_content_trgm` on `lower(messages.content)` for non-deleted, non-recalled messages;
- `idx_users_username_trgm`, `idx_users_email_trgm`, and `idx_users_display_name_trgm` on non-deleted users;
- `idx_conversations_name_trgm` on non-deleted group conversation names;
- `idx_messages_conversation_created_id` for message conversation scope and keyset ordering.

The older V8 search migration is retained unchanged for Flyway checksum compatibility. V18 is the enforcement/repair migration: it requires `pg_trgm`, then uses `create index if not exists` for the same named indexes so successful V8 installs do not receive duplicates. If the extension cannot be installed or is not permitted by the database administrator, deployment fails clearly instead of starting with an unindexed search path.

Functional PostgreSQL coverage runs with `mvn verify -Ppostgres-integration` on an ephemeral PostgreSQL 17 Testcontainer and validates the extension, index catalog, search results, visibility, deduplication, and cursor semantics after Flyway migrates an empty database. H2 tests remain useful for fast result checks but are not evidence of PostgreSQL compatibility. Planner verification is deliberately opt-in with `RUN_DB_PERF_TESTS=true`; no index-plan or latency claim is made when that layer is not run.

## PostgreSQL 17 planner observations

The opt-in R20 benchmark executes the production-shaped content branch with bound parameters, the `message_user_deletions` visibility predicate, ordering, and the `limit + 1` lookahead. It uses 5,000 messages in a small conversation and 45,000 in a large conversation, then measures rare, common, and no-match terms after `ANALYZE`.

On the local PostgreSQL 17.11 Testcontainer, the parameterized `lower(content) like ?` form selected a `Bitmap Index Scan` on `idx_messages_content_trgm` for all six scenarios. Rare terms completed in about 4.2–4.3 ms, no-match terms in under 1 ms, and common terms in about 55–68 ms on this 50,000-row synthetic dataset. Common-term cost is expected to be higher because many rows match; the benchmark does not establish a production latency SLO.

An earlier literal, conversation-scoped diagnostic selected `idx_messages_unread_count` for a rare term. That is a valid cost-based alternative because the index provides conversation filtering and ordering; it does not indicate an expression or `gin_trgm_ops` mismatch. The R20 test keeps both the actual bound query shape and the rare/common/no-match selectivity counts so future planner changes remain visible without forcing a GIN plan in production.

The existing query policy also permits one- and two-character searches. Those inputs cannot provide three-character trigrams, so the planner may prefer a conversation-oriented index or another cost-based path; this is a known selectivity limitation, not a correctness fallback. Search semantics and the current 100-character validation contract remain unchanged.
