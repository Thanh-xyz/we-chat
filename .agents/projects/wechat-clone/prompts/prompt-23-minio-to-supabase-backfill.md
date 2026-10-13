# Prompt 23 — Backfill Existing MinIO Objects to Supabase Storage

Version: v1.0
Date: 2026-10-12
Project: WeChat Clone / Real-Time Web Chat System
Primary agent: SRE
Supporting agents: Backend Architect, Database Reliability Engineer, Security Architect
Reviewer: Code Reviewer

## Context

Prompt 22 đã chuyển runtime storage mới sang Supabase S3-compatible API và đạt:

- Supabase endpoint/region/signing: PASS.
- AWS SDK v2 với forcePathStyle=true: PASS.
- Supabase bucket private và bucket bootstrap idempotent: PASS.
- Upload/download checksum cho object mới: PASS.
- Upload qua backend replica A rồi download qua replica B: PASS.
- User B authorization denial: PASS.
- Backend/frontend/PostgreSQL/S3 regression suites: PASS.
- Existing MinIO objects: deferred.
- Phát hiện dữ liệu thật trong minio_data; báo cáo hiện ghi khoảng 16 metadata files và khoảng 348 KiB.
- MinIO container/volume đang được giữ làm rollback source; không được xóa.

Một credential local đã từng xuất hiện trong output trung gian. Trước khi dùng credential cũ cho bất kỳ migration nào, phải rotate/revoke credential đó và cập nhật .env bằng credential mới. Không in hoặc lặp lại giá trị secret.

## Objective

Copy toàn bộ logical attachment objects còn tồn tại trong MinIO sang bucket Supabase attachments, xác minh integrity và đối chiếu với attachment metadata trong PostgreSQL, mà không xóa hoặc thay đổi source MinIO.

Migration phải:

- idempotent;
- resumable;
- tạo manifest checksum;
- giữ object key/metadata theo contract hiện tại nếu có thể;
- phát hiện missing object, orphan object, duplicate key và checksum mismatch;
- cho phép rollback target-side mà không ảnh hưởng source;
- không tuyên bố cutover hoàn tất trước khi mọi acceptance bắt buộc PASS.

## Important distinction: logical objects vs MinIO internals

Không coi số lượng file metadata trong minio_data là số lượng attachment.

Không copy trực tiếp thư mục raw MinIO bằng filesystem command. MinIO volume có internal metadata/system files. Phải enumerate logical objects qua một trong các cách an toàn:

- MinIO S3 API bằng AWS SDK/CLI;
- mc ls --recursive nếu mc đã có và được cấu hình an toàn;
- application storage adapter hiện tại;
- MinIO container đang chạy với bucket/object listing hợp lệ.

Loại trừ .minio.sys, internal metadata và object không thuộc bucket attachment. Nếu không thể enumerate logical objects qua API, kết quả là BLOCKED.

## Result states

- PASS: object được copy và verify trực tiếp bằng manifest/checksum.
- FAIL: migration chạy được nhưng object/metadata/integrity sai.
- BLOCKED: thiếu credential sau rotation, không truy cập được source/target, không xác định được mapping hoặc thao tác không an toàn.
- Skipped, dry-run hoặc “source có vẻ tồn tại” không phải PASS.

Overall verdict chỉ là PASS khi mọi acceptance bắt buộc pass. Có item BLOCKED thì overall là BLOCKED.

## Safety constraints

- Rotate/revoke credential đã lộ trước khi migration.
- Không in access key, secret, token, presigned URL hoặc full sensitive config.
- Không chạy docker compose down -v.
- Không xóa minio_data, source bucket hoặc source object.
- Không overwrite target object khi chưa kiểm tra identity/integrity.
- Không xóa target object tự động nếu chưa có manifest và rollback authorization.
- Không sửa DB attachment metadata hàng loạt nếu chưa có mapping evidence.
- Không public Supabase bucket.
- Không disable authentication/authorization.
- Không chạy migration production-wide nếu chưa có dry-run và sample verification.
- Giữ local manifest ở nơi không commit; redact object keys khi đưa vào report.

## Gate 0 — Inspect current repository and migration surface

Đọc:

- .agents/projects/wechat-clone/PROJECT-CONTEXT.md
- .agents/projects/wechat-clone/WORKFLOW.md
- .agents/projects/wechat-clone/prompts/prompt-22-supabase-storage-s3-migration.md
- current docker-compose.yml
- storage service/configuration hiện tại
- attachment entity/repository/service/controller
- Flyway migrations liên quan attachment
- storage/S3 integration tests
- deployment docs và rollback notes

Xác định chính xác:

1. Source MinIO bucket name và endpoint.
2. Target Supabase bucket name.
3. Object key strategy.
4. Attachment DB columns: ID, owner, bucket, object key, size, content type, checksum, status.
5. Cách attachment record liên kết với message.
6. Existing object status nào cần migrate.
7. Cách enumerate source object và target object.
8. Cách app tạo download URL sau migration.
9. Có migration tool/script hiện tại không.

Không tự giả định object key bằng attachment ID nếu source chưa chứng minh.

## Gate 1 — Credential rotation and access validation

Trước khi đọc/copy object:

1. Xác nhận credential từng xuất hiện trong output đã bị revoke/rotate.
2. Cập nhật local .env bằng credential mới nếu cần; không commit.
3. Kiểm tra source MinIO credential và target Supabase S3 credential đều hoạt động.
4. Xác nhận quyền cần thiết:
   - source: list/head/get object;
   - target: head/list/put/head object;
   - delete chỉ dùng khi rollback được ủy quyền.
5. Test một object nhỏ trong isolated prefix hoặc test fixture.
6. Không ghi credential vào manifest/log.

Nếu chưa thể xác nhận rotation hoặc access validation, đánh dấu BLOCKED và dừng trước data copy.

## Gate 2 — Read-only inventory and reconciliation

Tạo inventory read-only cho source và target.

### Source inventory

Enumerate logical objects qua S3 API. Với mỗi object ghi vào manifest local:

- source bucket;
- object key;
- size;
- content type/metadata nếu có;
- ETag nếu có, nhưng không coi ETag luôn là SHA-256 hoặc MD5;
- SHA-256 tính bằng cách stream object;
- source last-modified nếu cần;
- attachment ID mapping nếu xác định được.

### Database inventory

Đọc attachment metadata và phân loại:

- DB record có source object;
- DB record thiếu source object;
- source object không có DB record;
- status chưa hoàn tất;
- duplicate object key;
- bucket/object key sai hoặc null;
- object bị User/Message reference nhưng record không hợp lệ.

Không sửa DB trong bước inventory.

### Target inventory

List target bucket/prefix hiện có. Không assume target rỗng. Phân loại:

- object đã tồn tại và checksum/size khớp;
- object tồn tại nhưng mismatch;
- object target không có source/DB mapping;
- object do migration run trước tạo.

Nếu provider không cung cấp SHA-256 metadata trực tiếp, download/stream object để verify checksum; không suy luận từ ETag.

## Gate 3 — Migration plan and dry-run

Trước khi upload thật, tạo dry-run report:

- total logical source objects;
- total DB attachment records;
- total expected objects;
- missing/orphan/duplicate/conflict counts;
- total bytes;
- key mapping;
- target bucket/prefix;
- objects already verified;
- objects requiring copy;
- objects blocked.

Nếu phát hiện conflict, không overwrite. Dừng và báo FAIL hoặc BLOCKED tùy nguyên nhân.

Dry-run phải reconcile được:

source logical objects ↔ database attachment metadata ↔ target Supabase objects

Chỉ tiếp tục copy khi mapping và cutover strategy đã rõ.

## Gate 4 — Copy implementation

Dùng S3 API/SDK hoặc migration utility an toàn, không copy raw MinIO filesystem.

Yêu cầu:

- stream object để giới hạn memory;
- preserve object key nếu contract yêu cầu;
- preserve content type and required metadata;
- bounded concurrency;
- retry có backoff và giới hạn;
- idempotent khi chạy lại;
- không đánh dấu DB READY chỉ vì upload request đã gửi;
- lưu per-object result vào local manifest;
- có timeout;
- không retry vô hạn;
- source read-only trong toàn bộ migration.

Idempotency rule:

- target object missing → upload → verify → mark COPIED;
- target object exists + checksum/size match → do not overwrite → mark VERIFIED;
- target object exists + mismatch → do not overwrite → mark CONFLICT.

Không tự động xóa hoặc overwrite CONFLICT.

## Gate 5 — Per-object verification

Sau mỗi upload hoặc batch nhỏ, xác nhận:

- target object tồn tại;
- target size khớp source;
- target SHA-256 khớp source;
- content type/required metadata khớp;
- object key khớp mapping;
- target object đọc được bằng server-side S3 client;
- presigned download nếu contract dùng presigned URL trả đúng bytes;
- DB record vẫn trỏ đúng object.

Không đưa object content, secret hoặc presigned URL vào report.

## Gate 6 — Full reconciliation

Sau khi copy xong, chạy reconciliation độc lập.

Acceptance bắt buộc:

- mọi DB attachment cần migrate có target object;
- mọi target object cần migrate có source/DB mapping;
- không có checksum mismatch;
- không có size mismatch;
- không có duplicate key;
- không có object READY nhưng target thiếu;
- object mới và object cũ dùng cùng download/authorization contract;
- User B không thể truy cập object của User A;
- source MinIO vẫn nguyên vẹn và đọc được.

Nếu có missing/orphan/conflict, overall không được là PASS.

## Gate 7 — Cutover verification

Chỉ chạy sau khi Gate 6 pass và credential rotation hoàn tất.

1. Giữ MinIO source/volume nguyên vẹn.
2. Đảm bảo application config trỏ Supabase.
3. Chạy lại sample từ object cũ qua backend replica A.
4. Download sample qua replica B và so SHA-256.
5. Stop riêng A nếu an toàn.
6. Download lại sample qua B.
7. Chạy attachment authorization test với User B.
8. Kiểm tra app không fallback âm thầm về MinIO.
9. Ghi rõ MinIO đang là rollback source, không phải runtime dependency.

Nếu không thể stop riêng A, đánh dấu A6 BLOCKED.

## Gate 8 — Rollback and retirement policy

Không retire MinIO trong prompt này nếu chưa có:

- full manifest verified;
- DB/object reconciliation pass;
- cross-instance download pass;
- rollback decision được ghi rõ;
- retention window cho source backup;
- confirmation không còn record nào trỏ riêng tới MinIO;
- credential rotation hoàn tất.

Rollback phải target-safe:

- có thể chuyển config về known-good storage chỉ khi code/config hỗ trợ;
- không xóa source MinIO;
- không xóa target objects hàng loạt nếu chưa được ủy quyền;
- nếu code cũ đã bị remove, ghi rõ rollback cần git/config rollback tương ứng.

## Regression tests

Chạy suite phù hợp với repository:

mvn test
mvn verify -Ppostgres-integration
mvn -q -Ps3-integration verify
npm test
npm run lint
npm run build
git diff --check

Nếu có migration script/test mới, chạy:

- dry-run trên fixture;
- idempotent rerun;
- interruption/resume test;
- checksum mismatch test;
- missing source object test;
- target conflict test;
- authorization test;
- failure/retry bounded test.

Không cộng số test của nhiều lần chạy thành một tổng.

## Final report

# Verdict
PASS / FAIL / BLOCKED

## Security prerequisite

- Leaked credential rotated/revoked: PASS/FAIL/BLOCKED
- New credentials used: PASS/FAIL/BLOCKED

## Inventory

- Logical source objects:
- DB attachment records:
- Expected objects:
- Source bytes:
- Existing target objects:
- Missing/orphan/conflict counts:

## Migration

- Dry-run:
- Objects copied:
- Objects skipped as already verified:
- Objects failed:
- Objects blocked:
- Total bytes copied:
- Manifest path, local only and not committed:

## Integrity

- Size reconciliation:
- SHA-256 reconciliation:
- Metadata reconciliation:
- DB/object mapping:
- Source remains intact:

## Runtime cutover

- Existing object download:
- Upload A → download B:
- Download after A stopped:
- User B authorization denial:

## Files changed

- path — reason

## Tests actually run

- command — result

## Remaining risk

- Retention/rollback window:
- Orphan handling:
- Backup/replication:
- Any unresolved conflict:

## Next action

- smallest safe action

## Definition of Done

Migration chỉ đạt PASS khi:

- credential exposure đã được xử lý;
- logical object inventory hoàn chỉnh;
- dry-run reconciliation không còn unexplained conflict;
- mọi object cần migrate đã copy và SHA-256 verified;
- DB metadata mapping đúng;
- Supabase download qua backend replicas pass;
- User B authorization vẫn bị từ chối;
- source MinIO chưa bị xóa;
- rerun không tạo duplicate/corruption;
- final report có manifest/result thực tế.

## Changelog

### v1.0 — 2026-10-12

- Tạo runbook backfill từ MinIO sang Supabase.
- Thêm credential rotation prerequisite.
- Phân biệt logical S3 objects với MinIO internal metadata files.
- Thêm manifest checksum, idempotency, reconciliation, resume và rollback gates.
