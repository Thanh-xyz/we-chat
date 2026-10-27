create extension if not exists unaccent;

create or replace function public.chatspace_search_normalize(value text)
returns text
language sql
immutable
parallel safe
strict
as $$
    select lower(public.unaccent('public.unaccent'::regdictionary, value))
$$;

create index if not exists idx_users_username_normalized_trgm
    on users using gin (public.chatspace_search_normalize(username) gin_trgm_ops)
    where deleted_at is null;

create index if not exists idx_users_display_name_normalized_trgm
    on users using gin (public.chatspace_search_normalize(display_name) gin_trgm_ops)
    where deleted_at is null;

create index if not exists idx_conversations_name_normalized_trgm
    on conversations using gin (public.chatspace_search_normalize(name) gin_trgm_ops)
    where deleted_at is null and type = 'GROUP';
