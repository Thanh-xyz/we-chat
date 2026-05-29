create table if not exists users (
    id uuid primary key,
    username varchar(50) not null,
    email varchar(255) not null,
    password_hash varchar(255) not null,
    display_name varchar(120) not null,
    avatar_url text,
    status varchar(30) not null default 'OFFLINE',
    role varchar(30) not null default 'USER',
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index if not exists ux_users_username_lower on users (lower(username));
create unique index if not exists ux_users_email_lower on users (lower(email));

create table if not exists refresh_tokens (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    token_hash varchar(64) not null,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    created_at timestamptz not null default now(),
    replaced_by_token_hash varchar(64)
);

create unique index if not exists ux_refresh_tokens_token_hash on refresh_tokens (token_hash);
create index if not exists ix_refresh_tokens_user_id on refresh_tokens (user_id);
create index if not exists ix_refresh_tokens_expires_at on refresh_tokens (expires_at);
