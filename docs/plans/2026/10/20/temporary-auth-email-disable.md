# Kế hoạch triển khai: Tạm thời tắt gửi email xác thực

**Ngày:** 2026-10-20
**Mã tác vụ:** TEMP-AUTH-EMAIL-OFF-2026-10-20
**Giai đoạn workflow:** PLAN
**Chủ trì:** `wechat_project_lead` (COOK sau khi kế hoạch được phê duyệt)
**Người review:** `wechat_code_reviewer`, dùng `continuous-improvement-review` sau TEST
**Trạng thái kế hoạch:** Đã được người dùng phê duyệt; COOK đang thực hiện, P4 đang bị chặn ở kiểm thử frontend

## 1. Mục tiêu và tiêu chí nghiệm thu

### Mục tiêu

Tạm thời cho phép đăng ký và đăng nhập mà không gửi email xác thực, đồng thời giữ backend trên Render Free. Ứng dụng không được yêu cầu cấu hình SMTP khi email được tắt rõ ràng, không được tạo token xác thực hoặc đặt lại mật khẩu mà người dùng không thể sử dụng, và không được thông báo rằng email đã được gửi nếu dịch vụ gửi không chạy thành công.

Đây là chế độ vận hành tạm thời. Việc chọn nhà cung cấp email qua HTTPS, đăng ký tên miền và bật lại email nằm ngoài kế hoạch này.

### Tiêu chí nghiệm thu

- **AC1 — Chế độ email được cấu hình rõ ràng:** Có một cài đặt backend được tài liệu hóa, đề xuất tên `AUTH_EMAIL_ENABLED`, để điều khiển việc gửi email xác thực. Khi giá trị là `false`, dịch vụ production khởi động mà không cần thông tin SMTP hoặc cấu hình đường dẫn email, đồng thời không khởi tạo mail sender của JavaMail. Khi giá trị là `true`, hành vi SMTP hiện tại vẫn khả dụng.
- **AC2 — Đăng ký:** Khi email bị tắt, `POST /api/auth/register` tạo tài khoản với `emailVerified=false`, không tạo token xác thực và không gọi email sender. Phản hồi cho biết email xác thực chưa được gửi.
- **AC3 — Điều kiện đăng nhập:** Khi email bị tắt, `AUTH_REQUIRE_EMAIL_VERIFIED=false` cho phép tài khoản chưa xác thực đăng nhập. Việc kiểm tra mật khẩu, trạng thái/khóa tài khoản, token, vai trò và các kiểm soát phân quyền khác vẫn được giữ nguyên. Khi `AUTH_REQUIRE_EMAIL_VERIFIED=true`, đăng nhập bằng tài khoản chưa xác thực tiếp tục bị từ chối.
- **AC4 — Khôi phục mật khẩu và gửi lại email:** Khi email bị tắt, luồng quên mật khẩu và gửi lại email xác thực không được tra cứu địa chỉ để quyết định phản hồi, tạo hoặc vô hiệu hóa token, hay gọi email sender. Cả hai trả cùng một phản hồi tổng quát `503 Service Unavailable` với mọi địa chỉ email hợp lệ, duy trì khả năng chống dò tìm tài khoản và báo đúng rằng tính năng hiện không khả dụng.
- **AC5 — Thông báo frontend chính xác:** Giao diện đăng ký dùng phản hồi API để phân biệt “đã gửi email xác thực” với “đang tắt gửi email”. Giao diện quên mật khẩu hiển thị lỗi tính năng bị tắt từ backend và không hiển thị trạng thái thành công/đã gửi khi nhận phản hồi cho biết tính năng bị tắt.
- **AC6 — Token đã tồn tại:** Các liên kết xác thực và đặt lại mật khẩu đã phát hành, chưa hết hạn vẫn được endpoint hiện tại chấp nhận. Khi email bị tắt, hệ thống không phát hành token mới.
- **AC7 — Tương thích và lưu trữ:** Bật email vẫn giữ hành vi email hiện tại cùng các kiểm thử tương ứng. Không thêm schema cơ sở dữ liệu hoặc migration.
- **AC8 — Chế độ Render:** Render tiếp tục dùng dịch vụ Free hiện tại. Sau khi mã được triển khai và xác minh, cấu hình môi trường Render đặt rõ `AUTH_EMAIL_ENABLED=false` và `AUTH_REQUIRE_EMAIL_VERIFIED=false`. Thay đổi dashboard thực tế và smoke test production cần qua cổng phê duyệt riêng trong kế hoạch này.

## 2. Phạm vi và nội dung không thực hiện

### Trong phạm vi

- Cấu hình mail production có điều kiện và một sender bị vô hiệu hóa theo hướng fail-closed.
- Hành vi đăng ký, quên mật khẩu và gửi lại email xác thực khi chế độ email bị tắt.
- Thêm trường vào phản hồi đăng ký để cho biết trạng thái gửi email.
- Cập nhật thông báo đăng ký và quên mật khẩu trong React.
- Kiểm thử backend/frontend tập trung, rà soát tích hợp API và trình tự rollout lên Render.

### Ngoài phạm vi

- Chọn hoặc tích hợp nhà cung cấp email qua HTTPS.
- Đăng ký tên miền, cấu hình DNS sender, SPF/DKIM/DMARC hoặc email marketing.
- Tự động đánh dấu tài khoản là đã xác thực email.
- Thay đổi schema PostgreSQL, Flyway migration, dữ liệu cơ sở dữ liệu, Redis, STOMP, tệp đính kèm hoặc storage.
- Thay đổi kiểm tra đăng ký, mật khẩu, khóa tài khoản, vai trò, quy tắc JWT/session hoặc phân quyền không liên quan.
- Sửa tệp `.env` hoặc triển khai/thay đổi Render hay Vercel trước khi qua các cổng phê duyệt bên dưới.

## 3. Trạng thái hiện tại đã xác minh và các ràng buộc

- **VERIFIED-SOURCE:** `application.properties` ánh xạ `AUTH_REQUIRE_EMAIL_VERIFIED` với giá trị mặc định trong mã là `false`; giá trị trên Render không thể quan sát từ workspace này. Dịch vụ đăng nhập chỉ từ chối người dùng chưa xác thực khi cài đặt này là `true`.
  Bằng chứng: [application.properties](../../../../../wechatbackend/src/main/resources/application.properties) (`app.security.require-email-verified`); [AuthService.java](../../../../../wechatbackend/src/main/java/main/com/chat/wechat/auth/service/AuthService.java) (`login`).
- **VERIFIED-SOURCE:** `AuthMailConfiguration` hoạt động trong production profile và tạo SMTP sender. `AuthMailProperties` yêu cầu host, username, password và địa chỉ người gửi. Chỉ xóa biến SMTP trên Render có thể làm ứng dụng không khởi động.
  Bằng chứng: [AuthMailConfiguration.java](../../../../../wechatbackend/src/main/java/main/com/chat/wechat/auth/service/AuthMailConfiguration.java); [AuthMailProperties.java](../../../../../wechatbackend/src/main/java/main/com/chat/wechat/auth/service/AuthMailProperties.java); [AuthEmailConfigurationTest.java](../../../../../wechatbackend/src/test/java/main/com/chat/wechat/auth/service/AuthEmailConfigurationTest.java).
- **VERIFIED-SOURCE:** Đăng ký chạy trong transaction, lưu tài khoản chưa xác thực, sau đó tạo token xác thực và gọi đồng bộ `AuthEmailService`. Vì vậy lỗi gửi email có thể khiến transaction đăng ký không hoàn tất.
  Bằng chứng: [AuthService.java](../../../../../wechatbackend/src/main/java/main/com/chat/wechat/auth/service/AuthService.java) (`register`, `issueEmailVerification`).
- **VERIFIED-SOURCE:** Luồng quên mật khẩu tạo/lưu token đặt lại trước khi gọi sender. Luồng gửi lại email xác thực tạo token rồi gọi cùng quy trình xác thực. Controller hiện trả `202` sau khi service hoàn tất.
  Bằng chứng: [AuthService.java](../../../../../wechatbackend/src/main/java/main/com/chat/wechat/auth/service/AuthService.java) (`forgotPassword`, `resendVerification`); [AuthController.java](../../../../../wechatbackend/src/main/java/main/com/chat/wechat/auth/controller/AuthController.java) (`forgotPassword`, `resendVerification`).
- **VERIFIED-SOURCE:** `ApiException` nhận HTTP status và global exception handler tuần tự hóa phản hồi này, nên `503` đề xuất có thể dùng cơ chế lỗi hiện tại.
  Bằng chứng: [ApiException.java](../../../../../wechatbackend/src/main/java/main/com/chat/wechat/common/exception/ApiException.java); [GlobalExceptionHandler.java](../../../../../wechatbackend/src/main/java/main/com/chat/wechat/common/exception/GlobalExceptionHandler.java).
- **VERIFIED-SOURCE:** Dịch vụ email ghi log hiện dùng cho local/test bị giới hạn theo profile và chỉ ghi metadata, không ghi token thô. Đây không phải production fallback và giới hạn này phải được giữ nguyên.
  Bằng chứng: [LoggingAuthEmailService.java](../../../../../wechatbackend/src/main/java/main/com/chat/wechat/auth/service/LoggingAuthEmailService.java); [AuthEmailConfigurationTest.java](../../../../../wechatbackend/src/test/java/main/com/chat/wechat/auth/service/AuthEmailConfigurationTest.java).
- **VERIFIED-SOURCE:** Giao diện đăng ký hiện báo đã gửi liên kết xác thực; giao diện quên mật khẩu báo đã gửi hướng dẫn sau phản hồi API thành công. Cơ chế chuẩn hóa lỗi frontend hiển thị thông báo lỗi do server trả về.
  Bằng chứng: [RegisterPage.jsx](../../../../../wechatfrontend/src/pages/RegisterPage.jsx); [ForgotPasswordPage.jsx](../../../../../wechatfrontend/src/pages/ForgotPasswordPage.jsx); [errors.js](../../../../../wechatfrontend/src/services/api/errors.js).
- **VERIFIED-SOURCE:** React API client có phương thức gửi lại email xác thực nhưng hiện chưa có trang nào gọi. Backend endpoint là public và vẫn cần được bảo vệ theo đúng phạm vi.
  Bằng chứng: [authApi.js](../../../../../wechatfrontend/src/services/api/authApi.js); [AuthController.java](../../../../../wechatbackend/src/main/java/main/com/chat/wechat/auth/controller/AuthController.java).
- **VERIFIED-SOURCE:** Kiểm thử hiện tại xác nhận luồng đăng ký/tạo token khi email được bật, tính chống dò tài khoản trong quên mật khẩu, hành vi token đã tồn tại và cấu hình SMTP production. Cần giữ nguyên các kiểm thử hồi quy này.
  Bằng chứng: [AuthApiIntegrationTest.java](../../../../../wechatbackend/src/test/java/main/com/chat/wechat/auth/controller/AuthApiIntegrationTest.java); [AuthEmailConfigurationTest.java](../../../../../wechatbackend/src/test/java/main/com/chat/wechat/auth/service/AuthEmailConfigurationTest.java).
- **DOCUMENTED:** Dịch vụ web Render Free không thể gửi lưu lượng đi qua các cổng SMTP 25, 465 hoặc 587. [Tài liệu Render Free](https://render.com/docs/free).
- **BLOCKED:** Không quan sát được giá trị môi trường và hành vi trực tiếp trên Render/Vercel. Không có quyền truy cập dashboard và không thực hiện smoke test production trong giai đoạn lập kế hoạch này.
- **VERIFIED-SOURCE:** Trước khi ghi artifact kế hoạch, Git đã có `M .agents/projects/wechat-clone/WORKFLOW.md`, `M .agents/skills/plan/SKILL.md`, `M .agents/skills/refactor-plan/SKILL.md` và `D .aws`. Giữ nguyên các thay đổi đó.

## 4. Thiết kế đề xuất và các quyết định quan trọng

1. **RECOMMENDATION — Cấu hình rõ ràng:** Thêm `app.auth.email-enabled` lấy giá trị từ `AUTH_EMAIL_ENABLED`, mặc định trong mã là `true` để giữ tương thích. Dịch vụ Render phải đặt rõ giá trị `false` cho chế độ tạm thời. Cũng đặt rõ `AUTH_REQUIRE_EMAIL_VERIFIED=false` trên Render, không chỉ dựa vào mặc định trong mã.
2. **RECOMMENDATION — Bean có điều kiện:** Khi bật, giữ SMTP sender hiện tại cùng các thuộc tính SMTP/liên kết bắt buộc. Khi tắt, không bind/validate thông tin SMTP hoặc URL email-link và không tạo JavaMail sender. Cung cấp implementation `AuthEmailService` chỉ dành cho production ở chế độ tắt, fail-closed nếu bị gọi nhầm. Không dùng logging service local/test trong production.
3. **RECOMMENDATION — Chặn side effect trong service:** `AuthService` kiểm tra chế độ mail trước khi tra cứu/tạo token ở luồng quên mật khẩu và gửi lại xác thực. Trả cùng `503` tổng quát cho mọi địa chỉ email hợp lệ trước khi kiểm tra tài khoản có tồn tại hay không. Khi email bị tắt, đăng ký bỏ qua việc tạo token xác thực và gọi sender. Giữ nguyên việc xác minh email và kiểm tra token đặt lại mật khẩu để các token cũ chưa hết hạn vẫn hoạt động.
4. **RECOMMENDATION — Hợp đồng đăng ký:** Thêm boolean cộng thêm `verificationEmailSent` vào `RegisterResponse`. Giá trị là `false` khi email bị tắt và chỉ là `true` khi lời gọi sender đã hoàn tất. Điều này xác nhận sender nhận xử lý, không đảm bảo email đã vào hộp thư. Chuyên gia `api-contract-design` cần xác nhận tên, ngữ nghĩa và phần thân lỗi trước khi triển khai.
5. **RECOMMENDATION — Frontend:** Dùng `verificationEmailSent` để chọn thông báo đăng ký. Nếu trường không có trong phản hồi cũ, hiển thị nội dung trung tính để Vercel client có thể triển khai trước backend. Với quên mật khẩu, giữ nguyên form và hiển thị thông báo server về `503` khi tắt; không chuyển sang trạng thái thành công. Các trang verify-email và reset-password vẫn xử lý được token đã tồn tại.
6. **RECOMMENDATION — Không đổi schema:** Token đã lưu giá trị băm và thời hạn hết hạn; dừng phát hành token mới không cần migration. Các bản ghi token cũ tiếp tục theo quy tắc hết hạn/dọn dẹp hiện tại.
7. **Ràng buộc vận hành DOCUMENTED:** Render Free không có shell bền vững và chặn các cổng SMTP đi ra trong môi trường web service Free. Thay đổi môi trường Render và xác minh trực tiếp phải là thao tác trên dashboard có phê duyệt rõ ràng; không bước nào trong kế hoạch cần truy cập shell.

### Các phương án đã cân nhắc

- **Chỉ xóa biến môi trường SMTP:** Không chọn. Cấu hình mail production hiện bắt buộc khi khởi động, và các luồng đăng ký/khôi phục vẫn gọi email.
- **Dùng logging sender local/test trong production:** Không chọn. Service này bị giới hạn theo profile, không gửi liên kết cho người dùng và sẽ che giấu lỗi cấu hình production.
- **Tự đánh dấu tài khoản đã xác thực hoặc ghi token vào log:** Không chọn. Cách này xác nhận quyền sở hữu email khi chưa có bằng chứng hoặc làm lộ thông tin khôi phục tài khoản.
- **Giữ `202 Accepted` nhưng âm thầm bỏ qua gửi email:** Không chọn. Phản hồi và giao diện hiện tại ngụ ý yêu cầu đã được xử lý; trả thành công trong im lặng sẽ gây hiểu nhầm. Dùng `503` thống nhất để báo tính năng không khả dụng mà không tiết lộ tài khoản có tồn tại hay không.

## 5. Tệp và thành phần bị ảnh hưởng

| Thành phần | Cách xử lý dự kiến |
|---|---|
| `wechatbackend/src/main/resources/application.properties` | Thêm ánh xạ cấu hình bật/tắt email. |
| `wechatbackend/src/main/java/main/com/chat/wechat/auth/service/AuthMailConfiguration.java` | Bật điều kiện cho SMTP bean production theo thuộc tính mail. |
| `wechatbackend/src/main/java/main/com/chat/wechat/auth/service/AuthMailProperties.java` | Chỉ bind/validate SMTP khi email được bật. |
| `wechatbackend/src/main/java/main/com/chat/wechat/auth/service/AuthEmailProperties.java` | Không bắt buộc URL email-link khi gửi email bị tắt. |
| `wechatbackend/src/main/java/main/com/chat/wechat/auth/service/DisabledAuthEmailService.java` | **ĐỀ XUẤT TỆP MỚI** — production sender fail-closed cho chế độ email bị tắt. |
| `wechatbackend/src/main/java/main/com/chat/wechat/auth/service/AuthService.java` | Bỏ phát hành token khi đăng ký; chặn quên/gửi lại trước side effect tạo token; giữ nguyên chính sách đăng nhập và luồng tiêu thụ token. |
| `wechatbackend/src/main/java/main/com/chat/wechat/auth/dto/RegisterResponse.java` | Thêm `verificationEmailSent`. |
| `wechatbackend/src/main/java/main/com/chat/wechat/auth/controller/AuthController.java` | Chỉ xác minh hợp đồng; giữ route và annotation 201/202 hiện tại, service `ApiException` sẽ ghi đè ở nhánh bị tắt. |
| `wechatbackend/src/main/java/main/com/chat/wechat/common/exception/GlobalExceptionHandler.java` | Chỉ xác minh hợp đồng; dùng lại phản hồi status hiện có của `ApiException` trừ khi `api-contract-design` phát hiện thiếu sót. |
| `wechatbackend/src/test/java/main/com/chat/wechat/auth/service/AuthEmailConfigurationTest.java` | Xác minh SMTP khi bật và startup/chọn bean khi tắt. |
| `wechatbackend/src/test/java/main/com/chat/wechat/auth/controller/AuthApiIntegrationTest.java` | Giữ hồi quy chế độ bật và token cũ; chỉ điều chỉnh assertion nếu JSON kỳ vọng bị ảnh hưởng bởi trường cộng thêm. |
| `wechatbackend/src/test/java/main/com/chat/wechat/auth/controller/AuthEmailDisabledApiIntegrationTest.java` | **ĐỀ XUẤT TỆP MỚI** — kiểm thử đăng ký/đăng nhập, `503`, không tạo token/không gọi sender khi tắt. |
| `wechatfrontend/src/pages/RegisterPage.jsx` | Hiển thị phản hồi đăng ký chính xác theo response. |
| `wechatfrontend/src/pages/ForgotPasswordPage.jsx` | Hiển thị lỗi tính năng bị tắt `503` và không báo thành công trong nhánh này. |
| `wechatfrontend/src/pages/RegisterPage.test.jsx` | **ĐỀ XUẤT TỆP MỚI** — kiểm tra trạng thái gửi/đã tắt/trung tính với API cũ. |
| `wechatfrontend/src/pages/ForgotPasswordPage.test.jsx` | **ĐỀ XUẤT TỆP MỚI** — xác minh phản hồi tính năng bị tắt không hiển thị thành công. |
| Môi trường dịch vụ Render | Đặt thủ công `AUTH_EMAIL_ENABLED=false` và `AUTH_REQUIRE_EMAIL_VERIFIED=false` sau deploy; không thay dashboard trong PLAN. |

### Các phần được xác định là không ảnh hưởng

- **Database/schema/migrations:** Không ảnh hưởng; thay đổi hành vi phát hành token nhưng không cần chuyển đổi schema/dữ liệu.
- **Authentication và authorization:** Chỉ hành vi xác thực bị ảnh hưởng bởi cài đặt `requireEmailVerified` vốn đã có. Các kiểm tra khác giữ nguyên; chế độ tạm thời này chấp nhận quyền sở hữu email chưa được xác minh.
- **Realtime/Redis/STOMP:** Không ảnh hưởng; không có luồng event liên quan.
- **Storage/S3/Supabase:** Không ảnh hưởng.
- **Docker/Compose:** Không ảnh hưởng bởi rollout chỉ trên Render; không dự kiến sửa Compose.
- **Frontend REST contract:** Có ảnh hưởng do trường đăng ký cộng thêm và hành vi `503` được mô tả; path và body request giữ nguyên.

## 6. Các bước triển khai theo thứ tự và phụ thuộc

| ID | Công việc | Phụ thuộc | Nhóm song song | Lý do chặn |
|---|---|---|---|---|
| P1 | Chốt hợp đồng phản hồi đăng ký và lỗi mail bị tắt | Phê duyệt kế hoạch | 1 | Frontend và backend phải thống nhất trường dữ liệu và ngữ nghĩa lỗi. |
| P2 | Thêm chế độ mail rõ ràng và production bean có điều kiện | P1 | 2 | Hành vi service cần hợp đồng cấu hình ổn định. |
| P3 | Triển khai hành vi backend khi chế độ mail bị tắt | P1, P2 | 3 | Khi tắt không được tạo side effect token hoặc gọi mail. |
| P4 | Cập nhật thông báo đăng ký/khôi phục trên React | P1 | 3 | Frontend dùng hợp đồng response/lỗi đã thống nhất. |
| P5 | Xác minh backend và frontend tập trung | P2, P3, P4 | 4 | Cần hoàn tất thay đổi xuyên suốt các tầng. |
| P6 | Review độc lập và đánh giá sẵn sàng triển khai | P5 | 5 | Reviewer cần xem diff cuối cùng đã kiểm thử. |
| P7 | Deploy và đổi môi trường Render | P6 và phê duyệt riêng của người dùng | 6 | Cấu hình dịch vụ bên ngoài và production traffic nằm ngoài quyền của PLAN. |

### P1 — Chốt hợp đồng API

- **Mục tiêu:** Xác nhận trường đăng ký bổ sung và phản hồi tổng quát khi mail bị tắt.
- **Tệp/symbol:** `RegisterResponse`; `AuthController.register`, `forgotPassword`, `resendVerification`; `GlobalExceptionHandler`; `errors.js`.
- **Hiện có hay mới:** Tệp hiện có; chỉ chốt hợp đồng.
- **Ý định thay đổi:** Giữ đăng ký trả 201 và thêm `verificationEmailSent`. Quên/gửi lại khi bật vẫn trả 202; khi tắt trả `503` đồng nhất với một thông báo người dùng tổng quát cho mọi email hợp lệ.
- **Phụ thuộc:** Người dùng phê duyệt kế hoạch này.
- **Có thể làm song song với:** Không; đây là điều kiện tiên quyết của hợp đồng.
- **Tiêu chí nghiệm thu liên quan:** AC2, AC4, AC5.
- **Xác minh:** `api-contract-design` rà soát và chốt kỳ vọng kiểm thử hợp đồng.
- **Rủi ro/rollback:** Trường cộng thêm tương thích ngược. `503` là nhánh mới cho chế độ bị tắt; status chế độ đang bật không đổi.

### P2 — Thêm cấu hình mail có điều kiện

- **Mục tiêu:** Cho phép production khởi động không cần SMTP credential khi email được tắt rõ ràng.
- **Tệp/symbol:** `application.properties`; `AuthMailConfiguration`; `AuthMailProperties`; `AuthEmailProperties`; `DisabledAuthEmailService` mới.
- **Hiện có hay mới:** Các lớp cấu hình hiện tại và một service production mới được đề xuất.
- **Ý định thay đổi:** Mặc định giữ chế độ đang bật; chỉ tạo SMTP properties/sender khi bật; mỗi profile/chế độ có đúng một `AuthEmailService`; logging service tiếp tục chỉ dành cho local/test.
- **Phụ thuộc:** P1.
- **Có thể làm song song với:** Không; logic nghiệp vụ backend cần thuộc tính/chính sách bean trước.
- **Tiêu chí nghiệm thu liên quan:** AC1, AC7.
- **Xác minh:** `AuthEmailConfigurationTest` chứng minh chọn SMTP khi bật, startup khi tắt không cần SMTP/link properties, không có JavaMailSender khi tắt và không dùng logging fallback trong production.
- **Rủi ro/rollback:** Bean thiếu hoặc mâu thuẫn có thể làm ứng dụng không khởi động. Giữ nguyên SMTP env vars trong cửa sổ rollback ban đầu; không xóa chúng trong công việc này.

### P3 — Chặn side effect trong auth service và trả trạng thái gửi

- **Mục tiêu:** Đảm bảo chế độ bị tắt phản hồi đúng và không tạo side effect.
- **Tệp/symbol:** `AuthService.register`, `AuthService.forgotPassword`, `AuthService.resendVerification`, `AuthService.verifyEmail`, `AuthService.resetPassword`; `RegisterResponse`.
- **Hiện có hay mới:** Tệp hiện có.
- **Ý định thay đổi:** Bỏ phát hành token xác thực khi đăng ký; ném cùng lỗi `503` trước khi tra cứu tài khoản/tạo token ở quên/gửi lại; giữ nguyên luồng tiêu thụ token và điều kiện đăng nhập; trả trường trạng thái gửi mới.
- **Phụ thuộc:** P1 và P2.
- **Có thể làm song song với:** P4 sau khi P1 chốt; backend và frontend có thể triển khai riêng theo hợp đồng đã thống nhất.
- **Tiêu chí nghiệm thu liên quan:** AC2, AC3, AC4, AC6.
- **Xác minh:** Kiểm thử API chế độ tắt xác nhận trạng thái tài khoản, hành vi đăng nhập theo hai giá trị cấu hình, `503` đồng nhất với email tồn tại/không tồn tại, không có token mới và không gọi sender.
- **Rủi ro/rollback:** Đăng nhập tạm thời khi email chưa xác thực làm giảm độ tin cậy rằng người đăng ký kiểm soát địa chỉ đã nhập. Không đặt `emailVerified=true`.

### P4 — Làm cho thông báo frontend dựa trên response

- **Mục tiêu:** Bỏ thông báo sai rằng email đã được gửi hoặc khôi phục đã thành công.
- **Tệp/symbol:** `RegisterPage`; `ForgotPasswordPage`; các test page mới được đề xuất.
- **Hiện có hay mới:** Các trang hiện tại cùng hai test file mới được đề xuất.
- **Ý định thay đổi:** Dùng trường response đăng ký; hiển thị nội dung trung tính nếu API cũ không có trường; hiển thị thông báo server `503` khi khôi phục và không chuyển sang trạng thái thành công.
- **Phụ thuộc:** P1.
- **Có thể làm song song với:** P3 sau khi P1 được chốt.
- **Tiêu chí nghiệm thu liên quan:** AC5.
- **Xác minh:** Vitest component tests cho response đăng ký bật/tắt/cũ và `503` khôi phục; chạy lint tập trung và production build.
- **Rủi ro/rollback:** Vercel và Render triển khai độc lập. Triển khai client xử lý được cả response cũ và mới trước khi tắt mail backend.

### P5 — Chạy kiểm tra tập trung

- **Mục tiêu:** Xác minh cả hai chế độ cùng hợp đồng REST/UI.
- **Tệp/symbol:** Backend auth configuration/integration tests; React auth page tests.
- **Hiện có hay mới:** Test hiện tại và các test đề xuất ở trên.
- **Ý định thay đổi:** Không thay đổi mã sản phẩm trong bước này.
- **Phụ thuộc:** P2–P4.
- **Có thể làm song song với:** Các lệnh test backend và frontend độc lập.
- **Tiêu chí nghiệm thu liên quan:** AC1–AC7.
- **Lệnh xác minh dự kiến:**
  - Từ thư mục gốc: `cd wechatbackend && ./mvnw -Dtest=AuthEmailConfigurationTest,AuthApiIntegrationTest,AuthEmailDisabledApiIntegrationTest test`
  - Từ thư mục gốc: `cd wechatfrontend && npm test -- src/pages/RegisterPage.test.jsx src/pages/ForgotPasswordPage.test.jsx`
  - Từ thư mục gốc: `cd wechatfrontend && npm run lint -- src/pages/RegisterPage.jsx src/pages/ForgotPasswordPage.jsx`
  - Từ thư mục gốc: `cd wechatfrontend && npm run build`
- **Rủi ro/rollback:** H2 test kiểm tra logic service/API nhưng không chứng minh mạng hoặc nhà cung cấp trên Render hoạt động trực tiếp. Smoke test Render được thực hiện riêng.

### P6 — Review độc lập

- **Mục tiêu:** Kiểm tra sự phù hợp với kế hoạch, ranh giới auth, tương thích hợp đồng, thông báo UI chính xác và side effect token.
- **Tệp/symbol:** Diff cuối của P2–P4 và báo cáo xác minh.
- **Hiện có hay mới:** Chỉ review.
- **Ý định thay đổi:** Phát hiện và xử lý vấn đề trọng yếu trước khi thay đổi cấu hình production.
- **Phụ thuộc:** P5.
- **Có thể làm song song với:** Không.
- **Tiêu chí nghiệm thu liên quan:** Tất cả AC.
- **Xác minh:** `continuous-improvement-review` với bằng chứng bảo mật/tích hợp; không còn phát hiện Critical/High chưa xử lý.
- **Rủi ro/rollback:** Phát hiện làm đổi hành vi hoặc hợp đồng API phải quay lại PLAN/BA và cần phê duyệt trước khi mở rộng phạm vi.

### P7 — Rollout Render (thao tác bên ngoài)

- **Mục tiêu:** Bật chế độ tạm thời đã kiểm thử trên dịch vụ Free hiện tại.
- **Tệp/symbol:** Chỉ cài đặt dashboard Render; không sửa tệp repository.
- **Hiện có hay mới:** Cấu hình runtime bên ngoài.
- **Ý định thay đổi:** Triển khai frontend/backend tương thích trước, sau đó đặt rõ `AUTH_EMAIL_ENABLED=false` và `AUTH_REQUIRE_EMAIL_VERIFIED=false`, restart/redeploy và chạy smoke test đăng ký/đăng nhập/khôi phục/gửi lại.
- **Phụ thuộc:** P6 và phê duyệt rõ ràng riêng cho thay đổi Render.
- **Có thể làm song song với:** Không.
- **Tiêu chí nghiệm thu liên quan:** AC8 và AC1–AC5 trên môi trường đã deploy.
- **Xác minh:** Render khởi động không cần SMTP; đăng ký thành công; tài khoản chưa xác thực đăng nhập được; khôi phục/gửi lại trả cùng `503`; UI đã deploy không khẳng định đã gửi email.
- **Rủi ro/rollback:** Dịch vụ Free có thể scale về 0 và cần thời gian khởi động. Nếu rollback sang bản cũ, bản đó bỏ qua flag mới; giữ nguyên SMTP env vars bắt buộc trong cửa sổ rollback. Trên Render Free, SMTP có thể tiếp tục lỗi khi dùng mã cũ, nên rollback có thể khôi phục lỗi đăng ký trước đó thay vì khôi phục gửi email hoạt động.

## 7. Định tuyến chuyên gia

- **Tầng/thành phần bị ảnh hưởng:** Hợp đồng REST và phản hồi đăng ký/lỗi.
  - **Bằng chứng từ SCOUT/mã nguồn hiện tại:** `RegisterResponse`; `AuthController.register`, `forgotPassword`, `resendVerification`; `GlobalExceptionHandler.handleApiException`.
  - **Skill được chọn:** `api-contract-design`.
  - **Mục đích và giai đoạn:** Chốt trường phản hồi và hợp đồng `503` khi tắt trước COOK.
  - **Phụ thuộc:** Phê duyệt thủ công kế hoạch này; hợp đồng phải chốt trước khi triển khai backend/frontend.
  - **Skill xác minh và bằng chứng:** `testing` xác nhận status/body; `frontend-integration-review` kiểm tra cách client xử lý.
  - **Skill liên quan đã cân nhắc nhưng không chọn:** `wechat-frontend-integration`.
  - **Lý do không chọn:** Dùng review tích hợp frontend tiêu chuẩn để xác minh xuyên tầng; thiết kế hợp đồng thuộc `api-contract-design`.

- **Tầng/thành phần bị ảnh hưởng:** Logic auth Spring, transaction và cấu hình email.
  - **Bằng chứng từ SCOUT/mã nguồn hiện tại:** `AuthService.register`, `forgotPassword`, `resendVerification`, `issueEmailVerification`; `AuthMailConfiguration`; `AuthMailProperties`.
  - **Skill được chọn:** `spring-boot-engineering` (kèm hướng dẫn `wechat-spring-data-change` khi hành vi API/data đi qua nhiều tầng).
  - **Mục đích và giai đoạn:** COOK triển khai thay đổi service/cấu hình đã được duyệt.
  - **Phụ thuộc:** Hợp đồng API P1; cấu hình P2 phải có trước logic service P3.
  - **Skill xác minh và bằng chứng:** `testing` chạy test cấu hình/API tập trung và kiểm tra side effect trên bảng token.
  - **Skill liên quan đã cân nhắc nhưng không chọn:** `backend-architecture`.
  - **Lý do không chọn:** Không đề xuất đổi ranh giới package hoặc quyền sở hữu thành phần.

- **Tầng/thành phần bị ảnh hưởng:** Mức bảo đảm authentication và xác thực tài khoản.
  - **Bằng chứng từ SCOUT/mã nguồn hiện tại:** `AuthService.login` kiểm tra `requireEmailVerified`; đăng ký lưu `emailVerified=false`.
  - **Skill được chọn:** `backend-security`.
  - **Mục đích và giai đoạn:** Hướng dẫn COOK và review REVIEW về mức bảo đảm giảm, disabled sender fail-closed, không tự xác thực và không làm lộ token.
  - **Phụ thuộc:** Yêu cầu và hợp đồng API được thống nhất.
  - **Skill xác minh và bằng chứng:** `testing` kiểm tra hai giá trị `requireEmailVerified`; `continuous-improvement-review` rà soát ranh giới bảo mật.
  - **Skill liên quan đã cân nhắc nhưng không chọn:** `debugging`.
  - **Lý do không chọn:** Đây là chế độ vận hành tạm thời đã chọn, không phải điều tra nguyên nhân gốc của sự cố chưa rõ.

- **Tầng/thành phần bị ảnh hưởng:** Thông báo đăng ký và khôi phục mật khẩu trên React.
  - **Bằng chứng từ SCOUT/mã nguồn hiện tại:** Nội dung báo đã gửi hiện tại trong `RegisterPage`; thông báo thành công trong `ForgotPasswordPage`; các route `authApi`.
  - **Skill được chọn:** `frontend-engineering`.
  - **Mục đích và giai đoạn:** COOK cập nhật phản hồi theo response/lỗi.
  - **Phụ thuộc:** Hợp đồng REST P1.
  - **Skill xác minh và bằng chứng:** `frontend-quality-gate` chạy Vitest, lint, build tập trung; `frontend-integration-review` kiểm tra cách hiểu API.
  - **Skill liên quan đã cân nhắc nhưng không chọn:** `frontend-architecture`.
  - **Lý do không chọn:** Không cần đổi routing, store hoặc cấu trúc module.

- **Tầng/thành phần bị ảnh hưởng:** Hợp đồng môi trường Render và trình tự phát hành.
  - **Bằng chứng từ SCOUT/mã nguồn hiện tại:** Mapping env trong `application.properties` và production mail config; giới hạn cổng SMTP của Render Free đã được DOCUMENTED.
  - **Skill được chọn:** `deployment-operations`.
  - **Mục đích và giai đoạn:** COOK review hợp đồng biến môi trường và trình tự deploy/rollback; thay đổi production vẫn cần phê duyệt.
  - **Phụ thuộc:** Sẵn sàng P6 và phê duyệt rõ ràng riêng cho thao tác dashboard.
  - **Skill xác minh và bằng chứng:** Bằng chứng smoke test Render thủ công sau khi deploy được duyệt; không tuyên bố đã xác minh runtime trong PLAN.
  - **Skill liên quan đã cân nhắc nhưng không chọn:** `wechat-runtime-verification`.
  - **Lý do không chọn:** Skill này xác minh runtime Compose của repository; mục tiêu triển khai ở đây là Render và dashboard trực tiếp không truy cập được.

- **Tầng/thành phần bị ảnh hưởng:** Database và Flyway.
  - **Bằng chứng từ SCOUT/mã nguồn hiện tại:** Repository token email đã lưu token băm kèm hạn dùng; kế hoạch dừng phát hành mới nhưng không chuyển đổi schema/dữ liệu.
  - **Skill được chọn:** Không có.
  - **Mục đích và giai đoạn:** Không áp dụng.
  - **Phụ thuộc:** Không có.
  - **Skill xác minh và bằng chứng:** API test kiểm tra không tạo dòng token khi tắt; không cần test migration.
  - **Skill liên quan đã cân nhắc nhưng không chọn:** `database-migration`, `data-modeling`, `data-quality-integrity`.
  - **Lý do không chọn:** Không đề xuất đổi schema, mô hình dữ liệu hoặc sửa dữ liệu hiện có.

- **Tầng/thành phần bị ảnh hưởng:** Realtime, Redis, storage và Compose.
  - **Bằng chứng từ SCOUT/mã nguồn hiện tại:** Luồng auth-email không đi qua STOMP, Redis Pub/Sub, attachment storage hoặc Compose trong phạm vi này.
  - **Skill được chọn:** Không có.
  - **Mục đích và giai đoạn:** Không áp dụng.
  - **Phụ thuộc:** Không có.
  - **Skill xác minh và bằng chứng:** Không cần test realtime/storage cho thay đổi này; smoke test Render được xử lý riêng.
  - **Skill liên quan đã cân nhắc nhưng không chọn:** `realtime-engineering`, `wechat-storage-acceptance`, `wechat-runtime-verification`.
  - **Lý do không chọn:** Các subsystem này không thay đổi; xác minh Compose không chứng minh trạng thái triển khai Render.

- **Tầng/thành phần bị ảnh hưởng:** Tích hợp xuyên tầng và review cuối.
  - **Bằng chứng từ SCOUT/mã nguồn hiện tại:** Đăng ký đi qua controller → service → token repository → email sender → React registration page; khôi phục có luồng API/client tương ứng.
  - **Skill được chọn:** `frontend-integration-review`, `testing`, `frontend-quality-gate`, `continuous-improvement-review`.
  - **Mục đích và giai đoạn:** TEST xác minh tương thích hợp đồng và nghiệm thu; REVIEW rà soát diff cuối cùng cùng rủi ro còn lại.
  - **Phụ thuộc:** Triển khai P2–P4; kiểm tra P5 hoàn tất trước review P6.
  - **Bằng chứng xác minh:** Hợp đồng response, số lượng token trong database, trạng thái trang, output test và diff cuối.
  - **Skill liên quan đã cân nhắc nhưng không chọn:** `architecture-review`.
  - **Lý do không chọn:** Không có thay đổi lớn về kiến trúc repository hoặc module.

**Điều phối:** Không khởi chạy agent nào. Workflow của repository yêu cầu handoff thủ công; một skill được liệt kê trong kế hoạch chưa được xem là đã áp dụng cho đến khi được gọi và làm theo ở đúng giai đoạn.

## 8. Ma trận xác minh

| ID | Tiêu chí | Cách xác minh | Chủ trì | Môi trường cần thiết | Bằng chứng mong đợi |
|---|---|---|---|---|---|
| AC1 | Production ở chế độ tắt khởi động không cần SMTP và không tạo JavaMailSender | Test context cấu hình với `AUTH_EMAIL_ENABLED=false`, không có SMTP/link properties | `spring-boot-engineering`; `testing` | Spring test context | Context khởi động; một disabled service; không có JavaMailSender bean |
| AC2 | Register tạo tài khoản chưa xác thực, không có token/lời gọi email, response báo chưa gửi | MockMvc API test chế độ tắt cùng assertion token-table và sender | `spring-boot-engineering`; `testing` | H2 auth test context | 201, `emailVerified=false`, `verificationEmailSent=false`, không có token row/lời gọi sender |
| AC3 | Chỉ cho đăng nhập tài khoản chưa xác thực khi cờ yêu cầu xác thực email là false | API integration tests với cả giá trị false và true | `backend-security`; `testing` | H2 auth test context | Đăng nhập thành công khi false; status 403 hiện có khi true |
| AC4 | Quên/gửi lại trả 503 đồng nhất và không tạo token | API tests với email có/không có tài khoản, xác nhận trước mọi side effect tra cứu | `spring-boot-engineering`; `testing` | H2 auth test context | Cùng status/thông báo; không có reset/verification token row; sender không bị gọi |
| AC5 | UI phản ánh trạng thái mail và không báo đã gửi email khôi phục khi nhận 503 | React component tests; kiểm tra trạng thái success/error | `frontend-engineering`; `frontend-quality-gate` | Vitest/jsdom | Nội dung đúng cho đăng ký đã gửi/bị tắt/response cũ trung tính; 503 hiển thị là lỗi, không phải thành công |
| AC6 | Token chưa hết hạn vẫn hoạt động; hành vi token hết hạn/không hợp lệ giữ nguyên | Giữ và chạy các test token integration hiện có | `spring-boot-engineering`; `testing` | H2 auth test context | Assertion token hợp lệ/hết hạn/đã dùng hiện tại đều đạt |
| AC7 | Chế độ mail bật giữ hành vi hiện tại và schema không đổi | Test production config và Auth API hiện có; kiểm tra diff migration | `spring-boot-engineering`; `testing` | Spring test context; diff repository | Khi bật có SMTP bean; test đường đi email hiện có đạt; không thêm migration |
| AC8 | Dịch vụ Render Free chạy ở chế độ tạm thời | Sau phê duyệt riêng, kiểm tra dashboard và smoke test đã deploy | `deployment-operations`; thao tác thủ công được người dùng duyệt | Render dashboard và site Vercel đã deploy | Giá trị env đúng; service khởi động; đăng ký/đăng nhập/khôi phục đúng AC |

## 9. Bảo mật, tương thích, dữ liệu và vận hành

- **Mức bảo đảm authentication:** Cho đăng nhập khi chưa xác thực email nghĩa là chưa chứng minh quyền sở hữu địa chỉ được nhập. Chỉ chấp nhận như chế độ tạm thời; giữ `emailVerified=false`, ghi nhận rõ cấu hình tạm thời và giữ mọi kiểm tra auth/phân quyền khác.
- **An toàn token:** Không tạo hoặc ghi log token xác thực/đặt lại mới khi gửi bị tắt. Không đưa token, URL hoặc secret vào exception hay test output.
- **Chống dò tài khoản:** Trả cùng phản hồi `503` trước khi tra cứu địa chỉ; không phân biệt tài khoản có hay không.
- **Tương thích:** Boolean đăng ký mới chỉ bổ sung. Frontend dùng nội dung trung tính nếu backend cũ không trả field. Quên/gửi lại khi mail bật vẫn `202`; chỉ nhánh bị tắt trả `503`.
- **Transaction:** Giữ hành vi transaction hiện tại khi email bật. Khi tắt, tránh ghi token và gọi sender, nhờ vậy đăng ký không bị rollback do kết nối SMTP bị chặn.
- **Dữ liệu:** Không thay migration hoặc quy tắc dọn token. Token có sẵn tiếp tục được tiêu thụ/hết hạn theo mã hiện tại.
- **Hiệu năng/khả dụng:** Không thêm request ra ngoài. Render Free có thể scale về 0; thời gian khởi động lại là đặc tính triển khai, không tự nó chứng minh lỗi auth.
- **Quan sát hệ thống:** Không ghi token thô hoặc URL email-link. Phản hồi `503` tổng quát và request ID thông thường đủ để chẩn đoán yêu cầu chế độ tắt.
- **Thứ tự triển khai:** Trước hết deploy frontend chấp nhận cả hai dạng response; deploy backend hỗ trợ mới nhưng giữ biến môi trường hiện tại cho rollback; sau đó mới đặt rõ hai flag Render khi cả hai phiên bản đã sẵn sàng.
- **Khôi phục:** Khi bị tắt, người dùng đã đăng xuất không thể đặt lại mật khẩu qua email. Người dùng còn đăng nhập vẫn có thể dùng endpoint đổi mật khẩu hiện tại. Trường hợp mất quyền truy cập khi đã đăng xuất cần luồng khôi phục riêng, nằm ngoài phạm vi.
- **Bật lại trong tương lai:** Không tự động bật yêu cầu xác thực email. Tài khoản chưa xác thực vẫn giữ nguyên trạng thái; cần đánh giá luồng xác thực/gửi lại trước khi đổi cờ yêu cầu.

## 10. Rủi ro, phương án, giả định, câu hỏi mở và rollback

### Rủi ro

- **Đánh đổi sản phẩm/bảo mật — Cao:** Có thể tạo tài khoản với địa chỉ người dùng không kiểm soát. Chỉ chấp nhận tạm thời; không tự xác thực địa chỉ.
- **Giới hạn khôi phục tài khoản — Cao:** Người dùng đã đăng xuất không thể khôi phục mật khẩu qua email trong thời gian tắt.
- **Sai lệch cấu hình — Trung bình:** Render và Vercel là hai deployment riêng. Triển khai theo thứ tự cùng trường response bổ sung sẽ giảm nhưng không loại bỏ sai lệch phiên bản.
- **Giới hạn rollback — Trung bình:** Backend cũ bỏ qua `AUTH_EMAIL_ENABLED` và có thể gọi SMTP trở lại. Giữ biến SMTP hiện tại trong cửa sổ rollback. Trên Render Free, SMTP có thể vẫn bị chặn, nên rollback có thể đưa hệ thống về lỗi đăng ký cũ.
- **Chưa rõ runtime:** Kế hoạch này chưa xác minh giá trị hiện tại hoặc hành vi runtime trên Render.

### Giả định

- **ASSUMPTION:** Chế độ tạm thời được duyệt sẽ cho phép đăng ký/đăng nhập nhưng giữ `emailVerified=false`; điều này theo tiêu chí BA đã chấp nhận.
- **ASSUMPTION:** Chọn `503` đồng nhất thay vì âm thầm trả `202` cho thao tác quên/gửi lại khi tắt; phù hợp đề xuất BA đã chấp nhận.
- **ASSUMPTION:** Chế độ SMTP production hiện tại cần tiếp tục khả dụng cho sender trong tương lai, vì vậy cài đặt mail mới mặc định true trong mã và Render chủ động đặt false.
- **ASSUMPTION:** Frontend/backend có thể deploy độc lập; field API được bổ sung và frontend xử lý trung tính khi field vắng mặt.

### Câu hỏi mở

- **OPEN QUESTION:** Trước COOK, `api-contract-design` cần xác nhận tên field response chính xác cùng hình dạng/message tổng quát cho `503`. Không bắt đầu triển khai trước khi thống nhất hợp đồng.
- **OPEN QUESTION:** Trước production rollout, người dùng cần duyệt riêng thao tác đổi môi trường Render và smoke test. Kế hoạch này không cho phép truy cập hoặc thay đổi Render/Vercel.

### Kế hoạch rollback

1. Không đổi schema nên không cần rollback database.
2. Giữ SMTP env vars hiện tại cho đến khi backend/frontend mới qua smoke test được duyệt và hết cửa sổ rollback.
3. Nếu ứng dụng mới không khởi động, rollback sang deploy Render trước đó và giữ nguyên cấu hình môi trường trước đó. Lỗi gửi đăng ký cũ có thể tiếp tục vì Render Free chặn SMTP.
4. Nếu hành vi chế độ tắt sai, khôi phục build đã triển khai ổn định trước đó và giữ các cờ tạm thời được ghi nhận; không bật xác thực email hoặc tự xác thực tài khoản như biện pháp thay thế.
5. Chỉ bật lại gửi sau quyết định riêng và khi sender đã được xác minh là có thể truy cập; chỉ đổi `AUTH_REQUIRE_EMAIL_VERIFIED` sau khi quyết định cách người dùng chưa xác thực hiện tại có thể xác minh lại.

## 11. Định nghĩa hoàn tất

- AC1–AC8 đã được triển khai hoặc, với AC8, được xác minh sau khi có phê duyệt triển khai rõ ràng.
- Hợp đồng REST chính xác được duyệt trước khi sửa mã.
- Cấu hình production khi tắt khởi động không cần SMTP credential và không khởi tạo mail transport.
- Luồng auth bị tắt không tạo token không dùng được và không gọi email sender.
- UI không nói email đã gửi khi API báo gửi bị tắt.
- Hồi quy chế độ bật, test chính sách auth, frontend tests, lint và frontend build đạt; kết quả được báo cáo trung thực.
- Không có schema migration, sửa tệp môi trường ngoài phạm vi hoặc thay đổi công việc Git đã tồn tại từ trước.
- Review độc lập không còn phát hiện Critical/High chưa giải quyết.
- Chỉ thay đổi dashboard/deploy Render sau cổng phê duyệt rõ ràng tương ứng.

### Các kiểm tra đã thực hiện trong PLAN

- **PASS — Kiểm tra mã nguồn:** Đã đọc controller, service, cấu hình, response DTO, exception mapping, các trang/frontend API/xử lý lỗi và test liên quan.
- **PASS — Git preflight:** Chạy `git status --short` trước khi tạo artifact và xem diff hiện có; giữ nguyên các thay đổi trên workflow/skill và tệp `.aws` đã bị xóa.
- **PASS — Kiểm tra trùng artifact:** Trước khi tạo, chưa có plan file ở đường dẫn ngày đã chọn.
- **SKIPPED — Test/build:** Không chạy test hoặc build trong giai đoạn PLAN.
- **BLOCKED — Runtime trực tiếp:** Không quan sát được giá trị dashboard Render/Vercel, trạng thái startup hoặc hành vi API smoke trong phiên này.

### Định tuyến chuyên gia và cổng phê duyệt

1. **Cổng duyệt kế hoạch:** Người dùng phải duyệt đủ bốn deliverable của PLAN trước khi bắt đầu COOK.
2. **Cổng hợp đồng:** `api-contract-design` phải chốt field đăng ký và hợp đồng phản hồi `503` trước khi triển khai.
3. **Cổng xác minh:** Các kiểm tra backend/frontend và review độc lập phải đạt trước khi đổi cấu hình production.
4. **Cổng triển khai:** Người dùng phải duyệt rõ việc đổi cấu hình môi trường Render và chạy smoke test production.
5. **Cổng bật lại trong tương lai:** Chọn nhà cung cấp email qua HTTPS, cấu hình sender/domain đã xác minh và bật yêu cầu xác thực cần một tác vụ riêng được duyệt.

### Handoff thủ công

- **A — Duyệt kế hoạch:** Chờ người dùng duyệt rõ cả bốn deliverable PLAN trước khi COOK hoặc workflow triển khai nào khác bắt đầu.
- **B — Sửa kế hoạch:** Hỏi nội dung cần thay đổi.
- **C — Quay lại phân tích:** Đề xuất `scout` hoặc `ba-analysis` nếu thiếu bằng chứng hoặc yêu cầu chưa rõ.
- **D — Dừng:** Kết thúc mà không triển khai.

**Đề xuất:** Chọn A — duyệt kế hoạch; chỉ bắt đầu COOK sau khi người dùng xác nhận rõ kế hoạch cùng hợp đồng, xác minh, định tuyến và các cổng phê duyệt.
