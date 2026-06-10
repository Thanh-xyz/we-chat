create table users (
    id uuid primary key,
    username varchar(50) not null,
    email varchar(255) not null,
    password_hash varchar(255) not null,
    display_name varchar(120) not null,
    avatar_url text,
    status varchar(30) not null default 'OFFLINE',
    role varchar(30) not null default 'USER',
    enabled boolean not null default true,
    account_status varchar(30) not null default 'ACTIVE',
    email_verified boolean not null default false,
    token_version integer not null default 0,
    last_login_at timestamp with time zone,
    failed_login_count integer not null default 0,
    locked_until timestamp with time zone,
    deleted_at timestamp with time zone,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now()
);

create unique index ux_users_username on users (username);
create unique index ux_users_email on users (email);

create table roles (
    id uuid primary key,
    code varchar(50) not null,
    name varchar(120) not null,
    description text,
    system_role boolean not null default false,
    deleted_at timestamp with time zone,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now()
);

create table permissions (
    id uuid primary key,
    code varchar(80) not null,
    name varchar(120) not null,
    description text,
    created_at timestamp with time zone not null default now()
);

create table user_roles (
    user_id uuid not null references users(id) on delete cascade,
    role_id uuid not null references roles(id) on delete restrict,
    assigned_by uuid references users(id) on delete set null,
    assigned_at timestamp with time zone not null default now(),
    primary key (user_id, role_id)
);

create table role_permissions (
    role_id uuid not null references roles(id) on delete cascade,
    permission_id uuid not null references permissions(id) on delete cascade,
    primary key (role_id, permission_id)
);

create table audit_logs (
    id uuid primary key,
    actor_user_id uuid references users(id) on delete set null,
    action varchar(120) not null,
    target_type varchar(80) not null,
    target_id varchar(120),
    before_value text,
    after_value text,
    ip_address varchar(80),
    user_agent text,
    created_at timestamp with time zone not null default now()
);

create table refresh_tokens (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    token_hash varchar(64) not null,
    expires_at timestamp with time zone not null,
    revoked_at timestamp with time zone,
    created_at timestamp with time zone not null default now(),
    replaced_by_token varchar(64),
    device_info text,
    ip_address varchar(80)
);

create unique index ux_refresh_tokens_token_hash on refresh_tokens (token_hash);

create table password_reset_tokens (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    token_hash varchar(64) not null,
    expires_at timestamp with time zone not null,
    used_at timestamp with time zone,
    created_at timestamp with time zone not null default now()
);

create unique index ux_password_reset_tokens_token_hash on password_reset_tokens (token_hash);

create table email_verification_tokens (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    token_hash varchar(64) not null,
    expires_at timestamp with time zone not null,
    used_at timestamp with time zone,
    created_at timestamp with time zone not null default now()
);

create unique index ux_email_verification_tokens_token_hash on email_verification_tokens (token_hash);

insert into roles (id, code, name, description, system_role)
values ('00000000-0000-0000-0000-000000000001', 'USER', 'User', 'Regular chat user', true);

insert into permissions (id, code, name, description)
values ('10000000-0000-0000-0000-000000000001', 'USER_WRITE', 'Write users', 'Update user profile data');

insert into role_permissions (role_id, permission_id)
values ('00000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001');
