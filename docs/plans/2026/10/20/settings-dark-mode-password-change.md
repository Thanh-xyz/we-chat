# Kế hoạch: Settings, dark mode và đổi mật khẩu

- Ngày: 2026-10-20
- Giai đoạn: PLAN
- Task ID: WEB-SETTINGS-SECURITY-THEME
- Owner COOK đề xuất: `wechat_project_lead`
- Reviewer đề xuất: `wechat_code_reviewer`
- Đầu vào: Scout và BA trong lượt hội thoại này; người dùng xác nhận phương án A và xác nhận dark mode áp dụng toàn website, gồm các trang auth.
- Trạng thái: Kế hoạch chờ duyệt. Phê duyệt BA không phải phê duyệt bắt đầu COOK.


## 1. Mục tiêu và tiêu chí nghiệm thu

### Mục tiêu

Thêm trang Settings cho người dùng đã đăng nhập, hỗ trợ đổi dark/light theme trên toàn website và cung cấp form đổi mật khẩu dùng endpoint hiện có. Theme được lưu cục bộ trên trình duyệt theo giả định đã nêu trong BA. Lỗi trạng thái WebSocket được tách thành một việc điều tra riêng; kế hoạch này không thay đổi reconnect hoặc hợp đồng realtime.

### Tiêu chí nghiệm thu

- **SET-01 — Điều hướng:** người dùng đã đăng nhập mở được Settings từ điều hướng trong app; người chưa đăng nhập bị route guard hiện tại chặn.
- **THEME-01 — Phủ giao diện:** lựa chọn theme áp dụng lên cả các trang auth (login, register, forgot/reset password, verify email), chat, danh bạ và Settings.
- **THEME-02 — Lưu lựa chọn:** dark/light được lưu trên cùng trình duyệt sau reload; lần đầu mặc định light. Không đồng bộ lựa chọn giữa thiết bị.
- **THEME-03 — Đọc và thao tác:** lựa chọn theme có nhãn truy cập được, dùng được bằng bàn phím, focus rõ và màu chữ/nền đủ tương phản trong trạng thái thường, lỗi, hover, focus và disabled.
- **PASS-01 — Đổi mật khẩu thành công:** người dùng gửi mật khẩu hiện tại và mật khẩu mới hợp lệ; frontend gọi `POST /api/auth/change-password`; khi server trả thành công, session cục bộ được xóa và người dùng được hướng đến đăng nhập lại.
- **PASS-02 — Đổi mật khẩu thất bại:** mật khẩu hiện tại sai, mật khẩu mới không hợp lệ hoặc xác nhận không khớp thì không hiện trạng thái thành công; lỗi hiển thị rõ và không lộ dữ liệu nhạy cảm.
- **PASS-03 — Bảo vệ tài khoản:** không có trường user ID do client chọn; server tiếp tục xác định tài khoản từ principal đã xác thực. Request chưa xác thực bị từ chối.
- **PASS-04 — Chống gửi lặp:** khi request đang chạy, submit bị khóa; trường mật khẩu được xóa sau thành công hoặc rời trang.

**Ngoài tiêu chí của kế hoạch này:** nguyên nhân và cách sửa trạng thái kết nối. Cần có kết quả debugging trước khi tạo kế hoạch riêng cho thay đổi reconnect.

## 2. Phạm vi và ngoài phạm vi

### Trong phạm vi

- Route Settings được bảo vệ dưới khu vực app.
- Nút/điểm vào Settings từ sidebar hiện tại.
- Lựa chọn theme toàn website, bao gồm các trang auth mà người dùng vừa xác nhận.
- Lưu lựa chọn theme trong localStorage của trình duyệt; light là mặc định.
- Form đổi mật khẩu tích hợp API hiện hữu, xác nhận mật khẩu mới ở client và xử lý session bị thu hồi.
- Kiểm thử frontend, kiểm tra contract auth hiện hữu, lint/build và kiểm tra trình duyệt theo ma trận bên dưới.

### Ngoài phạm vi

- Đổi REST contract, backend service, schema, Flyway migration hoặc chính sách mật khẩu.
- Đồng bộ theme theo tài khoản/giữa thiết bị.
- Đổi cách giữ phiên sau khi đổi mật khẩu; hợp đồng hiện tại thu hồi refresh token và tăng token version.
- Sửa reconnect, STOMP destinations, Redis delivery, replay/resync hoặc gateway.
- Mở rộng Settings sang thông báo, hồ sơ, privacy hay các cài đặt khác.
- Đổi upload/download hoặc storage attachment.
- Redesign toàn bộ giao diện ngoài các sửa CSS cần để hai theme hiển thị đúng.

## 3. Trạng thái hiện tại đã xác minh và ràng buộc

Các nhãn dưới đây mô tả source hiện tại, không phải kết quả runtime:

- **VERIFIED-SOURCE:** `App.jsx` có route public và route được bọc bởi `ProtectedRoute`; hiện có `/app`, `/app/chat/:conversationId`, `/app/contacts`, chưa có Settings. `ChatSidebar.jsx` có thông báo, danh bạ và đăng xuất.
- **VERIFIED-SOURCE:** `styles-tokens.css` chứa token sáng ở `:root`; `index.css` có các màu nền/viền sáng viết trực tiếp trên auth, chat và contacts. Dark mode phải rà toàn bộ CSS để không còn mảng sáng ngoài chủ ý.
- **VERIFIED-SOURCE:** `AuthLayout.jsx` dùng chung bởi một số trang auth; `VerifyEmailPage.jsx` cũng render qua layout này. Theme toàn website bao gồm các trang public hiện có.
- **VERIFIED-SOURCE:** `AuthController.changePassword` trả `204 No Content`; `ChangePasswordRequest` có `currentPassword` và `newPassword`; `AuthService.changePassword` kiểm tra user hiện tại, password policy và mật khẩu cũ, sau đó cập nhật password/token version, thu hồi refresh token và ghi audit.
- **VERIFIED-SOURCE:** `SecurityConfig` yêu cầu authentication cho endpoint này; `JwtAuthenticationFilter` so sánh token version với auth state hiện tại. Không cần truyền user ID từ frontend.
- **VERIFIED-SOURCE:** `authApi.js` chưa có `changePassword`; `useAuthStore.expireSession` xóa session local; `App.jsx` đã lắng nghe event `wechat:session-expired`.
- **VERIFIED-SOURCE / rủi ro tích hợp:** `apiClient` thử refresh và gửi lại request cho mọi response 401 có refresh token, ngoại trừ request refresh. Backend hiện trả 401 khi mật khẩu hiện tại sai; thao tác có thể bị retry một lần trước khi lỗi được hiển thị. Không đổi interceptor chung trong phạm vi này nếu chưa chứng minh UX lỗi.
- **VERIFIED-SOURCE:** có test source cho `AuthApiIntegrationTest.changePasswordRevokesAllRefreshTokensAndRequiresNewPassword` và `WebSocketManager`; chúng chưa được chạy trong giai đoạn PLAN.
- **ASSUMPTION đã được xác nhận trong BA:** theme lưu trên thiết bị hiện tại bằng localStorage, mặc định light.
- **VERIFIED-SOURCE, trạng thái working tree:** có nhiều thay đổi chưa commit, gồm `AuthService`, các file auth email, trang đăng ký/khôi phục mật khẩu, `.aws` đang bị xóa và `docs/plans/` chưa track. Tất cả phải được bảo toàn; không ghi đè hoặc revert các chỉnh sửa có sẵn.
- **SKIPPED:** không chạy unit/integration test, lint, build, browser hoặc runtime check trong PLAN. Không có runtime evidence mới.

## 4. Thiết kế đề xuất và quyết định

1. Dùng một trang riêng `/app/settings` vì có hai luồng khác nhau: preference giao diện và một thao tác bảo mật cần thông báo trạng thái/đăng nhập lại. Sidebar hiện có là nơi điều hướng vào trang.
2. Tạo Zustand store nhỏ cho theme, đọc/ghi một key localStorage; store đồng bộ `document.documentElement.dataset.theme`. Khởi tạo trước lần render đầu ở `main.jsx` để giảm chớp giao diện sáng khi đã lưu dark.
3. Khai báo bảng token dark bằng selector trên root document trong `styles-tokens.css`; sửa `index.css` để bề mặt và text của auth/chat/contacts/notifications/forms dùng token. Giữ các gradient/brand có chủ ý và xác minh contrast trong cả hai theme.
4. Giữ nguyên API đổi mật khẩu: thêm hàm vào `authApi.js`, gọi qua `apiClient`, không tạo endpoint mới. Tái sử dụng `PasswordField` và `FormAlert`; xác nhận mật khẩu mới là kiểm tra phía client.
5. Sau response thành công, gọi `expireSession` và chuyển tới `/login`. Không gọi server logout như điều kiện để xóa session: refresh token đã bị thu hồi và endpoint logout có thể thất bại; session local vẫn phải được xóa.
6. Không thay đổi `WebSocketManager` trong công việc này. Source cho thấy badge dựa trên callback STOMP; chưa có browser/transport evidence để kết luận root cause. Yêu cầu chẩn đoán dùng skill `debugging` riêng trước khi lập kế hoạch sửa.

## 5. File và component dự kiến

### File hiện có dự kiến sửa

- `wechatfrontend/src/main.jsx` — khởi tạo theme trước render.
- `wechatfrontend/src/App.jsx` — route được bảo vệ cho Settings; giữ listener hết hạn session hiện tại.
- `wechatfrontend/src/components/conversations/ChatSidebar.jsx` — thêm lối vào Settings.
- `wechatfrontend/src/styles-tokens.css` — token cho dark theme.
- `wechatfrontend/src/index.css` — dùng token cho toàn bộ các bề mặt auth và app.
- `wechatfrontend/src/services/api/authApi.js` — thêm client cho `POST /auth/change-password`.

### File mới đề xuất — chưa tồn tại trong repository

- `wechatfrontend/src/pages/SettingsPage.jsx` — tổng hợp theme và phần đổi mật khẩu.
- `wechatfrontend/src/features/settings/settingsStore.js` — trạng thái/persistence theme cục bộ.
- `wechatfrontend/src/components/settings/ChangePasswordForm.jsx` — form đổi mật khẩu, validation, loading, lỗi và thành công.
- `wechatfrontend/src/features/settings/settingsStore.test.js` — mặc định light, đổi theme và persistence.
- `wechatfrontend/src/pages/SettingsPage.test.jsx` — render các mục Settings và điều khiển theme.
- `wechatfrontend/src/components/settings/ChangePasswordForm.test.jsx` — validation, submit, lỗi, trạng thái loading và session clear sau thành công.
- `wechatfrontend/src/components/conversations/ChatSidebar.test.jsx` — lối vào Settings.
- `wechatfrontend/src/services/api/authApi.test.js` — phương thức, URL và payload của client đổi mật khẩu.

Không dự kiến sửa backend, database, Docker/Compose, gateway, attachment/storage hoặc cấu hình môi trường.

## 6. Thứ tự công việc và phụ thuộc

### P1 — Theme state và CSS tokens

- **Mục tiêu:** áp dụng theme sớm và nhất quán trên toàn website, gồm auth routes.
- **File/symbol:** sửa `main.jsx`, `styles-tokens.css`, `index.css`; thêm `features/settings/settingsStore.js` và `settingsStore.test.js`.
- **Hiện có/mới:** các file đầu đã có; store và test là file mới đề xuất.
- **Ý định:** mặc định light; đọc/ghi localStorage; cập nhật thuộc tính theme ở root; thay các màu nền/text/viền cần thiết bằng semantic tokens.
- **Phụ thuộc:** không.
- **Có thể chạy song song:** rà soát token và form Settings có thể thiết kế song song, nhưng tích hợp vào SettingsPage cần P2.
- **Acceptance:** THEME-01, THEME-02.
- **Verification:** test store; browser check qua auth, chat, contacts và Settings; rà CSS để không còn vùng trắng/sáng ngoài chủ ý.
- **Rủi ro/rollback:** nếu browser không có localStorage hoặc dữ liệu không hợp lệ, fallback light. Rollback bằng cách bỏ khởi tạo/store/selector dark và giữ token sáng hiện tại.

### P2 — Settings route, trang và điều hướng

- **Mục tiêu:** tạo điểm vào Settings dưới quyền route guard hiện có.
- **File/symbol:** sửa `App.jsx`, `ChatSidebar.jsx`; thêm `pages/SettingsPage.jsx`, `SettingsPage.test.jsx`, `ChatSidebar.test.jsx`.
- **Hiện có/mới:** route và sidebar đã có; page/tests mới đề xuất.
- **Ý định:** thêm route `/app/settings`, nút có accessible name, trang có đường quay lại chat, điều khiển Light/Dark nối với store.
- **Phụ thuộc:** P1 cho theme store; P3 có thể tích hợp form sau khi khung Settings tồn tại.
- **Có thể chạy song song:** phần khung page và P3 có thể phát triển song song nếu component form sở hữu file riêng, nhưng tích hợp cuối cùng tuần tự.
- **Acceptance:** SET-01, THEME-03.
- **Verification:** React tests cho sidebar/page và kiểm tra route guard, keyboard, mobile/desktop trong browser.
- **Rủi ro/rollback:** không thay route contacts hoặc hành vi auth; rollback bằng cách gỡ riêng route và nút Settings.

### P3 — Form đổi mật khẩu và session handling

- **Mục tiêu:** nối UI với hợp đồng API hiện tại, xử lý lỗi và thu hồi session minh bạch.
- **File/symbol:** sửa `services/api/authApi.js`; thêm `components/settings/ChangePasswordForm.jsx` và test; sử dụng `PasswordField`, `FormAlert`, `errorMessage`, `useAuthStore.expireSession`.
- **Hiện có/mới:** API backend và component password field đã có; caller/form/test mới đề xuất.
- **Ý định:** gửi đúng hai trường backend yêu cầu; kiểm tra mật khẩu xác nhận ở client; disable submit khi pending; sau 204 xóa session local và chuyển tới login.
- **Phụ thuộc:** P2 để gắn form vào trang Settings.
- **Có thể chạy song song:** test API service có thể phát triển cùng form nếu file ownership tách biệt; tích hợp cuối cần tuần tự.
- **Acceptance:** PASS-01 đến PASS-04.
- **Verification:** Vitest cho API/form; chạy `AuthApiIntegrationTest` hiện có để xác nhận contract backend và thu hồi token; kiểm tra browser với account test.
- **Rủi ro/rollback:** 401 sai mật khẩu có thể bị interceptor refresh/retry một lần. Nếu kiểm thử cho thấy lỗi bị che hoặc session bị xóa sai, quay lại PLAN để đánh giá contract/error classification; không thay interceptor chung trong task này.

### P4 — Quality gate và review

- **Mục tiêu:** xác minh toàn bộ tiêu chí giao diện và auth sau khi P1–P3 hoàn tất.
- **File/symbol:** tất cả file sửa/thêm ở trên; không thay đổi thêm trong P4 chỉ để làm test xanh.
- **Phụ thuộc:** P1–P3.
- **Có thể chạy song song:** không; chạy sau khi tích hợp xong.
- **Acceptance:** tất cả SET/THEME/PASS.
- **Verification:** matrix ở mục 8, review độc lập, sửa lỗi theo phê duyệt riêng.
- **Rủi ro/rollback:** nếu quality gate thất bại, trả COOK cho đúng phần liên quan rồi chạy lại test; không gộp sửa ngoài phạm vi.

### Bảng phụ thuộc

| ID | Công việc | Phụ thuộc | Nhóm song song | Lý do chặn |
|---|---|---|---|---|
| P1 | Theme state/tokens | — | 1 | Cần trước khi theme control dùng được. |
| P2 | Settings route/page/navigation | P1 | 2 | Page cần store và route guard. |
| P3 | Form đổi mật khẩu/API client | P2 để tích hợp | 2 | Có thể phát triển form riêng nhưng chỉ tích hợp khi page tồn tại. |
| P4 | Test tổng hợp, lint/build, browser, review | P1–P3 | 3 | Chỉ có giá trị trên bản đã tích hợp. |

## 7. Specialist routing và approval gates

### Specialist routing

- **UI route, Settings page, form và accessibility**
  - Affected component: frontend route/page/navigation.
  - Evidence từ SCOUT: `App.jsx:ProtectedRoute`, `ChatSidebar.jsx:sidebar-actions`; chưa có Settings.
  - Selected skill: `frontend-ui-ux` ở PLAN/COOK; `frontend-engineering` ở COOK.
  - Dependencies: P1 trước tích hợp theme; route/auth không đổi.
  - Verification: `frontend-quality-gate` và `testing`; expected evidence là test output cùng kiểm tra browser/kb/responsive.
  - Considered but not selected: `frontend-architecture`; không có bằng chứng cần đổi cấu trúc module rộng.

- **Theme tokens và CSS toàn website**
  - Affected component: `styles-tokens.css`, `index.css`, `main.jsx`.
  - Evidence từ SCOUT/BA: token sáng và màu hard-coded; người dùng xác nhận bao gồm auth pages.
  - Selected skill: `frontend-design-system` ở PLAN/COOK; `frontend-engineering` ở COOK.
  - Dependencies: lựa chọn theme toàn site đã được người dùng xác nhận; persistence local theo BA.
  - Verification: `frontend-quality-gate`; expected evidence là lint/build, test store và kiểm tra trực quan tất cả route.
  - Considered but not selected: skill UI design tổng thể; không redesign toàn trang.

- **Auth client, password flow và session**
  - Affected component: frontend API/auth session; backend contract hiện tại được dùng lại.
  - Evidence: `AuthController.changePassword`, `AuthService.changePassword`, `authApi.js` chưa có caller, `authStore.expireSession`.
  - Selected skill: `wechat-frontend-integration` và `frontend-engineering` ở COOK.
  - Dependencies: giữ nguyên endpoint/payload/204; P2 trước tích hợp form.
  - Verification: `frontend-integration-review`, `testing`, test backend `AuthApiIntegrationTest`; expected evidence gồm payload đúng, từ chối request chưa auth và xử lý revoke-session.
  - Considered but not selected: `spring-boot-engineering`, `wechat-spring-data-change`, `backend-security` implementation; backend contract/security không đổi trong plan này. Nếu phát sinh đổi server behavior, quay lại PLAN và chọn specialist tương ứng.

- **Database, migration, storage và attachment**
  - Affected status: không ảnh hưởng theo quyết định lưu theme cục bộ; không thêm dữ liệu backend.
  - Evidence: store theme ở frontend; API đổi mật khẩu dùng user/token hiện có.
  - Selected skill: không chọn migration/storage skill.
  - Verification: không có migration/storage check trong task; bảo đảm diff không chạm các boundary này.
  - Considered but not selected: `database-migration`, `data-modeling`, `wechat-storage-acceptance`; không có schema hoặc storage change.

- **Realtime, Redis, gateway và runtime**
  - Affected status: ngoài phạm vi implementation plan này; không sửa reconnect hoặc Compose.
  - Evidence: trạng thái được set ở `WebSocketManager`; nguyên nhân runtime chưa xác định.
  - Selected skill: `debugging` cho chẩn đoán riêng trước mọi thay đổi connection; sau đó `wechat-realtime-contract` nếu nguyên nhân yêu cầu sửa reconnect/resync. Đây là handoff riêng, chưa chạy.
  - Verification: browser/network evidence cho debugging; không đưa vào quality gate của Settings.
  - Considered but not selected: `wechat-runtime-verification`; không có yêu cầu xác nhận deployment/replica trong plan này.

- **Review và ownership**
  - Owner COOK: `wechat_project_lead`.
  - Reviewer: `wechat_code_reviewer`, dùng `continuous-improvement-review` sau TEST cho diff frontend/auth.
  - Dependencies: chỉ review sau khi có diff và TEST report.
  - Reviewer không được coi test pass là đủ; kiểm tra session revocation, scope, CSS toàn site và các file dirty trước đó.

### Approval gates

1. Người dùng phải duyệt kế hoạch này trước khi bắt đầu COOK.
2. Phê duyệt chỉ bao gồm Settings, local theme và UI đổi mật khẩu. Không bao gồm thay đổi backend contract/schema, policy session, reconnect, deployment, commit hoặc push.
3. Theme được xác nhận áp dụng cả website; localStorage theo thiết bị là giả định BA đã dùng. Nếu cần đồng bộ account, quay lại PLAN vì sẽ ảnh hưởng API/database.
4. Lỗi trạng thái kết nối cần debugging riêng; không có reconnect fix trong COOK của kế hoạch này.
5. Bảo toàn mọi thay đổi hiện hữu; không revert toàn file hoặc xóa dữ liệu chưa commit.

## 8. Ma trận kiểm tra

Các lệnh dưới đây là **planned**, chưa chạy. Frontend scripts được khai báo trong `wechatfrontend/package.json`; backend dùng Maven Wrapper. Backend auth integration test chạy với H2 fixture, không chứng minh tương thích PostgreSQL nhưng phù hợp để kiểm tra contract hiện hữu khi không đổi schema.

| ID | Tiêu chí | Phương pháp xác minh | Môi trường cần có | Bằng chứng mong đợi |
|---|---|---|---|---|
| SET-01 | Mở Settings và giữ route protected | `cd wechatfrontend && npm test -- src/components/conversations/ChatSidebar.test.jsx src/pages/SettingsPage.test.jsx`; browser kiểm tra user đã/ chưa đăng nhập | Node dependencies; browser session cho manual | Link hoạt động; guest bị guard hiện tại chặn |
| THEME-01 | Theme áp dụng trên auth, chat, contacts, settings | `SettingsPage`/theme tests và browser kiểm tra toàn bộ các route | Frontend test/browser | Không có bề mặt sáng ngoài chủ ý ở dark mode |
| THEME-02 | Theme persist sau reload, light mặc định | `settingsStore.test.js`; reload browser cùng profile | jsdom/localStorage; browser | Theme giữ nguyên trên cùng browser; thiết bị mới mặc định light |
| THEME-03 | Accessible, responsive theme control | Browser keyboard check, focus/contrast check và frontend quality gate | Browser desktop/mobile viewport | Accessible name/pressed state đúng, focus rõ, layout không vỡ |
| PASS-01 | Đổi mật khẩu và đăng nhập lại | `npm test -- src/components/settings/ChangePasswordForm.test.jsx src/services/api/authApi.test.js`; `cd wechatbackend && ./mvnw -Dtest=AuthApiIntegrationTest test`; browser với account test | Node dependencies; Java/Maven; test fixture | Payload đúng, response success, session local bị xóa, trang login hiện ra |
| PASS-02 | Lỗi current password/policy/confirmation | Vitest form; backend integration test hiện có; browser | Test fixtures | Lỗi hiển thị, không có success state, không ghi password vào log |
| PASS-03 | Endpoint protected và user identity từ principal | Backend `AuthApiIntegrationTest`; frontend integration review | Maven/JDK 21 | Request unauthenticated bị từ chối; client không gửi user ID |
| PASS-04 | Không submit lặp; xóa password sau thành công | Vitest form và browser keyboard check | Frontend dependencies/browser | Nút pending disabled; fields được reset khi kết thúc thành công |
| ALL | Regression/style/build | `cd wechatfrontend && npm run lint`; `cd wechatfrontend && npm run build` | Node dependencies | Exit code 0, không lỗi lint/build |

Không chạy các lệnh này trong PLAN. Browser check dùng account test; không ghi thông tin đăng nhập hoặc token vào output.

## 9. Security, compatibility, dữ liệu và vận hành

- **Authorization:** endpoint đổi mật khẩu tiếp tục yêu cầu access token hợp lệ, lấy user từ principal. Không đưa quyền quyết định tài khoản vào frontend.
- **Password privacy:** không log request body, không echo password vào alert; dùng autocomplete phù hợp (`current-password`, `new-password`); khóa submit khi pending và clear state sau thành công.
- **Session compatibility:** backend đổi password tăng token version và thu hồi refresh tokens. Frontend xóa local session ngay khi nhận 204; không kỳ vọng phiên hiện tại tiếp tục hoạt động.
- **API compatibility:** không đổi URL, DTO, status hoặc error contract. Kiểm tra current 401 refresh interceptor behavior; nếu yêu cầu thay đổi semantics, cần sửa BA/PLAN.
- **Data/migration:** N/A theo localStorage. Không sửa Flyway hoặc dữ liệu người dùng.
- **Realtime:** N/A trong implementation; Redis Pub/Sub không được thay đổi.
- **Storage:** N/A; không đụng attachment/private storage.
- **Performance/observability:** không thêm dependency, metric hay log. Theme store chỉ lưu giá trị enum nhỏ; không log giá trị form.
- **Deployment:** frontend mới cần backend đang có `/api/auth/change-password`; source hiện tại đã xác nhận endpoint. Không đổi Compose hoặc gateway.
- **Rollback:** revert riêng route, menu, store, page/form, token/CSS và API caller thuộc diff đã review. Không revert file nguyên trạng vì working tree đã có user edits; không đụng `.aws`, `docs/plans/` khác hoặc file auth email đang sửa.

## 10. Rủi ro, phương án, giả định và câu hỏi còn mở

### Rủi ro

- Màu hard-coded rải rác có thể gây vùng sáng hoặc contrast kém trong dark mode; phải kiểm tra toàn bộ route, không chỉ Settings.
- `apiClient` retry một response 401 có thể làm lỗi “mật khẩu hiện tại không đúng” hiển thị chậm. Chỉ mở rộng scope nếu test chứng minh UX không chấp nhận được.
- Cập nhật `main.jsx`/CSS trong working tree có thể chồng với thay đổi khác; dùng diff nhỏ và giữ nguyên chỉnh sửa sẵn có.
- Lỗi kết nối realtime chưa có root cause. Không tuyên bố đã giải quyết trong plan này.

### Phương án đã so sánh

- **Trang Settings riêng (khuyến nghị):** phù hợp với hai nhóm cài đặt; hỗ trợ route history và mobile tốt. Có thêm route/page.
- **Dialog/popover:** truy cập nhanh hơn, nhưng form đổi mật khẩu và focus management khó dùng hơn; kém thuận tiện nếu thêm cài đặt sau này.
- **Theme localStorage (khuyến nghị):** không cần backend/migration, dễ rollback; không đồng bộ thiết bị.
- **Theme lưu theo tài khoản:** đồng bộ nhiều thiết bị nhưng cần API, schema, auth và rollout bổ sung; không nằm trong lựa chọn BA hiện tại.
- **Tăng retry vô hạn cho WebSocket:** chưa được chọn; có thể che lỗi auth hoặc giữ vòng thử khi server không khả dụng. Cần debugging trước.

### Giả định và câu hỏi mở

- **ASSUMPTION:** người dùng đổi theme trong Settings sau đăng nhập; lựa chọn đã lưu vẫn được áp dụng trên các trang auth khi quay lại.
- **ASSUMPTION:** theme là tùy chọn theo browser/device; light mặc định.
- Không còn câu hỏi chặn plan cho phạm vi đã được người dùng xác nhận.
- Kết quả debugging connection sẽ là đầu vào cho một BA/PLAN riêng nếu cần sửa code.

## 11. Definition of Done

- Route Settings được bảo vệ, có liên kết điều hướng, hiển thị đúng ở desktop và mobile.
- Dark/light áp dụng trên toàn website, giữ lựa chọn sau reload cùng browser, light là mặc định mới.
- Form đổi mật khẩu gọi đúng API hiện hữu; xử lý success/error/pending và kết thúc session local sau success.
- Không thay đổi backend security policy, REST contract, database, storage, realtime hay deployment.
- Mọi acceptance criteria trong mục 1 có kết quả kiểm thử trực tiếp; lint/build đạt; browser check hoàn tất hoặc limitation được ghi rõ.
- Diff không chứa thay đổi ngoài phạm vi, không làm mất user edits; không có password/token trong log.
- TEST report hoàn tất, reviewer đã đánh giá diff, không còn Critical/High finding chưa xử lý.

## Kiểm tra đã thực hiện trong PLAN

- `git status --short`: PASS — working tree có thay đổi có sẵn; đã ghi nhận và giữ nguyên.
- Đọc source route, theme CSS, password API/security/session, realtime client, test source và `docs/testing.md`: PASS.
- Test/lint/build/browser/runtime: SKIPPED — chưa chạy trong giai đoạn lập kế hoạch.
- Tạo plan mới tại đường dẫn này; không sửa application code, test, config, database hoặc runtime.

## Manual handoff

**A — Approve plan:** wait for explicit approval of all four PLAN deliverables before COOK or any other implementation workflow begins.

**B — Revise plan:** ask what needs to change.

**C — Return to analysis:** recommend `scout` or `ba-analysis` if evidence or requirements are insufficient.

**D — Stop:** finish without implementing anything.

**Khuyến nghị: A.** Phê duyệt kế hoạch và bốn deliverables trước khi bắt đầu COOK; lỗi trạng thái kết nối vẫn cần được điều tra riêng trước khi sửa.
