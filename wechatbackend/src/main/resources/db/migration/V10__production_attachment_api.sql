alter table message_attachments
    alter column message_id drop not null;

alter table message_attachments
    add column if not exists uploader_id uuid references users(id) on delete restrict,
    add column if not exists conversation_id uuid references conversations(id) on delete cascade,
    add column if not exists original_file_name varchar(255),
    add column if not exists checksum varchar(128),
    add column if not exists scan_status varchar(40) not null default 'PENDING',
    add column if not exists deleted_at timestamptz,
    add column if not exists updated_at timestamptz not null default now();

update message_attachments ma
set conversation_id = m.conversation_id,
    uploader_id = m.sender_id,
    original_file_name = coalesce(ma.original_file_name, ma.file_name),
    updated_at = coalesce(ma.updated_at, ma.created_at, now())
from messages m
where ma.message_id = m.id
  and (ma.conversation_id is null or ma.uploader_id is null or ma.original_file_name is null);

update message_attachments
set original_file_name = coalesce(original_file_name, file_name),
    updated_at = coalesce(updated_at, created_at, now())
where original_file_name is null;

update message_attachments
set file_type = case
    when upper(file_type) in ('IMAGE', 'FILE', 'VOICE') then upper(file_type)
    when lower(coalesce(file_type, mime_type, '')) like 'image/%' then 'IMAGE'
    when lower(coalesce(file_type, mime_type, '')) like 'audio/%' then 'VOICE'
    when file_type is not null or mime_type is not null then 'FILE'
    else null
end
where file_type is null
   or upper(file_type) not in ('IMAGE', 'FILE', 'VOICE');

do $$
begin
    if not exists (
        select 1 from pg_constraint where conname = 'ck_message_attachments_file_type'
    ) then
        alter table message_attachments
            add constraint ck_message_attachments_file_type
            check (file_type is null or file_type in ('IMAGE', 'FILE', 'VOICE'));
    end if;
end $$;

do $$
begin
    if not exists (
        select 1 from pg_constraint where conname = 'ck_message_attachments_scan_status'
    ) then
        alter table message_attachments
            add constraint ck_message_attachments_scan_status
            check (scan_status in ('PENDING', 'CLEAN', 'INFECTED', 'FAILED'));
    end if;
end $$;

create index if not exists idx_message_attachments_conversation_id
    on message_attachments (conversation_id);

create index if not exists idx_message_attachments_uploader_id
    on message_attachments (uploader_id);

create index if not exists idx_message_attachments_storage_key
    on message_attachments (storage_key);

create index if not exists idx_message_attachments_deleted_at
    on message_attachments (deleted_at);

create index if not exists idx_message_attachments_pending_user_conversation
    on message_attachments (uploader_id, conversation_id, created_at)
    where message_id is null and deleted_at is null;
