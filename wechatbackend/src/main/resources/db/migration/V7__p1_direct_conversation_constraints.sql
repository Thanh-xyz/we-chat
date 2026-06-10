do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'uq_direct_conversations_pair'
    ) then
        alter table direct_conversations
            add constraint uq_direct_conversations_pair unique (user_low_id, user_high_id);
    end if;
end $$;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'uq_direct_conversations_conversation_id'
    ) then
        alter table direct_conversations
            add constraint uq_direct_conversations_conversation_id unique (conversation_id);
    end if;
end $$;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'ck_direct_conversations_users_order'
    ) then
        alter table direct_conversations
            add constraint ck_direct_conversations_users_order check (user_low_id < user_high_id);
    end if;
end $$;
