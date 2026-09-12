alter table message_attachments
    add column if not exists file_category varchar(20),
    add column if not exists width integer,
    add column if not exists height integer,
    add column if not exists duration_seconds integer,
    add column if not exists download_count bigint not null default 0;

update message_attachments
set file_category = case
        when upper(coalesce(file_category, file_type, '')) in ('IMAGE', 'VIDEO', 'VOICE', 'FILE') then upper(coalesce(file_category, file_type))
        when lower(coalesce(mime_type, '')) like 'image/%' then 'IMAGE'
        when lower(coalesce(mime_type, '')) like 'video/%' then 'VIDEO'
        when lower(coalesce(mime_type, '')) like 'audio/%' then 'VOICE'
        else 'FILE'
    end
where file_category is null
   or upper(file_category) not in ('IMAGE', 'VIDEO', 'VOICE', 'FILE');

update message_attachments
set duration_seconds = duration
where duration_seconds is null
  and duration is not null;

update message_attachments
set download_count = 0
where download_count is null;

alter table message_attachments
    alter column file_category set not null;

do $$
begin
    if not exists (
        select 1 from pg_constraint where conname = 'ck_message_attachments_file_category'
    ) then
        alter table message_attachments
            add constraint ck_message_attachments_file_category
            check (file_category in ('IMAGE', 'VIDEO', 'VOICE', 'FILE'));
    end if;
end $$;

create index if not exists idx_message_attachments_conversation_created_desc
    on message_attachments (conversation_id, created_at desc)
    where deleted_at is null;

create index if not exists idx_message_attachments_uploader
    on message_attachments (uploader_id);

create index if not exists idx_message_attachments_file_category
    on message_attachments (file_category);

create index if not exists idx_message_attachments_message
    on message_attachments (message_id);

create index if not exists idx_message_attachments_storage
    on message_attachments (storage_key);

create index if not exists idx_message_attachments_deleted
    on message_attachments (deleted_at);

create index if not exists idx_message_attachments_gallery
    on message_attachments (conversation_id, file_category, created_at desc)
    where deleted_at is null and message_id is not null;

comment on column message_attachments.file_category is 'Business media category. MIME type is validation metadata only.';
comment on column message_attachments.storage_key is 'Internal storage object key. Never expose this value through public APIs.';
