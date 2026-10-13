# WeChat Clone Prompt Catalog

Các prompt riêng cho project WeChat được lưu trong thư mục này. Mỗi prompt phải:

- có version và changelog;
- dùng `../PROJECT-CONTEXT.md` khi cần context và xác minh lại bằng source hiện tại;
- dùng workflow trong `../WORKFLOW.md` khi prompt được user kích hoạt;
- phân biệt `PASS`, `FAIL` và `BLOCKED`;
- nêu rõ acceptance criteria và evidence;
- không lặp lại historical test result như runtime proof.

## Historical task briefs

These prompts record earlier scopes and reported results. They are not automatically active and do not grant permission for a new task. Their repository-relative paths and Maven S3 profile have been updated for reuse; runtime claims still need fresh evidence.

- [Prompt 21 — R16 Runtime Acceptance (rewritten)](../../../Prompt%2021%20-%20R16%20Runtime%20Acceptance%20-%20rewritten.md)
- [Prompt 22 — Supabase Storage S3 migration](prompt-22-supabase-storage-s3-migration.md)
- [Prompt 23 — MinIO to Supabase backfill](prompt-23-minio-to-supabase-backfill.md)

## Recommended naming

```text
prompt-[NN]-[r-item]-[short-task-name].md
```

Ví dụ:

```text
prompt-22-r11-auth-state-cache.md
prompt-23-e2e-two-user-chat.md
```
