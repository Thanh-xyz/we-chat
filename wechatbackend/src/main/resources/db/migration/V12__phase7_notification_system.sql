create table if not exists notifications (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    actor_user_id uuid references users(id) on delete set null,
    conversation_id uuid references conversations(id) on delete cascade,
    message_id uuid references messages(id) on delete cascade,
    type varchar(40) not null,
    title varchar(255) not null,
    content text,
    is_read boolean not null default false,
    created_at timestamptz not null default now(),
    read_at timestamptz,
    deleted_at timestamptz,
    constraint ck_notifications_type check (type in (
        'MESSAGE',
        'MENTION',
        'GROUP_INVITE',
        'GROUP_UPDATE',
        'REACTION',
        'SYSTEM'
    ))
);

create index if not exists idx_notifications_user_read
    on notifications (user_id, is_read)
    where deleted_at is null;

create index if not exists idx_notifications_user_created
    on notifications (user_id, created_at desc)
    where deleted_at is null;

create index if not exists idx_notifications_conversation
    on notifications (conversation_id)
    where deleted_at is null;

create index if not exists idx_notifications_message
    on notifications (message_id)
    where deleted_at is null;

create table if not exists notification_preferences (
    id uuid primary key,
    user_id uuid not null unique references users(id) on delete cascade,
    message_enabled boolean not null default true,
    mention_enabled boolean not null default true,
    reaction_enabled boolean not null default true,
    group_enabled boolean not null default true,
    system_enabled boolean not null default true,
    email_enabled boolean not null default false,
    push_enabled boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_notification_preferences_user
    on notification_preferences (user_id);

create table if not exists notification_delivery (
    id uuid primary key,
    notification_id uuid not null references notifications(id) on delete cascade,
    channel varchar(30) not null,
    status varchar(30) not null,
    sent_at timestamptz,
    created_at timestamptz not null default now(),
    constraint ck_notification_delivery_channel check (channel in ('IN_APP', 'PUSH', 'EMAIL')),
    constraint ck_notification_delivery_status check (status in ('PENDING', 'SENT', 'FAILED'))
);

create index if not exists idx_notification_delivery_notification
    on notification_delivery (notification_id);

create index if not exists idx_notification_delivery_status
    on notification_delivery (channel, status, created_at);
