do $$
begin
    if not exists (select 1 from pg_extension where extname = 'pg_trgm') then
        raise exception 'PostgreSQL extension pg_trgm is required for search indexes';
    end if;
end $$;

create index if not exists idx_users_username_trgm
    on users using gin (lower(username) gin_trgm_ops)
    where deleted_at is null;

create index if not exists idx_users_email_trgm
    on users using gin (lower(email) gin_trgm_ops)
    where deleted_at is null;

create index if not exists idx_users_display_name_trgm
    on users using gin (lower(display_name) gin_trgm_ops)
    where deleted_at is null;

create index if not exists idx_conversations_name_trgm
    on conversations using gin (lower(name) gin_trgm_ops)
    where deleted_at is null and type = 'GROUP';

create index if not exists idx_messages_content_trgm
    on messages using gin (lower(content) gin_trgm_ops)
    where deleted_at is null and is_recalled = false;
