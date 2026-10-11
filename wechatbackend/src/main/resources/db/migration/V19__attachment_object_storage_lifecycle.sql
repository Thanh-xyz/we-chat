alter table message_attachments
    add column if not exists storage_status varchar(20) not null default 'READY';

update message_attachments
set storage_status = 'READY'
where storage_status is null
   or storage_status not in ('INIT', 'READY', 'FAILED', 'DELETE_PENDING', 'DELETED');

do $$
begin
    if not exists (
        select 1 from pg_constraint where conname = 'ck_message_attachments_storage_status'
    ) then
        alter table message_attachments
            add constraint ck_message_attachments_storage_status
            check (storage_status in ('INIT', 'READY', 'FAILED', 'DELETE_PENDING', 'DELETED'));
    end if;
end $$;

create index if not exists idx_message_attachments_storage_status_created
    on message_attachments (storage_status, created_at)
    where storage_status in ('INIT', 'FAILED', 'DELETE_PENDING');

comment on column message_attachments.storage_status is
    'Object lifecycle: INIT before presigned upload finalization, READY after server validation, FAILED or DELETE_PENDING for cleanup.';
