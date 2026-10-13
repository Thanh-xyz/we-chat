---
name: wechat-storage-acceptance
description: Audit or verify WeChat attachment upload, S3-compatible storage, download authorization, cross-replica access, or legacy MinIO backfill boundaries.
---

# Attachment storage acceptance

Read `docs/architecture/storage.md`, `docs/api/attachments.md`, `docs/testing.md`, and the current `AttachmentController`, `AttachmentService`, `FileStorageService`, S3 adapter, repository, and V19 migration before designing a check. Confirm the active provider from current configuration without printing credentials or presigned URLs.

For a new-object acceptance run, verify initiation, direct PUT, completion to `READY`, message reference, authorized download, byte or SHA-256 equality, and non-member denial. A cross-replica claim also needs evidence that upload/finalize and download reached different backend replicas; if safe and in scope, repeat download after one replica stops. Confirm failure handling leaves invalid objects inaccessible. Record each criterion as PASS, FAIL, or BLOCKED from direct evidence.

`-Ps3-integration` uses a MinIO Testcontainer to test the adapter and cannot prove Supabase behavior. Existing MinIO objects require a separate inventory and backfill decision; consult `.agents/projects/wechat-clone/prompts/prompt-23-minio-to-supabase-backfill.md` only when legacy migration is the task. Never copy raw MinIO volume files or infer logical object count from its filesystem metadata. Current user authorization is required before modifying `.env`, copying live objects, or retiring source data.
