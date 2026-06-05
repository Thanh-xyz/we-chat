alter table conversation_members
    add column if not exists read_at timestamptz;

alter table messages
    add column if not exists is_edited boolean not null default false,
    add column if not exists is_recalled boolean not null default false;

update messages
set is_edited = true
where edited_at is not null and is_edited = false;

update messages
set is_recalled = true
where recalled_at is not null and is_recalled = false;

create table if not exists message_user_deletions (
    message_id uuid not null references messages(id) on delete cascade,
    user_id uuid not null references users(id) on delete cascade,
    deleted_at timestamptz not null default now(),
    primary key (message_id, user_id)
);

create index if not exists idx_message_user_deletions_user_id
    on message_user_deletions (user_id, deleted_at desc);

create table if not exists message_reactions (
    message_id uuid not null references messages(id) on delete cascade,
    user_id uuid not null references users(id) on delete cascade,
    emoji varchar(40) not null,
    created_at timestamptz not null default now(),
    primary key (message_id, user_id, emoji)
);

create index if not exists idx_message_reactions_message_id
    on message_reactions (message_id);

create index if not exists idx_conversation_members_read_at
    on conversation_members (conversation_id, user_id, read_at);

create index if not exists idx_messages_conversation_content
    on messages (conversation_id, created_at desc)
    where deleted_at is null;
