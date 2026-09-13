create index if not exists ix_refresh_tokens_revoked_at_cleanup
    on refresh_tokens (revoked_at)
    where revoked_at is not null;

create index if not exists ix_password_reset_tokens_used_at_cleanup
    on password_reset_tokens (used_at)
    where used_at is not null;

create index if not exists ix_email_verification_tokens_used_at_cleanup
    on email_verification_tokens (used_at)
    where used_at is not null;

create index if not exists idx_notifications_deleted_at_cleanup
    on notifications (deleted_at)
    where deleted_at is not null;

create index if not exists idx_notifications_read_at_cleanup
    on notifications (read_at)
    where is_read = true and deleted_at is null;

