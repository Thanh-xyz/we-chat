alter table conversation_members
    add column if not exists last_read_at timestamptz;

update conversation_members cm
set last_read_at = coalesce(cm.last_read_at, m.created_at, cm.read_at)
from messages m
where cm.last_read_message_id = m.id
  and cm.last_read_at is null;

create index if not exists idx_conversation_members_conversation_user_read
    on conversation_members (conversation_id, user_id, last_read_message_id, last_read_at)
    where left_at is null;

create index if not exists idx_conversation_members_user_unread
    on conversation_members (user_id, archived_at, conversation_id, last_read_at)
    where left_at is null;

create index if not exists idx_messages_unread_count
    on messages (conversation_id, created_at, sender_id)
    where deleted_at is null and is_recalled = false and recalled_at is null;

create index if not exists idx_message_user_deletions_user_message
    on message_user_deletions (user_id, message_id);
