alter table conversation_members
    add column if not exists pinned_at timestamptz,
    add column if not exists muted_until timestamptz,
    add column if not exists archived_at timestamptz;

create index if not exists idx_conversation_members_user_archive_pin
    on conversation_members (user_id, archived_at, pinned_at desc)
    where left_at is null;

create table if not exists message_attachments (
    id uuid primary key,
    message_id uuid not null references messages(id) on delete cascade,
    file_name varchar(255) not null,
    file_url varchar(2048) not null,
    file_type varchar(100),
    file_size bigint,
    created_at timestamptz not null default now()
);

alter table message_attachments
    add column if not exists file_type varchar(100);

alter table message_attachments
    alter column storage_key drop not null,
    alter column mime_type drop not null,
    alter column file_size drop not null;

create index if not exists idx_message_attachments_message_id
    on message_attachments (message_id);

insert into permissions (id, code, name, description)
values
    ('10000000-0000-0000-0000-000000000014', 'MESSAGE_EDIT', 'Edit messages', 'Edit own messages within the allowed edit window'),
    ('10000000-0000-0000-0000-000000000015', 'MESSAGE_DELETE', 'Delete messages', 'Delete messages for current user or moderation use cases'),
    ('10000000-0000-0000-0000-000000000016', 'MESSAGE_REACT', 'React to messages', 'Add or remove own reactions to messages')
on conflict do nothing;

insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code in ('MESSAGE_EDIT', 'MESSAGE_DELETE', 'MESSAGE_REACT')
where r.code in ('USER', 'MODERATOR')
on conflict do nothing;

insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code in ('MESSAGE_DELETE', 'MESSAGE_REACT')
where r.code in ('ADMIN', 'SUPER_ADMIN')
on conflict do nothing;
