create table if not exists friend_requests (
    id uuid primary key,
    requester_id uuid not null references users(id) on delete cascade,
    receiver_id uuid not null references users(id) on delete cascade,
    status varchar(30) not null,
    message varchar(255),
    responded_at timestamptz,
    expires_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_friend_requests_not_self check (requester_id <> receiver_id),
    constraint ck_friend_requests_status check (status in ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELLED', 'EXPIRED'))
);

create unique index if not exists ux_friend_requests_pending_pair
    on friend_requests (least(requester_id, receiver_id), greatest(requester_id, receiver_id))
    where status = 'PENDING';

create index if not exists ix_friend_requests_requester
    on friend_requests (requester_id, created_at desc);

create index if not exists ix_friend_requests_receiver
    on friend_requests (receiver_id, created_at desc);

create index if not exists ix_friend_requests_status
    on friend_requests (status);

create index if not exists ix_friend_requests_created_desc
    on friend_requests (created_at desc);

create table if not exists friendships (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    friend_id uuid not null references users(id) on delete cascade,
    status varchar(30) not null,
    created_at timestamptz not null,
    deleted_at timestamptz,
    constraint ck_friendships_not_self check (user_id <> friend_id),
    constraint ck_friendships_status check (status in ('ACTIVE', 'DELETED')),
    constraint ux_friendships_user_friend unique (user_id, friend_id)
);

create index if not exists ix_friendships_user_status
    on friendships (user_id, status);

create index if not exists ix_friendships_friend
    on friendships (friend_id);

create index if not exists ix_friendships_created_desc
    on friendships (created_at desc);

create table if not exists user_blocks (
    id uuid primary key,
    blocker_id uuid not null references users(id) on delete cascade,
    blocked_id uuid not null references users(id) on delete cascade,
    reason varchar(255),
    created_at timestamptz not null,
    constraint ck_user_blocks_not_self check (blocker_id <> blocked_id),
    constraint ux_user_blocks_blocker_blocked unique (blocker_id, blocked_id)
);

create index if not exists ix_user_blocks_blocker
    on user_blocks (blocker_id, created_at desc);

create index if not exists ix_user_blocks_blocked
    on user_blocks (blocked_id);

insert into permissions (id, code, name, description)
values
    ('10000000-0000-0000-0000-000000000018', 'FRIEND_READ', 'Read friends', 'View friend list, friend requests, blocks, and relationship summary'),
    ('10000000-0000-0000-0000-000000000019', 'FRIEND_REQUEST_SEND', 'Send friend requests', 'Send contact/friend requests to active users'),
    ('10000000-0000-0000-0000-000000000020', 'FRIEND_REQUEST_RESPOND', 'Respond to friend requests', 'Accept, decline, or cancel friend requests related to the current user'),
    ('10000000-0000-0000-0000-000000000021', 'FRIEND_DELETE', 'Remove friends', 'Remove an active friendship'),
    ('10000000-0000-0000-0000-000000000022', 'USER_BLOCK', 'Block users', 'Block or unblock users')
on conflict do nothing;

insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code in ('FRIEND_READ', 'FRIEND_REQUEST_SEND', 'FRIEND_REQUEST_RESPOND', 'FRIEND_DELETE', 'USER_BLOCK')
where r.code in ('USER', 'MODERATOR')
on conflict do nothing;

insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code in ('FRIEND_READ')
where r.code in ('ADMIN', 'SUPER_ADMIN')
on conflict do nothing;

comment on table friend_requests is 'Friend/contact request workflow. Pending requests expire after the service policy window.';
comment on table friendships is 'Two-way friendship rows are stored for fast mobile/web contact list queries.';
comment on table user_blocks is 'User-level blocks. Blocks cancel pending requests and disable direct messaging between the pair.';
