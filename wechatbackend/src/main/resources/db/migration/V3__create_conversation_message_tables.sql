create table if not exists conversations (
    id uuid primary key,
    type varchar(30) not null,
    name varchar(160),
    created_by uuid not null references users(id) on delete restrict,
    deleted_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_conversations_type check (type in ('PRIVATE', 'GROUP'))
);

create index if not exists ix_conversations_created_by on conversations (created_by);
create index if not exists ix_conversations_deleted_at on conversations (deleted_at);

create table if not exists conversation_members (
    conversation_id uuid not null references conversations(id) on delete cascade,
    user_id uuid not null references users(id) on delete cascade,
    member_role varchar(30) not null default 'MEMBER',
    joined_at timestamptz not null default now(),
    last_read_message_id uuid,
    muted boolean not null default false,
    primary key (conversation_id, user_id),
    constraint ck_conversation_members_role check (member_role in ('OWNER', 'ADMIN', 'MEMBER'))
);

create index if not exists ix_conversation_members_user_id on conversation_members (user_id);

create table if not exists messages (
    id uuid primary key,
    conversation_id uuid not null references conversations(id) on delete cascade,
    sender_id uuid not null references users(id) on delete restrict,
    content text not null,
    message_type varchar(30) not null default 'TEXT',
    deleted_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_messages_type check (message_type in ('TEXT', 'IMAGE', 'FILE'))
);

alter table conversation_members
    add constraint fk_conversation_members_last_read_message
    foreign key (last_read_message_id) references messages(id) on delete set null;

create index if not exists ix_messages_conversation_created_at on messages (conversation_id, created_at desc);
create index if not exists ix_messages_sender_id on messages (sender_id);
