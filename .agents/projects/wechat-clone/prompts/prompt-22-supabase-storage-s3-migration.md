# Prompt 22 — Migrate MinIO to Supabase Storage via S3-Compatible API

Version: `v1.0`
Date: `2026-10-12`
Project: `WeChat Clone / Real-Time Web Chat System`
Primary agent: `Backend Architect`
Supporting agents: `DevOps Automator`, `SRE`, `Security Architect`
Reviewer: `Code Reviewer`

## Objective

Chuyển attachment object storage từ **MinIO chạy local qua Docker** sang **Supabase Storage dùng S3-compatible API**, đồng thời giữ nguyên:

- storage abstraction và API contract hiện tại nếu không có bug;
- private access và authorization;
- object key, metadata, checksum và byte integrity;
- upload/download/delete/presigned URL behavior;
- khả năng chạy qua nhiều backend replicas;
- dữ liệu MinIO hiện tại cho đến khi có migration decision và verification rõ ràng.

User đã xác nhận các Supabase S3 credentials cần thiết đang tồn tại trong `.env` local. Được phép đọc và cập nhật `.env` local trong phạm vi task này, nhưng **không được in secret ra terminal, diff, log, screenshot hoặc final report**.

## Critical Supabase facts

Không sao chép mù quáng endpoint trong yêu cầu cũ. Hãy lấy endpoint và region từ Supabase Dashboard. Tài liệu chính thức hiện minh họa S3 endpoint dạng:

```text
https://<project-ref>.storage.supabase.co/storage/v1/s3
```

Không tự dùng dạng `/v1/s3` nếu Dashboard/documentation của project không xác nhận dạng đó.

Supabase S3 access key/secret là credential server-side, có quyền rộng và bypass RLS theo tài liệu S3 authentication. Không đưa chúng vào frontend, browser bundle, test fixture commit hoặc client-side signing code.

Tham khảo:

- [Supabase S3 Authentication](https://supabase.com/docs/guides/storage/s3/authentication)
- [Supabase S3 Compatibility](https://supabase.com/docs/guides/storage/s3/compatibility)
- [Supabase Download Objects](https://supabase.com/docs/guides/storage/management/download-objects)

## Definition of Done

Chỉ kết luận `PASS` khi:

1. Backend không còn runtime dependency bắt buộc vào MinIO local.
2. Supabase S3 endpoint, region, access key và secret key được wiring đúng mà không lộ secret.
3. `forcePathStyle`/addressing style được xác minh theo SDK và request thực tế.
4. Bucket `attachments` tồn tại, private và được xử lý idempotent.
5. Upload/download/delete và presigned URL flow pass theo API contract.
6. Upload qua backend replica A rồi download qua replica B trả đúng bytes/checksum.
7. User B vẫn bị từ chối khi truy cập attachment của User A.
8. Existing MinIO objects có migration decision rõ ràng; không xóa volume trước verification.
9. Backend, frontend và integration regression suites liên quan pass.
10. Final report liệt kê file thay đổi, diff summary, commands thực tế, Dashboard steps và limitations.

## Scope

### In scope

- Docker Compose service/dependency/volume references của MinIO.
- `.env` local và `.env.example` storage configuration.
- Storage config, S3 client, bucket bootstrap, upload/download/delete và presigned URL logic.
- Attachment lifecycle, authorization và file validation regression.
- Supabase Dashboard setup instructions.
- Tests và docs trực tiếp liên quan migration.

### Out of scope

- Thay đổi JWT, WebSocket authorization, Redis Pub/Sub, rate limiter hoặc PostgreSQL schema không cần thiết.
- Public bucket hoặc anonymous write/read.
- Đưa S3 credentials vào frontend.
- Đổi upload protocol chỉ để làm test pass.
- Xóa MinIO data trước khi xác định existing-object migration.
- Refactor storage architecture nếu adapter hiện tại đã đủ.

## Safety and result states

- Được đọc `.env` local vì user đã xác nhận credentials có sẵn.
- Có thể loại bỏ key MinIO và cập nhật `STORAGE_*` trong `.env` local, nhưng không echo giá trị thật.
- `.env.example` chỉ dùng placeholders.
- Không dùng `set -x`; không log request headers, Authorization, AWS signature, presigned URL hoặc secret query parameters.
- Không public bucket, disable auth, chạy `docker compose down -v`, hoặc xóa database/MinIO volume/object cũ trước verification.
- Nếu Supabase project/network/credentials không dùng được, kết quả là `BLOCKED`.

Mỗi acceptance item là một trong:

- `PASS`: có bằng chứng trực tiếp từ source, test output hoặc runtime verification.
- `FAIL`: test chạy được nhưng behavior sai.
- `BLOCKED`: thiếu runtime dependency, quyền, credential, network hoặc dữ liệu để kiểm tra an toàn.

Skipped test, compile thành công, config parse được hoặc bucket tồn tại một mình không đủ để kết luận `PASS`.

## Gate 0 — Load context and inspect repository

Đọc trước:

- `.agents/projects/wechat-clone/PROJECT-CONTEXT.md`
- `.agents/projects/wechat-clone/WORKFLOW.md`
- application repository thật, không chỉ `agency-agents`
- Docker Compose và deployment docs
- backend storage abstraction/configuration
- S3/MinIO client implementation
- attachment controller/service/repository
- upload/finalize/download/delete flow
- file validation pipeline
- frontend attachment flow
- existing MinIO/S3 integration tests
- Flyway migrations liên quan attachment nếu có

Nếu application source không có trong workspace hiện tại, dừng ở mức audit/prompt và báo `BLOCKED`; không tuyên bố đã sửa code.

Xác định chính xác:

1. SDK/language đang dùng.
2. Endpoint/DTO upload, finalize, download và delete.
3. Multipart proxy hay presigned upload.
4. Attachment metadata và object-key strategy.
5. Authorization ownership/access checks.
6. Bucket bootstrap behavior.
7. Replica model và cách chứng minh request tới A/B.
8. Cơ chế frontend truy cập presigned URL.

Không tự giả định endpoint, DTO, status, config key hoặc SDK.

## Gate 1 — Existing MinIO data and migration boundary

Trước khi xóa service/volume, kiểm tra:

- MinIO bucket hiện có object thật không.
- PostgreSQL có attachment record tham chiếu MinIO không.
- Object key có cần giữ nguyên không.
- Có migrate existing objects sang Supabase không.
- Có cần dual-read, dual-write, backfill, cutover hoặc rollback không.

Nếu còn object thật mà migration strategy chưa được xác định:

- không xóa MinIO volume;
- không âm thầm bỏ khả năng đọc object cũ;
- ghi `BLOCKED` cho destructive cutover;
- đề xuất migration plan riêng hoặc yêu cầu quyết định.

Nếu không có object cũ hoặc user xác nhận chỉ cần object mới, ghi rõ trong report.

## Gate 2 — Baseline and inventory

Dùng path thực tế của repository:

```bash
git status --short
rg -n -i "minio|MINIO_|storage|s3|presign|presigned|bucket|object" .
docker compose --env-file .env -f docker/docker-compose.yml config -q
docker compose --env-file .env -f docker/docker-compose.yml ps
```

Ghi baseline riêng cho application tests, MinIO integration, PostgreSQL integration nếu liên quan, frontend tests/build và stack health.

## Gate 3 — Docker and infrastructure

Sau khi không có blocker dữ liệu:

1. Remove hoặc disable service `minio`.
2. Remove MinIO-only named volumes/bind mounts.
3. Remove chỉ dependency `minio` khỏi backend `depends_on`; giữ PostgreSQL/Redis dependencies.
4. Remove MinIO-only healthcheck, ports, networks và environment references.
5. Tìm và xử lý script/docs/test còn giả định MinIO là runtime bắt buộc.
6. Không publish thêm port Supabase hoặc backend.

Expected:

```text
docker compose config -q → PASS
backend starts without local MinIO → PASS
no stale mandatory minio dependency → PASS
```

## Gate 4 — Environment configuration

### `.env.example`

Dùng placeholders, không dùng secret thật:

```env
STORAGE_TYPE=s3
STORAGE_ENDPOINT=https://<project-ref>.storage.supabase.co/storage/v1/s3
STORAGE_PRESIGNED_ENDPOINT=https://<project-ref>.storage.supabase.co/storage/v1/s3
STORAGE_ACCESS_KEY=<supabase_s3_access_key>
STORAGE_SECRET_KEY=<supabase_s3_secret_key>
STORAGE_BUCKET=attachments
STORAGE_REGION=<region_from_supabase_dashboard>
```

Nếu app dùng tên config khác, giữ naming convention hiện tại và document mapping; không tạo duplicate keys vô lý.

### `.env` local

- Đọc để validate endpoint/bucket/region/credential presence.
- Remove `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD`, `MINIO_API_PORT` và MinIO-only keys nếu không còn reference.
- Cập nhật `STORAGE_*` theo giá trị Supabase thực tế.
- Không đưa giá trị thật vào report hoặc diff gửi cho user.
- Nếu secret thiếu hoặc endpoint chưa chắc chắn, đánh dấu `BLOCKED`; không invent.

## Gate 5 — S3 client and storage service

Rà soát implementation thực tế, không giả định AWS SDK v3/boto3.

### Endpoint, addressing and signing

- S3 client phải lấy endpoint từ config.
- Xác minh endpoint có `/storage/v1/s3` theo Dashboard/docs/runtime.
- Dùng direct storage hostname nếu Supabase cung cấp.
- Kiểm tra `forcePathStyle: true` hoặc equivalent của SDK.
- Xác nhận request thực tế là path-style hợp lệ, không tạo bucket subdomain DNS sai.
- Không thay host của presigned URL sau khi ký nếu việc đó làm đổi canonical request/signature.
- Region dùng để ký phải khớp region Supabase cung cấp; không mặc định `ap-southeast-1`.

### Credentials

- S3 access key/secret chỉ ở backend/server-side.
- Không dùng anon/publishable/service-role key thay cho generated S3 credentials nếu contract yêu cầu S3 credentials.
- Không bundle access key/secret vào frontend.

### Bucket bootstrap

Nếu app có `makeBucket`, `createBucket`, `HeadBucket` hoặc startup initialization:

- xác định bucket được tạo ở Dashboard hay bởi app;
- startup không crash vì `AlreadyExists`;
- không nuốt mọi lỗi thành “bucket đã tồn tại”; phân biệt AlreadyExists, AccessDenied, endpoint failure và auth failure;
- có thể disable auto-create và coi bucket là deployment prerequisite;
- behavior phải idempotent và có test.

### Presigned URL

- Signer dùng đúng endpoint, region, method, bucket, key, content type và TTL.
- URL cuối cùng phải truy cập được từ browser/client thực tế.
- Nếu có `STORAGE_PRESIGNED_ENDPOINT` riêng, chứng minh signing và public URL mapping; nếu không cần tách, tránh cấu hình dư thừa.
- Không string-replace hostname sau signing nếu chưa chứng minh signature vẫn hợp lệ.

## Gate 6 — Supabase Dashboard prerequisite

Final report phải hướng dẫn ngắn gọn, không chứa secret:

1. Mở Supabase project.
2. Vào Storage và tạo bucket `attachments`.
3. Đặt bucket là **private** nếu app dùng backend/presigned URL.
4. Vào Storage configuration/S3 section.
5. Enable S3 protocol nếu Dashboard yêu cầu.
6. Generate S3 Access Key và Secret Access Key; lưu secret ngay nếu chỉ hiển thị một lần.
7. Copy chính xác endpoint và region Dashboard cung cấp.
8. Đặt credentials ở server-side `.env`, không đưa vào frontend.
9. Kiểm tra bucket policy/RLS/access model theo flow ứng dụng.

Không yêu cầu user public bucket hoặc gửi key qua chat.

## Gate 7 — Runtime acceptance

Tạo fixture riêng và tính SHA-256 local. Không log nội dung, token, presigned URL hoặc private key.

### Positive lifecycle

```text
User A
  → initiate upload qua backend replica A
  → upload bytes qua current API/S3 flow
  → server-side validation
  → finalize nếu có
  → attachment READY
  → create/use message reference
  → download qua backend replica B
```

Kiểm tra object tồn tại trong Supabase bucket, DB metadata đúng, length/checksum khớp, downloaded bytes giống source, object key không phụ thuộc local filesystem, và A/B thực sự khác nhau.

### Instance independence

- Stop riêng replica A nếu xác định được container và an toàn.
- Giữ B healthy.
- Download attachment qua B.
- Restart A khi cần.

Không thể stop riêng A an toàn thì đánh dấu test `BLOCKED`.

### Authorization

User B phải bị từ chối khi request download URL, download object, finalize/delete attachment của A hoặc gắn attachment của A vào message nếu API hỗ trợ. Giữ nguyên HTTP status/error contract.

### Negative and failure tests

Kiểm tra invalid MIME/extension/magic bytes, malformed image, empty file nếu policy cấm, file vượt giới hạn, Supabase unavailable ở initiate/upload/finalize/download/delete, và sai bucket/credential/region trong test-safe environment.

Invalid upload không được thành `READY`. Storage outage không được trả presigned URL giả hoặc retry vô hạn.

## Gate 8 — Regression suites

Chạy commands thực tế phù hợp với app:

```bash
mvn test
mvn verify -Ppostgres-integration
mvn verify -Ps3-integration
npm test
npm run lint
npm run build
git diff --check
```

Không báo MinIO integration là pass nếu suite bị xóa mà không có replacement S3 coverage. Nếu profile cần rename, ghi rõ.

Nếu Docker/Testcontainers hoặc Supabase network không khả dụng, báo `BLOCKED` cùng command/error thực tế.

## Files and diff report

Báo cáo:

1. File thực sự đã sửa.
2. Vì sao file đó cần sửa.
3. Diff summary theo nhóm: Compose, env, backend, frontend, tests, docs.
4. MinIO references còn lại và lý do giữ lại.
5. Diff/config đã redact; không đưa secret thật vào report.
6. Nếu chỉ audit được mà chưa sửa file, nói rõ `BLOCKED`.

Không đổi endpoint API, DB schema hoặc object-key strategy nếu không có root cause và acceptance evidence.

## Final report format

```markdown
# Verdict
PASS / FAIL / BLOCKED

## Migration decision
- Existing MinIO objects: none / migrated / deferred / BLOCKED
- Cutover strategy:
- Rollback strategy:

## Runtime
- Supabase endpoint shape verified: PASS/FAIL/BLOCKED
- Region/signing verified: PASS/FAIL/BLOCKED
- Backend replicas:
- Bucket privacy:

## Acceptance matrix
| ID | Criterion | Status | Evidence |
|---|---|---|---|
| A1 | No mandatory MinIO runtime dependency | | |
| A2 | Supabase S3 client connects | | |
| A3 | Bucket bootstrap is idempotent | | |
| A4 | Upload/download checksum matches | | |
| A5 | Upload A → download B | | |
| A6 | B still downloads after A stops | | |
| A7 | User B is denied | | |
| A8 | Invalid files are rejected | | |
| A9 | Storage failures are safe | | |
| A10 | Regression suites pass | | |

## Files changed
- `path` — reason

## Sanitized diff summary
- [Compose/env/backend/frontend/tests/docs]

## Supabase Dashboard steps
- [short numbered instructions; no secrets]

## Tests actually run
- `[command]` — result

## Limitations/blockers
- [none or concrete items]

## Next action
- [smallest safe action]
```

## Prompt QA cases

Before using this prompt, verify it handles:

1. **Happy path:** Supabase credentials and bucket exist; A uploads and B downloads with matching checksum.
2. **Configuration edge case:** Dashboard endpoint includes `/storage/v1/s3`, region differs from `ap-southeast-1`, or SDK addressing differs from MinIO.
3. **Failure/blocker:** `.env` lacks usable credentials, Supabase is unreachable, or existing MinIO objects have not been migrated.

## Changelog

### v1.0 — 2026-10-12

- Reframed the request as a guarded migration runbook.
- Added existing-object migration boundary, rollback and `PASS/FAIL/BLOCKED` policy.
- Added Supabase endpoint/region/signing verification and server-only credential rules.
- Added cross-replica, authorization, presigned URL and failure acceptance criteria.

