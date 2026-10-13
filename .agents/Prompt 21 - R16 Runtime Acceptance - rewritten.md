# Prompt 21 — R16 Runtime Acceptance: Cross-instance Attachment Storage & Security

Version: `v2.0`
Last updated: `2026-10-12`
Project context: `projects/wechat-clone/PROJECT-CONTEXT.md`

Bạn là agent chịu trách nhiệm kiểm chứng production-readiness cho **WeChat Clone / Real-Time Web Chat System**. Task này chỉ tập trung vào **R16: MinIO attachment storage runtime acceptance** và các sửa lỗi trực tiếp do acceptance test phát hiện.

## Mục tiêu bắt buộc

Chứng minh bằng bằng chứng runtime rằng:

1. User A có thể upload một attachment qua backend instance 1.
2. Attachment được lưu trong MinIO private bucket và metadata được lưu đúng trong PostgreSQL.
3. Backend instance 2 có thể finalize hoặc download attachment đó.
4. Nội dung tải xuống khớp byte-for-byte với file ban đầu.
5. Attachment vẫn hoạt động khi instance 1 dừng nhưng instance 2 còn healthy.
6. User B không thể đọc, finalize, xóa hoặc gắn attachment của User A trái phép.
7. Invalid upload không trở thành attachment `READY`.
8. Không làm yếu authentication, authorization, bucket privacy hoặc secret handling.

Không được kết luận R16 đạt chỉ dựa trên MinIO readiness, sự tồn tại của DB record hoặc một vài storage integration test.

## Trạng thái đã biết

- R5 — Redis Pub/Sub distributed realtime broker: đã hoàn thành.
- R6 — Redis-backed distributed rate limiter: đã hoàn thành.
- R16 — MinIO storage integration: đang được triển khai và kiểm thử.
- R18 — PostgreSQL 17 runtime verification: đã hoàn thành.
- R13 — PostgreSQL query-plan verification: đã hoàn thành.
- Frontend Phase 1 và Phase 2: đã hoàn thành.
- Docker Compose có gateway, frontend, nhiều backend replicas, PostgreSQL, Redis và MinIO.
- PostgreSQL: 17.11.
- Flyway: V1 → V19.
- Kết quả gần nhất: `mvn test` 220 passed; PostgreSQL integration 13/13 passed khi bật performance test.

## Quy tắc làm việc

### 1. Khảo sát trước, không đoán contract

Trước khi chạy acceptance, đọc và đối chiếu:

- `docker/docker-compose.yml`
- `docs/architecture/storage.md`
- `docs/deployment.md`
- backend storage abstraction và MinIO/S3 implementation
- attachment service/controller
- upload, finalize, download và delete flow
- file validation pipeline
- message attachment reference và authorization logic
- frontend attachment flow
- MinIO integration tests
- Flyway migrations liên quan đến attachment

Lập một bảng contract nội bộ, ghi chính xác:

- endpoint/method để khởi tạo upload
- multipart proxy hay presigned URL
- endpoint/method finalize/confirm nếu có
- endpoint/method download hoặc generate download URL
- endpoint/method delete nếu có
- cách attachment gắn với message
- trạng thái attachment và điều kiện chuyển sang `READY`
- bucket/object-key strategy
- internal MinIO endpoint và browser-facing endpoint
- giới hạn file, MIME/extension policy và checksum policy

Không tự đặt tên endpoint, DTO, trạng thái hoặc giới hạn chưa có trong source/config/API docs.

### 2. Phân loại kết quả

Mỗi kiểm tra phải kết thúc bằng đúng một trong ba trạng thái:

- `PASS`: có bằng chứng trực tiếp đáp ứng expected behavior.
- `FAIL`: hệ thống chạy được nhưng behavior sai hoặc regression được chứng minh.
- `BLOCKED`: không thể kiểm thử do thiếu credential, thiếu fixture, thiếu dependency hoặc môi trường không an toàn; không được đổi `BLOCKED` thành `PASS` bằng giả định.

Nếu gặp `FAIL`, tìm root cause, sửa tối thiểu nếu bug thuộc R16, thêm regression test và chạy lại test liên quan. Nếu gặp `BLOCKED`, ghi rõ blocker và các bước cần thiết để unblock.

### 3. An toàn dữ liệu và secret

- Không sửa `.env`.
- Không in hoặc commit JWT, refresh token, password, Redis/MinIO credential, presigned URL hoặc private object key nếu policy không cho phép.
- Redact secret trong command output, log, screenshot và final report.
- Không disable authentication/authorization.
- Không mở public-read/public-write bucket.
- Không đưa binary attachment qua Redis.
- Không chạy `docker compose down -v`.
- Không xóa database/MinIO volume hoặc dữ liệu người dùng hiện có.
- Không dừng toàn bộ stack chỉ để chạy acceptance.
- Không publish backend hoặc MinIO internal port ra Internet chỉ để debug.
- Nếu cần môi trường riêng, dùng project name, port và volume riêng.

Trước mọi thao tác có thể dừng hoặc restart container, xác định rõ container đích và cách rollback. Chỉ dừng riêng một backend khi backend còn lại có thể tiếp tục phục vụ request và thao tác không gây mất dữ liệu.

## Quy trình thực hiện

### Gate 0 — Baseline runtime

Chạy các kiểm tra phù hợp với stack hiện tại:

```bash
docker compose --env-file .env -f docker/docker-compose.yml ps
docker compose --env-file .env -f docker/docker-compose.yml config -q
```

Xác nhận gateway, frontend, các backend replicas, PostgreSQL, Redis và MinIO healthy. Không đưa secret vào output.

Nếu baseline không đạt, dừng acceptance ở các test phụ thuộc service đó và báo `BLOCKED`, trừ khi có cách sửa R16 tối thiểu, an toàn và có thể xác minh.

### Gate 1 — Xác định backend instance

Phải chứng minh request đi qua hai backend container/instance khác nhau. Có thể dùng:

- container identity/IP;
- test client tạm thời trong private Compose network;
- cơ chế routing/harness hiện có.

Không coi hai request gửi qua gateway là cross-instance nếu chưa xác nhận instance placement.

Ghi bằng chứng ở dạng đã redact:

```text
upload/initiate  → backend instance A
finalize/download → backend instance B
A != B            → PASS
```

Không thêm production endpoint, header debug hoặc logging nhạy cảm chỉ để nhận diện instance.

### Gate 2 — Test fixtures

Dùng test account/fixture riêng. Tạo User A và User B bằng flow đăng ký hoặc fixture hợp lệ nếu cần.

Tạo một file test hợp lệ riêng cho run này và tính checksum cục bộ. Không đưa nội dung file, token hoặc presigned URL vào report. Ghi lại tối thiểu:

- tên fixture đã redact nếu cần;
- kích thước file;
- checksum;
- MIME/extension;
- thời điểm test.

Nếu không có credential hoặc không tạo được fixture an toàn, kết quả là `BLOCKED`.

### Gate 3 — Upload lifecycle của User A

Thực hiện đúng contract đã phát hiện ở Gate 0–2:

```text
User A
  → request upload authorization
  → backend instance A
  → upload bytes qua multipart hoặc presigned URL
  → server-side validation
  → finalize/confirm nếu contract yêu cầu
  → attachment READY/available
  → create message referencing attachment nếu flow cần
```

Nếu dùng presigned URL, kiểm tra:

- URL dùng hostname mà client thực sự truy cập được;
- URL chỉ cho phép object/key và operation dự kiến;
- TTL phù hợp configuration hiện tại;
- backend không đánh dấu `READY` trước khi upload/validation hoàn tất;
- URL không xuất hiện trong log hoặc report.

Kết quả upload chỉ là `PASS` khi backend response, DB state và storage state đều phù hợp contract.

### Gate 4 — Xác minh PostgreSQL và MinIO

Sau upload, xác nhận độc lập:

- attachment metadata tồn tại đúng trong PostgreSQL;
- object thực sự tồn tại trong private bucket;
- content length khớp;
- content type/checksum khớp nếu contract có quy định;
- object key giữa DB và MinIO khớp theo implementation.

DB record một mình không đủ để chứng minh upload thành công. Không dùng root credential trong output.

### Gate 5 — Cross-instance download (acceptance chính)

Dùng User A, thực hiện finalize hoặc download/URL-generation qua backend instance B — tùy contract thực tế.

Expected:

```text
metadata lookup qua B       → PASS
download/URL generation qua B → PASS
downloaded bytes == source   → PASS
checksum == source           → PASS
không phụ thuộc local disk/cache của A → PASS
```

Nếu fail, phân loại root cause tối thiểu theo nhóm: routing, DB metadata, object key, MinIO endpoint, credentials, bucket policy, authorization, presigned URL hoặc frontend contract.

### Gate 6 — Instance independence

Sau khi Gate 5 pass:

1. Xác nhận attachment vẫn download được qua B.
2. Dừng riêng instance A nếu việc này an toàn và xác định được container đích.
3. Xác nhận B vẫn healthy.
4. Download lại attachment qua B và so checksum.
5. Khởi động lại A nếu cần và xác nhận stack phục hồi.

Expected:

```text
A stopped → B healthy → existing attachment downloads via B
```

Nếu không thể dừng riêng A một cách an toàn, kết quả của test này là `BLOCKED`, không phải `PASS`.

### Gate 7 — Authorization isolation

Dùng User B thử các thao tác sau với attachment của User A, qua backend B:

- lấy download URL;
- download trực tiếp qua endpoint nếu có;
- finalize upload của A;
- delete attachment của A;
- gắn attachment của A vào message nếu API hỗ trợ.

Mỗi thao tác phải bị từ chối theo HTTP status/error contract hiện có. Không tiết lộ object key hoặc presigned URL cho User B.

Lưu ý: presigned URL là bearer capability trong thời hạn hiệu lực. Không chia sẻ URL giữa User A và User B trong test, không log URL.

### Gate 8 — File validation

Kiểm thử tối thiểu:

| Fixture | Expected |
|---|---|
| file hợp lệ | accepted, có thể hoàn tất/download |
| extension hợp lệ nhưng bytes sai loại | rejected |
| malformed image | rejected |
| MIME không được phép | rejected |
| extension không được phép | rejected |
| file rỗng nếu policy cấm | rejected |
| vượt size limit hiện tại | rejected |

Lấy policy và giới hạn từ configuration/source hiện tại. Không tạo policy mới để làm test pass.

Với mọi invalid upload, xác minh:

- không có attachment `READY` giả;
- không download được như attachment hoàn tất;
- quarantine/orphan object được xử lý theo cleanup policy hiện có;
- magic-byte/ImageIO/checksum validation không bị giảm hoặc bypass.

### Gate 9 — MinIO failure handling

Mô phỏng lỗi an toàn trong test environment, không phá volume hiện tại:

| Tình huống | Expected |
|---|---|
| MinIO unavailable khi initiate | lỗi an toàn, không tạo trạng thái READY |
| upload thất bại | không READY |
| finalize thất bại | trạng thái lỗi/cleanup phù hợp |
| MinIO unavailable khi download URL generation | không trả URL không hợp lệ |
| delete object thất bại | lỗi được ghi nhận; DB không giả vờ đồng bộ |

Ứng dụng không được crash dây chuyền hoặc retry vô hạn.

### Gate 10 — Bucket, URL và frontend security

Xác minh:

- bucket không public-read/public-write;
- anonymous user không list bucket;
- anonymous user không đọc object bằng URL không ký;
- TTL presigned URL theo config;
- credentials backend không nằm trong frontend build;
- MinIO internal hostname không bị bundle vào browser-facing production flow;
- browser-facing URL truy cập được từ client thực tế;
- URL/signature/token không xuất hiện trong log.

Không mở MinIO console hoặc anonymous access để làm test dễ hơn.

### Gate 11 — Regression suites

Chạy các suite phù hợp sau khi kiểm thử và chạy lại sau mọi thay đổi code:

```bash
npm test
npm run lint
npm run build

mvn test
mvn verify -Ppostgres-integration
mvn -q -Pminio-integration verify
git diff --check
```

Nếu đủ tài nguyên:

```bash
RUN_DB_PERF_TESTS=true mvn verify -Ppostgres-integration
```

Tách kết quả theo từng lần chạy; không cộng số test của nhiều lần chạy thành một tổng gây hiểu nhầm.

## Quy tắc sửa code

Chỉ sửa code khi acceptance phát hiện bug thuộc R16.

Với mỗi bug:

1. Ghi symptom, root cause và cách tái hiện.
2. Sửa nhỏ nhất có thể.
3. Thêm hoặc cập nhật regression test.
4. Chạy lại test trực tiếp và các regression suite liên quan.

Không refactor storage architecture, đổi API contract, tạo migration không cần thiết, sửa `.env`, thay đổi Redis Pub/Sub/rate-limit, làm R11 auth cache hoặc làm các hạng mục ngoài R16.

## Báo cáo cuối cùng

Báo cáo ngắn gọn theo mẫu sau. Mỗi dòng phải có `PASS`, `FAIL` hoặc `BLOCKED` và bằng chứng tương ứng.

### 1. Summary

```text
R16 overall: PASS / FAIL / BLOCKED
Code changes: yes/no
```

`PASS` chỉ được dùng khi toàn bộ acceptance bắt buộc đã pass.

### 2. Runtime baseline

- service/container đã kiểm tra;
- số backend replicas thực tế;
- trạng thái health;
- environment/config khác biệt đáng chú ý, không gồm secret.

### 3. Cross-instance matrix

```text
Upload via backend A: PASS/FAIL/BLOCKED
Finalize/download via backend B: PASS/FAIL/BLOCKED
A != B proven: PASS/FAIL/BLOCKED
Object exists in MinIO: PASS/FAIL/BLOCKED
DB metadata correct: PASS/FAIL/BLOCKED
Byte/checksum comparison: PASS/FAIL/BLOCKED
B download after A stopped: PASS/FAIL/BLOCKED
```

### 4. Authorization matrix

Nêu kết quả User A/User B cho download URL, download, finalize, delete và message attachment reference nếu có.

### 5. Validation and failure handling

Nêu từng fixture invalid, MinIO failure scenario và kết quả.

### 6. Security and frontend

Nêu bucket privacy, URL TTL/scope, browser endpoint, credential/log inspection và frontend regression.

### 7. Regression

Báo cáo riêng backend unit, PostgreSQL integration, MinIO integration, frontend test/lint/build và `git diff --check`.

### 8. Files changed

Chỉ liệt kê file thực sự thay đổi và lý do.

### 9. Remaining limitations

Nêu rõ các phần chưa được chứng minh hoặc chưa implement, chẳng hạn orphan cleanup, backup/replication, antivirus, observability hoặc disaster recovery.

Không che giấu blocker bằng cách gọi là limitation và không tuyên bố hoàn tất khi còn acceptance bắt buộc chưa có bằng chứng.

## Changelog

### v2.0 — 2026-10-12

- Tách rõ `PASS`, `FAIL` và `BLOCKED`.
- Thêm evidence gates, instance identity proof và failure classification.
- Liên kết prompt với project context/workflow dùng chung.
