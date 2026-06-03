update conversations
set type = 'DIRECT'
where type = 'PRIVATE';

alter table conversations
    drop constraint if exists ck_conversations_type;

alter table conversations
    add constraint ck_conversations_type check (type in ('DIRECT', 'GROUP'));

alter table conversations
    add column if not exists avatar_url text,
    add column if not exists last_message_id uuid,
    add column if not exists last_message_at timestamptz;

create index if not exists idx_conversations_last_message_at on conversations (last_message_at desc);

alter table conversation_members
    add column if not exists nickname varchar(120),
    add column if not exists left_at timestamptz,
    add column if not exists muted_until timestamptz,
    add column if not exists pinned_at timestamptz;

create index if not exists idx_conversation_members_user_id_left_at on conversation_members (user_id, left_at);

alter table messages
    drop constraint if exists ck_messages_type;

alter table messages
    add constraint ck_messages_type check (message_type in ('TEXT', 'IMAGE', 'FILE', 'VOICE', 'SYSTEM'));

alter table messages
    add column if not exists reply_to_message_id uuid,
    add column if not exists edited_at timestamptz,
    add column if not exists recalled_at timestamptz;

alter table messages
    alter column content drop not null;

do $$
begin
    if not exists (
        select 1 from pg_constraint where conname = 'fk_messages_reply_to_message'
    ) then
        alter table messages
            add constraint fk_messages_reply_to_message
            foreign key (reply_to_message_id) references messages(id) on delete set null;
    end if;
end $$;

create table if not exists direct_conversations (
    conversation_id uuid primary key references conversations(id) on delete cascade,
    user_low_id uuid not null references users(id) on delete cascade,
    user_high_id uuid not null references users(id) on delete cascade,
    created_at timestamptz not null default now(),
    constraint ck_direct_conversations_pair_order check (user_low_id < user_high_id),
    constraint uq_direct_conversations_pair unique (user_low_id, user_high_id)
);

insert into direct_conversations (conversation_id, user_low_id, user_high_id)
select cm1.conversation_id, cm1.user_id, cm2.user_id
from conversation_members cm1
join conversation_members cm2
    on cm2.conversation_id = cm1.conversation_id
   and cm1.user_id < cm2.user_id
join conversations c on c.id = cm1.conversation_id
where c.type = 'DIRECT'
on conflict do nothing;

create table if not exists message_attachments (
    id uuid primary key,
    message_id uuid not null references messages(id) on delete cascade,
    storage_key text not null,
    file_url text not null,
    file_name varchar(255) not null,
    mime_type varchar(120) not null,
    file_size bigint not null,
    width integer,
    height integer,
    duration integer,
    created_at timestamptz not null default now()
);

create index if not exists idx_message_attachments_message_id on message_attachments (message_id);

create table if not exists message_receipts (
    message_id uuid not null references messages(id) on delete cascade,
    user_id uuid not null references users(id) on delete cascade,
    delivered_at timestamptz,
    seen_at timestamptz,
    primary key (message_id, user_id)
);

create index if not exists idx_message_receipts_user_seen on message_receipts (user_id, seen_at);

create table if not exists message_reactions (
    message_id uuid not null references messages(id) on delete cascade,
    user_id uuid not null references users(id) on delete cascade,
    emoji varchar(40) not null,
    created_at timestamptz not null default now(),
    primary key (message_id, user_id, emoji)
);

create table if not exists message_mentions (
    message_id uuid not null references messages(id) on delete cascade,
    mentioned_user_id uuid not null references users(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (message_id, mentioned_user_id)
);

create table if not exists conversation_audit_logs (
    id uuid primary key,
    conversation_id uuid not null references conversations(id) on delete cascade,
    actor_user_id uuid references users(id) on delete set null,
    action varchar(120) not null,
    target_user_id uuid references users(id) on delete set null,
    before_value text,
    after_value text,
    created_at timestamptz not null default now()
);

create index if not exists idx_conversation_audit_logs_conversation_id on conversation_audit_logs (conversation_id, created_at desc);

do $$
begin
    if not exists (
        select 1 from pg_constraint where conname = 'fk_conversations_last_message'
    ) then
        alter table conversations
            add constraint fk_conversations_last_message
            foreign key (last_message_id) references messages(id) on delete set null;
    end if;
end $$;

insert into permissions (id, code, name, description)
values
    ('10000000-0000-0000-0000-000000000009', 'CONVERSATION_READ', 'Read conversations', 'Read conversations where the user is an active member'),
    ('10000000-0000-0000-0000-000000000010', 'CONVERSATION_WRITE', 'Write conversations', 'Create and update conversations'),
    ('10000000-0000-0000-0000-000000000011', 'MESSAGE_READ', 'Read messages', 'Read messages in conversations where the user is an active member'),
    ('10000000-0000-0000-0000-000000000012', 'MESSAGE_SEND', 'Send messages', 'Send messages in conversations where the user is an active member'),
    ('10000000-0000-0000-0000-000000000013', 'ATTACHMENT_UPLOAD', 'Upload attachments', 'Upload files and media for chat messages')
on conflict (id) do nothing;

insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code in ('CONVERSATION_READ', 'CONVERSATION_WRITE', 'MESSAGE_READ', 'MESSAGE_SEND', 'ATTACHMENT_UPLOAD')
where r.code = 'USER'
on conflict do nothing;

insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code in ('CONVERSATION_READ', 'CONVERSATION_WRITE', 'MESSAGE_READ', 'MESSAGE_SEND', 'MESSAGE_MODERATE', 'ATTACHMENT_UPLOAD')
where r.code = 'MODERATOR'
on conflict do nothing;

insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code in ('CONVERSATION_READ', 'MESSAGE_READ', 'MESSAGE_MODERATE')
where r.code in ('ADMIN', 'SUPER_ADMIN')
on conflict do nothing;
