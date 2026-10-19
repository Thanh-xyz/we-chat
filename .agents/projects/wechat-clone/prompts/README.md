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

- [Prompt 21 — R16 Runtime Acceptance (rewritten)](Prompt%2021%20-%20R16%20Runtime%20Acceptance%20-%20rewritten.md)
- [Prompt 22 — Supabase Storage S3 migration](prompt-22-supabase-storage-s3-migration.md)
- [Prompt 23 — MinIO to Supabase backfill](prompt-23-minio-to-supabase-backfill.md)

## Completed and available R16 briefs

Prompt 24/R11, Prompt 25/Supabase storage acceptance, Prompt 26/authenticated frontend and realtime acceptance, and Prompt 27/realtime edge acceptance have been explicitly executed in the current project workflow. Their results remain historical evidence and must be reconfirmed when a later task depends on them.

- [Prompt 24 — R11 Redis auth-state cache and cross-replica revocation](prompt-24-r11-auth-state-cache.md)
- [Prompt 25 — R16 Supabase Storage cross-replica runtime acceptance](prompt-25-r16-supabase-cross-replica-runtime.md)
- [Prompt 26 — R16 Authenticated Frontend and Realtime Runtime Acceptance](prompt-26-r16-autheticated-frontend-and-realtime.md)
- [Prompt 27 — R16 Realtime Reconnect, Resynchronization, and Authorization Edge Acceptance](prompt-27-r16-realtime-reconnect-resync.md)

## Next recommended task

Prompts 28 and 29 are the next staged R16 briefs. They are not active until explicitly selected by the user or project lead.

- [Prompt 28 — R16 Realtime Redis Failure, Backpressure, and Observability Acceptance](prompt-28-r16-realtime-failure-observability.md)
- [Prompt 29 — R16 Production-Readiness Evidence Closeout](prompt-29-r16-production-readiness-closeout.md)

## Recommended naming

```text
prompt-[NN]-[r-item]-[short-task-name].md
```

Ví dụ:

```text
prompt-22-r11-auth-state-cache.md
prompt-23-e2e-two-user-chat.md
```
