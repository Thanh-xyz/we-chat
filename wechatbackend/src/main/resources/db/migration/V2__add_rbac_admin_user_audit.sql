alter table users
    add column if not exists account_status varchar(30) not null default 'ACTIVE',
    add column if not exists email_verified boolean not null default false,
    add column if not exists token_version integer not null default 0,
    add column if not exists last_login_at timestamptz,
    add column if not exists failed_login_count integer not null default 0,
    add column if not exists locked_until timestamptz,
    add column if not exists deleted_at timestamptz;

update users
set account_status = case
    when enabled = false then 'BLOCKED'
    else account_status
end;

create index if not exists ix_users_account_status on users (account_status);
create index if not exists ix_users_deleted_at on users (deleted_at);

create table if not exists roles (
    id uuid primary key,
    code varchar(50) not null,
    name varchar(120) not null,
    description text,
    system_role boolean not null default false,
    deleted_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index if not exists ux_roles_code_lower on roles (lower(code)) where deleted_at is null;

create table if not exists permissions (
    id uuid primary key,
    code varchar(80) not null,
    name varchar(120) not null,
    description text,
    created_at timestamptz not null default now()
);

create unique index if not exists ux_permissions_code_lower on permissions (lower(code));

create table if not exists user_roles (
    user_id uuid not null references users(id) on delete cascade,
    role_id uuid not null references roles(id) on delete restrict,
    assigned_by uuid references users(id) on delete set null,
    assigned_at timestamptz not null default now(),
    primary key (user_id, role_id)
);

create index if not exists ix_user_roles_role_id on user_roles (role_id);

create table if not exists role_permissions (
    role_id uuid not null references roles(id) on delete cascade,
    permission_id uuid not null references permissions(id) on delete cascade,
    primary key (role_id, permission_id)
);

create index if not exists ix_role_permissions_permission_id on role_permissions (permission_id);

create table if not exists audit_logs (
    id uuid primary key,
    actor_user_id uuid references users(id) on delete set null,
    action varchar(120) not null,
    target_type varchar(80) not null,
    target_id varchar(120),
    before_value text,
    after_value text,
    ip_address varchar(80),
    user_agent text,
    created_at timestamptz not null default now()
);

create index if not exists ix_audit_logs_actor_user_id on audit_logs (actor_user_id);
create index if not exists ix_audit_logs_target on audit_logs (target_type, target_id);
create index if not exists ix_audit_logs_created_at on audit_logs (created_at);

insert into roles (id, code, name, description, system_role)
values
    ('00000000-0000-0000-0000-000000000001', 'USER', 'User', 'Regular chat user', true),
    ('00000000-0000-0000-0000-000000000002', 'MODERATOR', 'Moderator', 'Content moderation role', true),
    ('00000000-0000-0000-0000-000000000003', 'ADMIN', 'Admin', 'User and operational administration role', true),
    ('00000000-0000-0000-0000-000000000004', 'SUPER_ADMIN', 'Super Admin', 'Full system administration role', true)
on conflict (id) do nothing;

insert into permissions (id, code, name, description)
values
    ('10000000-0000-0000-0000-000000000001', 'USER_READ', 'Read users', 'View user list and user details'),
    ('10000000-0000-0000-0000-000000000002', 'USER_WRITE', 'Write users', 'Update user profile data'),
    ('10000000-0000-0000-0000-000000000003', 'USER_STATUS_WRITE', 'Change user status', 'Block, unblock, mark pending or soft delete users'),
    ('10000000-0000-0000-0000-000000000004', 'ROLE_READ', 'Read roles', 'View roles and permissions'),
    ('10000000-0000-0000-0000-000000000005', 'ROLE_WRITE', 'Write roles', 'Create, update and delete roles'),
    ('10000000-0000-0000-0000-000000000006', 'ROLE_ASSIGN', 'Assign roles', 'Assign roles to users'),
    ('10000000-0000-0000-0000-000000000007', 'MESSAGE_MODERATE', 'Moderate messages', 'Moderate reported messages and content'),
    ('10000000-0000-0000-0000-000000000008', 'AUDIT_READ', 'Read audit logs', 'View audit logs')
on conflict (id) do nothing;

insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code in ('USER_WRITE')
where r.code = 'USER'
on conflict do nothing;

insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code in ('USER_WRITE', 'MESSAGE_MODERATE')
where r.code = 'MODERATOR'
on conflict do nothing;

insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code in ('USER_READ', 'USER_WRITE', 'USER_STATUS_WRITE', 'ROLE_READ', 'ROLE_ASSIGN', 'AUDIT_READ')
where r.code = 'ADMIN'
on conflict do nothing;

insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code in ('USER_READ', 'USER_WRITE', 'USER_STATUS_WRITE', 'ROLE_READ', 'ROLE_WRITE', 'ROLE_ASSIGN', 'MESSAGE_MODERATE', 'AUDIT_READ')
where r.code = 'SUPER_ADMIN'
on conflict do nothing;

insert into user_roles (user_id, role_id, assigned_at)
select u.id, r.id, now()
from users u
join roles r on r.code = coalesce(nullif(u.role, ''), 'USER')
on conflict do nothing;
