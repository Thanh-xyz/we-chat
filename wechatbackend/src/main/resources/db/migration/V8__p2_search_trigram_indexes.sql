do $$
begin
    begin
        create extension if not exists pg_trgm;
    exception
        when insufficient_privilege then
            raise notice 'Skipping pg_trgm extension creation: insufficient privilege';
        when undefined_file then
            raise notice 'Skipping pg_trgm extension creation: extension is not available';
    end;

    if exists (select 1 from pg_extension where extname = 'pg_trgm') then
        execute 'create index if not exists idx_users_username_trgm
            on users using gin (lower(username) gin_trgm_ops)
            where deleted_at is null';

        execute 'create index if not exists idx_users_email_trgm
            on users using gin (lower(email) gin_trgm_ops)
            where deleted_at is null';

        execute 'create index if not exists idx_users_display_name_trgm
            on users using gin (lower(display_name) gin_trgm_ops)
            where deleted_at is null';

        execute 'create index if not exists idx_conversations_name_trgm
            on conversations using gin (lower(name) gin_trgm_ops)
            where deleted_at is null and type = ''GROUP''';

        execute 'create index if not exists idx_messages_content_trgm
            on messages using gin (lower(content) gin_trgm_ops)
            where deleted_at is null and is_recalled = false';
    else
        raise notice 'Skipping trigram indexes because pg_trgm is not installed';
    end if;
end $$;
