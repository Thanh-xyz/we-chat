alter table refresh_tokens
    add column if not exists replaced_by_token varchar(64),
    add column if not exists device_info text,
    add column if not exists ip_address varchar(80);

update refresh_tokens
set replaced_by_token = replaced_by_token_hash
where replaced_by_token is null
  and replaced_by_token_hash is not null;

create index if not exists ix_refresh_tokens_user_active
    on refresh_tokens (user_id, revoked_at, expires_at);

create index if not exists ix_refresh_tokens_created_at
    on refresh_tokens (created_at);

create table if not exists password_reset_tokens (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    token_hash varchar(64) not null,
    expires_at timestamptz not null,
    used_at timestamptz,
    created_at timestamptz not null default now()
);

create unique index if not exists ux_password_reset_tokens_token_hash
    on password_reset_tokens (token_hash);

create index if not exists ix_password_reset_tokens_user_id
    on password_reset_tokens (user_id);

create index if not exists ix_password_reset_tokens_active
    on password_reset_tokens (user_id, used_at, expires_at);

create index if not exists ix_password_reset_tokens_expires_at
    on password_reset_tokens (expires_at);

create table if not exists email_verification_tokens (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    token_hash varchar(64) not null,
    expires_at timestamptz not null,
    used_at timestamptz,
    created_at timestamptz not null default now()
);

create unique index if not exists ux_email_verification_tokens_token_hash
    on email_verification_tokens (token_hash);

create index if not exists ix_email_verification_tokens_user_id
    on email_verification_tokens (user_id);

create index if not exists ix_email_verification_tokens_active
    on email_verification_tokens (user_id, used_at, expires_at);

create index if not exists ix_email_verification_tokens_expires_at
    on email_verification_tokens (expires_at);

create index if not exists ix_audit_logs_action_created_at
    on audit_logs (action, created_at);
