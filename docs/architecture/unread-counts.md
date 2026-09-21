# Conversation unread counts

Conversation list and conversation search load unread counts through one batch aggregation after the authorized conversation page is loaded:

```text
authorized conversations
        ↓
conversation IDs
        ↓
one grouped unread-count query scoped to the current user
        ↓
merge counts, defaulting missing IDs to 0
```

The aggregation preserves the existing unread semantics: active membership, non-deleted and non-recalled messages, messages after the member's `last_read_at` (or the referenced last-read message timestamp), messages sent by someone else, and messages not deleted-for-me.

The current conversation page is capped at 100 items, so the batch query uses a bounded `IN` list. Existing indexes from V11 support the access pattern: `idx_conversation_members_conversation_user_read`, `idx_conversation_members_user_unread`, `idx_messages_unread_count`, and `idx_message_user_deletions_user_message`. No additional duplicate index is required for this path.

Empty conversation pages short-circuit before any unread-count repository call. Individual conversation read/unread endpoints retain their existing single-conversation behavior.
