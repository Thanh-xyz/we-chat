# Kế hoạch điều tra và xử lý blocker test quên mật khẩu

**Ngày:** 2026-10-20  
**Mã tác vụ:** TEMP-AUTH-EMAIL-OFF-2026-10-20-R1  
**Giai đoạn:** PLAN — quay lại từ COOK theo lựa chọn C của người dùng  
**Chủ trì:** `wechat_project_lead`  
**Người review:** `wechat_code_reviewer` qua `continuous-improvement-review`  
**Trạng thái:** Chờ người dùng duyệt kế hoạch sửa đổi

## 1. Mục tiêu và tiêu chí nghiệm thu

### Mục tiêu

Xác định nguyên nhân test `ForgotPasswordPage` bị Vitest đánh fail khi kiểm tra lỗi `503`, dù UI có bắt lỗi và hiển thị thông báo. Sau khi chứng minh nguyên nhân, thực hiện thay đổi nhỏ nhất để giữ kiểm tra hồi quy có ý nghĩa và chạy qua. Bảo toàn hợp đồng API, nội dung thông báo, không hiển thị trạng thái thành công khi backend trả lỗi.

Các thay đổi cấu hình email và auth backend P1–P3 từ kế hoạch gốc đã được triển khai và có kiểm tra riêng; kế hoạch này chỉ xử lý blocker xác minh frontend P4. Không sửa backend, cấu hình môi trường hay dịch vụ bên ngoài trong phạm vi này.

### Tiêu chí nghiệm thu

- **R1 — Nguyên nhân đã được chứng minh:** Có một tái hiện nhỏ và bằng chứng phân biệt được lỗi ở mock/test harness, luồng submit của trang hay xử lý lỗi UI. Không chốt giả thuyết chỉ từ tương quan.
- **R2 — Thông báo lỗi chính xác:** Test trang thật xác nhận phản hồi lỗi `503` hiển thị nội dung server `Tính năng email hiện tạm thời không khả dụng.` dưới `role="alert"`.
- **R3 — Không báo thành công:** Khi request thất bại, trang không hiển thị trạng thái `role="status"` thành công và không chuyển sang trạng thái đã gửi.
- **R4 — Không suy giảm kiểm tra:** Giữ nguyên các assertion hành vi của test hồi quy. Chỉ điều chỉnh ranh giới mock hoặc cách kích hoạt submit nếu R1 chứng minh đó là nguyên nhân; không xóa test hay đổi expected để làm test xanh.
- **R5 — Kiểm tra frontend đạt:** Test `RegisterPage` và `ForgotPasswordPage`, lint tập trung và production build đều đạt.
- **R6 — Không đổi production contract:** Không thay API route, status/body `503`, trạng thái đăng ký, quy tắc auth, backend hay cấu hình Render/Vercel.

## 2. Phạm vi và ngoài phạm vi

### Trong phạm vi

- Điều tra test `wechatfrontend/src/pages/ForgotPasswordPage.test.jsx` và luồng `ForgotPasswordPage.submit`.
- Khi đã xác định nguyên nhân, sửa test harness hoặc component React trong phạm vi P4 đã duyệt.
- Chạy kiểm tra frontend tập trung và cập nhật handoff COOK hiện có sau khi quay lại COOK.

### Ngoài phạm vi

- Thay đổi hợp đồng REST, API backend, auth/token, schema, migration, Redis, storage, STOMP, Docker hoặc triển khai Render/Vercel.
- Cài package mới, sửa `.env`, thay đổi dữ liệu hoặc secrets.
- Bật/tắt `AUTH_EMAIL_ENABLED` hay `AUTH_REQUIRE_EMAIL_VERIFIED` trên Render. P7 của kế hoạch gốc vẫn cần phê duyệt riêng.
- Mở rộng sang thiết kế lại luồng quên mật khẩu hoặc nội dung UI ngoài AC5.

## 3. Hiện trạng và bằng chứng

- **VERIFIED-SOURCE:** `ForgotPasswordPage.submit` gọi `requestPasswordReset`, bắt lỗi, chuyển nội dung qua `errorMessage`, hiển thị `FormAlert` và luôn đặt lại trạng thái `submitting`. Nếu request thành công thì mới đặt `sent=true`. Bằng chứng: `wechatfrontend/src/pages/ForgotPasswordPage.jsx` (`submit`); `wechatfrontend/src/components/auth/AuthLayout.jsx` (`FormAlert`).
- **VERIFIED-SOURCE:** `errorMessage` chuẩn hóa lỗi có `response.status` và `response.data.message`, rồi dùng thông điệp server. Bằng chứng: `wechatfrontend/src/services/api/errors.js` (`normalizeApiError`, `errorMessage`).
- **VERIFIED-SOURCE:** `requestPasswordReset` chuyển tiếp tới `authApi.forgotPassword`; API client dùng `POST /auth/forgot-password`. Bằng chứng: `wechatfrontend/src/features/auth/authActions.js`; `wechatfrontend/src/services/api/authApi.js`.
- **VERIFIED-SOURCE:** Test trang mock trực tiếp `requestPasswordReset` bằng `vi.hoisted`, ném lỗi có `response.status=503` và thông báo server, rồi kiểm tra alert cùng việc không có status thành công. Bằng chứng: `wechatfrontend/src/pages/ForgotPasswordPage.test.jsx`.
- **VERIFIED-TEST:** `npm test -- --reporter=verbose src/pages/ForgotPasswordPage.test.jsx` fail với `Service unavailable` tại mock. Lần chạy trước đó có temporary diagnostics cho thấy catch của component nhận lỗi và phần kiểm tra thông báo đã chạy, nhưng Vitest vẫn báo test fail; diagnostics đã được xóa.
- **VERIFIED-TEST:** Một test cô lập tạm thời dùng React event handler, Promise reject và mock `vi.hoisted` cùng kiểu đã pass. File chẩn đoán đã được xóa; kết quả này chứng minh cấu hình Vitest xử lý được ví dụ tối giản, không chứng minh trang thật đúng.
- **VERIFIED-TEST:** Trong COOK cùng ngày, `AuthEmailConfigurationTest` đạt 7 test; bộ backend auth đạt 14 test; `RegisterPage` đạt 3 test; lint và build frontend đạt. Đây là bằng chứng cho các bước trước, không thay cho việc xử lý test `ForgotPasswordPage`.
- **VERIFIED-TEST:** `git diff --check` đạt ở lần kiểm tra cuối COOK.
- **UNRESOLVED:** Bốn cách thử với mock reject `ApiError`, object thường, ném đồng bộ trong/ngoài `act` đều chưa làm test trang thật đạt. Chưa có nguyên nhân gốc được xác nhận.
- **VERIFIED-WORKTREE:** `git status --short` đã được kiểm tra trước bước PLAN. Có sẵn thay đổi người dùng và các file COOK trong working tree; tất cả phải được giữ nguyên. Kế hoạch này là file mới, không thay thế kế hoạch gốc.

## 4. Thiết kế đề xuất

Thực hiện theo nguyên tắc root-cause-first. Bước điều tra đầu tiên sẽ đối chiếu test trang thật với đường gọi production `requestPasswordReset` → `authApi.forgotPassword`, trong khi giữ nguyên assertion R2–R3. Đồng thời quan sát nơi Vitest nhận lỗi chưa xử lý và số lần handler/mock được gọi. Không thay component hay test expectation trước khi bằng chứng cho thấy lớp sai.

Sau khi nguyên nhân được chứng minh:

- Nếu lỗi nằm ở ranh giới mock/test harness, sửa ranh giới mock hoặc cách kích hoạt form để kiểm tra cùng hành vi thật qua luồng `authActions`/`authApi`.
- Nếu handler/component thực sự làm rò lỗi hoặc trạng thái sai, sửa phần nhỏ nhất trong `ForgotPasswordPage` và giữ kiểm tra giao diện hiện tại.
- Nếu bằng chứng chỉ ra vấn đề ở Vitest/jsdom hoặc cấu hình repo ngoài phạm vi frontend page, dừng và quay lại PLAN thay vì thêm dependency hoặc thay cấu hình toàn cục.

Mọi nhánh phải giữ thông điệp `503`, assertion không thành công, và kiểm tra không có trạng thái success. Không đổi status, API payload hoặc bảo mật.

## 5. File và thành phần liên quan

| File/thành phần | Trạng thái | Mục đích |
|---|---|---|
| `wechatfrontend/src/pages/ForgotPasswordPage.test.jsx` | Hiện có | Tái hiện lỗi và duy trì assertion hồi quy; có thể đổi mock boundary sau khi nguyên nhân được chứng minh |
| `wechatfrontend/src/pages/ForgotPasswordPage.jsx` | Hiện có | Chỉ sửa nếu bằng chứng cho thấy lỗi thuộc xử lý submit/UI |
| `wechatfrontend/src/features/auth/authActions.js` | Chỉ đọc | Trace ranh giới gọi reset |
| `wechatfrontend/src/services/api/authApi.js` | Chỉ đọc hoặc mock gián tiếp trong test | Trace request `POST /auth/forgot-password` |
| `wechatfrontend/src/services/api/errors.js` | Chỉ đọc | Xác nhận ánh xạ status/message |
| `wechatfrontend/src/test/setup.js`, `wechatfrontend/vite.config.js` | Chỉ đọc trong P1 | Kiểm tra setup Vitest nếu trace chỉ tới cấu hình |
| `.agents/projects/wechat-clone/WORKFLOW.md` | Chỉ cập nhật ở COOK sau khi duyệt | Ghi status và handoff P4/P5 mới, bảo toàn nội dung hiện có |

Không có file production/backend mới được đề xuất. Không đề xuất migration.

## 6. Các bước triển khai theo thứ tự

### P1 — Chứng minh nguồn lỗi ở test trang thật

- **Mục tiêu:** Phân biệt lỗi do test double, submit event, component catch/render hay Vitest nhận lỗi chưa xử lý.
- **Tệp/symbol:** `ForgotPasswordPage.test.jsx`; `ForgotPasswordPage.submit`; `requestPasswordReset`; `authApi.forgotPassword`; `errorMessage`.
- **Loại:** Điều tra trên code/test hiện có; diagnostics nếu cần chỉ dùng tạm và phải xóa.
- **Ý định:** Chạy đúng test fail với reporter chi tiết; so sánh boundary mock của action với mock tại `authApi.forgotPassword` nhưng giữ nguyên hành vi lỗi và assertion; ghi nhận số lần gọi, lỗi nào được catch và sự kiện error/unhandled rejection mà runner nhận. Chỉ thay một biến trong mỗi đối chứng.
- **Phụ thuộc:** Người dùng duyệt cả bốn deliverable của kế hoạch sửa đổi.
- **Có thể chạy song song:** Không.
- **Tiêu chí:** R1.
- **Xác minh:** `npm test -- --reporter=verbose src/pages/ForgotPasswordPage.test.jsx`; điều tra theo `debugging` đến khi giả thuyết có thể bị bác bỏ và được bằng chứng xác nhận.
- **Rủi ro/rollback:** Không để lại listener, log hoặc file chẩn đoán. Không sửa production trước khi root cause được chứng minh.

### P2 — Sửa đúng lớp đã chứng minh

- **Mục tiêu:** Làm test trang thật phản ánh đúng lỗi API mà không làm yếu tiêu chí nghiệm thu.
- **Tệp/symbol:** Mặc định `ForgotPasswordPage.test.jsx`; chỉ thêm `ForgotPasswordPage.jsx` nếu P1 chứng minh lỗi thuộc UI.
- **Loại:** File hiện có; không thêm package hoặc API.
- **Ý định:** Áp dụng một thay đổi tối thiểu tương ứng với nguyên nhân P1. Giữ kiểm tra alert 503 và vắng mặt success status. Nếu test harness là nguyên nhân, ưu tiên mock boundary sát API thật và giữ `authActions` thật trong test; chỉ làm nếu đối chứng xác nhận cách này giải quyết lỗi.
- **Phụ thuộc:** P1 có root cause `CONFIRMED`.
- **Có thể chạy song song:** Không.
- **Tiêu chí:** R2, R3, R4, R6.
- **Xác minh:** Chạy riêng test `ForgotPasswordPage` sau thay đổi. Nếu test vẫn fail, không lặp lại các cách đoán đã thử; dừng và ghi bằng chứng để quay lại PLAN.
- **Rủi ro/rollback:** Nếu test chỉ xanh do assertion bị bỏ, đổi, hoặc error bị nuốt toàn cục thì không đạt. Có thể rollback đúng diff P2 mà không ảnh hưởng backend.

### P3 — Chạy frontend checks và kiểm tra diff

- **Mục tiêu:** Xác nhận không có hồi quy trong hai auth pages.
- **Tệp/symbol:** Hai page và tests tương ứng; chỉ xem các file thực sự đổi.
- **Loại:** Xác minh, không thêm hành vi.
- **Ý định:** Chạy focused tests, ESLint và Vite production build; rà `git diff --check`; xóa toàn bộ diagnostics tạm.
- **Phụ thuộc:** P2 pass.
- **Có thể chạy song song:** Các lệnh test và lint/build có thể chạy độc lập sau P2.
- **Tiêu chí:** R4, R5, R6.
- **Lệnh:**
  - `npm test -- src/pages/RegisterPage.test.jsx src/pages/ForgotPasswordPage.test.jsx`
  - `npx eslint src/pages/RegisterPage.jsx src/pages/ForgotPasswordPage.jsx src/pages/RegisterPage.test.jsx src/pages/ForgotPasswordPage.test.jsx`
  - `npm run build`
  - Từ repo root: `git diff --check`
- **Rủi ro/rollback:** Build pass không thay cho test tương tác; phải báo riêng từng kết quả.

### P4 — REVIEW và handoff thủ công

- **Mục tiêu:** Kiểm tra diff và bằng chứng độc lập; cập nhật handoff COOK.
- **Tệp/symbol:** Diff P1–P3; `.agents/projects/wechat-clone/WORKFLOW.md`.
- **Loại:** Review và handoff.
- **Ý định:** Dùng `continuous-improvement-review` sau TEST; giữ các thay đổi người dùng khác; ghi rõ P4/P5 status. Không làm P7.
- **Phụ thuộc:** P3 đạt.
- **Có thể chạy song song:** Không.
- **Tiêu chí:** R1–R6.
- **Xác minh:** Không còn Critical/High finding chưa xử lý; `git status --short` không có diagnostics tạm.
- **Rủi ro/rollback:** Review finding về behavior/API cần quay lại PLAN/BA và không được tự mở rộng phạm vi.

| ID | Bước | Phụ thuộc | Nhóm song song | Điều kiện chặn |
|---|---|---|---|---|
| P1 | Chứng minh nguyên nhân test | Duyệt kế hoạch | — | Chưa có root cause thì không sửa |
| P2 | Sửa tối thiểu theo nguyên nhân | P1 | — | Không đổi expectation hay nuốt lỗi |
| P3 | Test, lint, build, diff check | P2 | Test và lint/build | P2 test phải đạt |
| P4 | REVIEW và handoff | P3 | — | Chưa đạt kiểm tra thì chưa REVIEW |

## 7. Specialist routing và approval gates

### Specialist routing

- **Frontend page/test**
  - **Bằng chứng:** `ForgotPasswordPage.jsx:submit`; `ForgotPasswordPage.test.jsx`; `errors.js:errorMessage`.
  - **Skills:** `debugging` ở P1 để chứng minh root cause; `frontend-engineering` ở P2 để sửa test/UI đã duyệt.
  - **Phụ thuộc:** P2 chỉ sau root cause được xác nhận.
  - **Skill xác minh:** `frontend-quality-gate` và `testing` ở P3; evidence là test page, lint và build.
  - **Đã cân nhắc nhưng không chọn:** `frontend-architecture` vì không đổi module/routing; `frontend-ui-ux` vì giữ nguyên journey/copy đã duyệt; `api-contract-design` vì hợp đồng 503 không đổi.
- **Review cuối**
  - **Bằng chứng:** Diff P2 và kết quả P3.
  - **Skill:** `continuous-improvement-review` ở P4.
  - **Phụ thuộc:** P3 đạt.
  - **Không chọn:** `code-review` riêng vì quy trình REVIEW của repository dùng `continuous-improvement-review` và đã bao gồm kiểm tra diff độc lập.
- **Backend/auth/security:** Không sửa trong replan; contract đã chốt trước COOK. `backend-security` không cần chạy lại trừ khi P1 chứng minh có thay đổi authorization/auth behavior, khi đó phải dừng và lập kế hoạch mới.
- **Database/Flyway, realtime/Redis, storage, Docker/Render:** Không bị ảnh hưởng; không chọn specialist cho các lớp này.

### Approval gates

1. **COOK:** Chỉ tiếp tục P1 sau khi người dùng phê duyệt kế hoạch sửa đổi này. Phê duyệt không cho phép thay đổi ngoài phạm vi R1–R6.
2. **Behavior/API deviation:** Nếu nguyên nhân yêu cầu thay đổi API, auth behavior, dependency, global Vitest config hoặc file ngoài phạm vi đã liệt kê, dừng và quay lại PLAN/BA để xin duyệt lại.
3. **Render:** P7 từ kế hoạch gốc tiếp tục cần phê duyệt production riêng; kế hoạch này không sửa dashboard hay deploy.

## 8. Ma trận kiểm chứng

| ID | Tiêu chí | Cách kiểm chứng | Owner/Skill | Môi trường | Kết quả mong đợi |
|---|---|---|---|---|---|
| R1 | Nguyên nhân test đã được chứng minh | Reproduce test thật, so sánh mock boundary và theo dõi catch/unhandled error | `debugging` | Vitest/jsdom | Root cause `CONFIRMED`; một đối chứng có thể bác bỏ giả thuyết |
| R2 | UI hiển thị message 503 | Component test trên trang thật với response error tương ứng | `frontend-engineering`; `testing` | Vitest/jsdom | `role=alert` có thông báo API |
| R3 | Không báo thành công | Assertion trên DOM sau lỗi | `frontend-engineering`; `testing` | Vitest/jsdom | Không có `role=status`; trang không chuyển success |
| R4 | Giữ độ mạnh kiểm tra | Review diff test và assertion trước/sau | `frontend-quality-gate` | Git diff | Assertion R2/R3 giữ nguyên về nghĩa; không có catch-all/global suppress |
| R5 | Auth pages không hồi quy | Lệnh focused Vitest, ESLint, build nêu tại P3 | `frontend-quality-gate` | Frontend workspace | Tất cả đạt; kết quả tách biệt |
| R6 | Contract/backend/deploy không đổi | Rà diff và `git status --short` | `continuous-improvement-review` | Worktree | Không có thay đổi API/backend/env/Render/Vercel |

## 9. Tương thích, bảo mật và vận hành

- **API/tương thích:** Giữ `POST /api/auth/forgot-password` phía backend và API client path `/auth/forgot-password`; lỗi `503` cùng response message không đổi.
- **Bảo mật:** Không thay auth, token hoặc rate limit. Không đưa token, secret hoặc URL ký vào test output.
- **Dữ liệu/migration:** Không áp dụng; không có schema/data change.
- **Realtime/storage:** Không áp dụng vì flow lỗi khôi phục không qua STOMP, Redis Pub/Sub hay attachment storage.
- **Triển khai:** Không thay Render/Vercel. Không kết luận trạng thái runtime từ test local.
- **Khả năng quan sát:** Diagnostics tạm phải được xóa trước khi COOK hoàn tất; không thêm logging vào sản phẩm cho riêng lỗi test.

## 10. Rủi ro, giả định, rollback và Definition of Done

- **Rủi ro:** Test có thể xanh khi dùng mock không đại diện hợp đồng API; test cần giữ actual page và assertion người dùng nhìn thấy. Nếu nguyên nhân nằm trong Vitest/jsdom toàn cục, xử lý ngoài scope page sẽ tạo rủi ro lan rộng và cần kế hoạch riêng.
- **Giả định:** Hợp đồng server trả lỗi `503` đã được chốt trong P1 của kế hoạch gốc; replan này không thay nó.
- **Câu hỏi mở:** Root cause chính xác ở đâu vẫn chưa biết; P1 phải trả lời trước mọi fix.
- **Rollback:** Chỉ hoàn tác diff P2 do tác vụ này tạo nếu test mới làm hỏng page; giữ nguyên các thay đổi COOK trước đó và mọi chỉnh sửa người dùng.
- **Definition of Done:** R1 được xác nhận; R2–R6 đạt; test không còn diagnostics; `git diff --check` đạt; handoff COOK được cập nhật; không có thay đổi production.

## 11. Kiểm tra trong giai đoạn PLAN

- **PASS:** Đọc `AGENTS.md`, workflow project, kế hoạch gốc, test và component liên quan; kiểm tra `git status --short`; xác nhận tên file kế hoạch mới chưa tồn tại.
- **VERIFIED-TEST:** Dùng kết quả test/lint/build từ COOK đã ghi ở mục 3. Không chạy lại acceptance suite trong PLAN này.
- **NOT RUN:** Không chạy test mới, không sửa source/test, không sửa workflow handoff hiện hữu, không thay đổi runtime.

## Handoff thủ công

**A — Approve plan:** Duyệt kế hoạch này trước khi tiếp tục COOK.

**B — Revise plan:** Nêu phần cần điều chỉnh.

**C — Return to analysis:** Quay lại `scout` hoặc `ba-analysis` nếu bằng chứng/phạm vi không đủ.

**D — Stop:** Dừng mà không triển khai thêm.

**Khuyến nghị:** A — Approve plan. Khi được duyệt, bước kế tiếp là P1 của COOK dùng `debugging`; không tự động bắt đầu trước xác nhận của người dùng.
