alter table audit_logs
    add column if not exists actor_username varchar(80),
    add column if not exists actor_email varchar(255),
    add column if not exists resource_type varchar(80),
    add column if not exists resource_id varchar(120),
    add column if not exists target_user_id uuid references users(id) on delete set null,
    add column if not exists conversation_id uuid references conversations(id) on delete set null,
    add column if not exists message_id uuid references messages(id) on delete set null,
    add column if not exists request_id varchar(128),
    add column if not exists trace_id varchar(128),
    add column if not exists metadata jsonb,
    add column if not exists result varchar(20) not null default 'SUCCESS',
    add column if not exists failure_reason text;

update audit_logs
set resource_type = coalesce(resource_type, target_type),
    resource_id = coalesce(resource_id, target_id)
where resource_type is null;

update audit_logs
set target_user_id = case
        when resource_type = 'USER' and resource_id ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
            and exists (select 1 from users where users.id = resource_id::uuid)
            then resource_id::uuid
        else target_user_id
    end,
    conversation_id = case
        when resource_type = 'CONVERSATION' and resource_id ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
            and exists (select 1 from conversations where conversations.id = resource_id::uuid)
            then resource_id::uuid
        else conversation_id
    end,
    message_id = case
        when resource_type = 'MESSAGE' and resource_id ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
            and exists (select 1 from messages where messages.id = resource_id::uuid)
            then resource_id::uuid
        else message_id
    end
where resource_id is not null;

alter table audit_logs
    alter column resource_type set not null;

alter table audit_logs
    alter column before_value type jsonb using case
        when before_value is null or before_value = '' then null
        else before_value::jsonb
    end,
    alter column after_value type jsonb using case
        when after_value is null or after_value = '' then null
        else after_value::jsonb
    end;

do $$
begin
    if not exists (
        select 1 from pg_constraint where conname = 'ck_audit_logs_result'
    ) then
        alter table audit_logs
            add constraint ck_audit_logs_result check (result in ('SUCCESS', 'FAILED'));
    end if;
end $$;

create index if not exists ix_audit_logs_actor_created
    on audit_logs (actor_user_id, created_at desc);

create index if not exists ix_audit_logs_action_created
    on audit_logs (action, created_at desc);

create index if not exists ix_audit_logs_resource
    on audit_logs (resource_type, resource_id);

create index if not exists ix_audit_logs_target_user
    on audit_logs (target_user_id, created_at desc);

create index if not exists ix_audit_logs_conversation
    on audit_logs (conversation_id, created_at desc);

create index if not exists ix_audit_logs_message
    on audit_logs (message_id, created_at desc);

create index if not exists ix_audit_logs_created_desc
    on audit_logs (created_at desc);

create index if not exists ix_audit_logs_request_id
    on audit_logs (request_id);

create index if not exists ix_audit_logs_metadata_gin
    on audit_logs using gin (metadata);

insert into permissions (id, code, name, description)
values
    ('10000000-0000-0000-0000-000000000017', 'AUDIT_EXPORT', 'Export audit logs', 'Export audit logs for compliance reviews')
on conflict do nothing;

insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code in ('AUDIT_EXPORT')
where r.code in ('ADMIN', 'SUPER_ADMIN')
on conflict do nothing;

comment on table audit_logs is 'Production audit trail. Recommend 90-180 day hot retention, longer archive for security-critical events, and monthly partitioning as volume grows.';
comment on column audit_logs.metadata is 'Sanitized JSON metadata only. Do not store raw passwords, tokens, Authorization headers, private URLs, or storage secrets.';
