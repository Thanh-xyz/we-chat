# Kế hoạch ổn định trạng thái kết nối WebSocket

- Ngày: 2026-10-26
- Task ID: `WS-CONNECTION-STABILITY-2026-10-26`
- Owner dự kiến: `wechat_project_lead`
- Reviewer dự kiến: `wechat_code_reviewer`
- Trạng thái: `PLAN — chờ phê duyệt`

## 1. Mục tiêu và tiêu chí chấp nhận

### Mục tiêu

Sửa lỗi trạng thái STOMP/WebSocket chuyển qua lại giữa `offline` và `reconnecting` sau khi đã hết số lần retry; đồng thời làm cho reconnect an toàn với access token gần hết hạn, retry thủ công, callback cũ và khoảng thời gian Redis Pub/Sub không thể replay.

### Tiêu chí chấp nhận

- **AC-1 — Backoff có giới hạn:** sau lần kết nối ban đầu thất bại, client chỉ lên lịch tối đa sáu lần retry với các khoảng `1s, 2s, 5s, 10s, 20s, 30s`; sau lần cuối thất bại không còn timer hoặc kết nối ngầm nào.
- **AC-2 — Trạng thái ổn định:** trong khi còn retry, trạng thái là `reconnecting`; `offline` chỉ xuất hiện khi người dùng chủ động disconnect, lấy token thất bại không thể phục hồi, hoặc toàn bộ retry đã hết. Trạng thái không tự quay lại `reconnecting` sau `offline` nếu không có thao tác retry mới.
- **AC-3 — Một lifecycle owner:** `WebSocketManager` sở hữu timer, attempt counter và client generation; STOMP auto-reconnect bị tắt để không có hai scheduler cạnh tranh.
- **AC-4 — Token hợp lệ trước CONNECT:** mỗi attempt lấy access token còn hiệu lực; token thiếu hoặc sắp hết hạn được refresh bằng cơ chế `refreshPromise` hiện có. Refresh đồng thời chỉ tạo một request; refresh thất bại kết thúc vòng reconnect và phát sự kiện hết phiên theo contract frontend hiện hành.
- **AC-5 — Không race retry/disconnect:** nhiều lần nhấn retry, React StrictMode cleanup/setup, logout hoặc chuyển user không tạo nhiều client song song; callback từ client cũ không được đổi state hoặc subscription của client mới.
- **AC-6 — Khôi phục sau reconnect:** subscription được tạo đúng một lần trên client mới; hội thoại đang mở được tải lại theo flow hiện có, danh sách hội thoại được tải lại với query hiện tại, và notification list/unread count được đồng bộ từ REST.
- **AC-7 — Không đổi server contract:** không thay đổi REST endpoint, DTO backend, STOMP destination/authorization, Redis channel, database, Flyway, storage, Compose hoặc gateway.
- **AC-8 — Quality gate:** focused tests, toàn bộ frontend unit tests liên quan, lint, build và `git diff --check` đều pass; kiểm tra browser mất/kết nối lại được chạy khi runtime và tài khoản test sẵn sàng, nếu không phải ghi `BLOCKED`.

## 2. Phạm vi và non-goals

### Trong phạm vi

- Lifecycle connect, reconnect, manual retry, disconnect và stale callback trong `WebSocketManager`.
- Theo dõi thời hạn access token trong memory và cung cấp token đủ hạn dùng cho STOMP CONNECT.
- Tái sử dụng refresh-token rotation và single-flight refresh hiện có.
- Resync frontend sau reconnect cho messages đang mở, conversation list và notifications.
- Regression tests cho scheduler, state sequence, token refresh, retry race và resync.

### Ngoài phạm vi

- Thay đổi TTL JWT, refresh-token rotation hoặc authorization backend.
- Thay đổi STOMP destination, message/event payload hoặc Redis Pub/Sub delivery semantics.
- Thêm durable event log/replay, Kafka, Redis Streams hoặc message queue mới.
- Thay đổi schema/Flyway, attachment storage, Docker Compose, Nginx hoặc deployment.
- Thêm presence/online status của người dùng; badge này chỉ phản ánh kết nối STOMP của client hiện tại.
- Commit, push, rebase, deploy hoặc thay đổi dữ liệu/runtime bên ngoài.

## 3. Trạng thái hiện tại và ràng buộc đã xác minh

### Bằng chứng hiện tại

- `VERIFIED-SOURCE` — `wechatfrontend/src/services/websocket/WebSocketManager.js:3` định nghĩa sáu delay `[1_000, 2_000, 5_000, 10_000, 20_000, 30_000]`.
- `VERIFIED-SOURCE` — `WebSocketManager.js:createClient` cấu hình STOMP `reconnectDelay=1_000`, sau đó thay đổi thuộc tính public này trong `onWebSocketClose` và đặt `0` khi hết retry.
- `VERIFIED-SOURCE` — dependency cài thực tế là `@stomp/stompjs@7.3.0`; `node_modules/@stomp/stompjs/esm6/client.js:400` chụp `reconnectDelay` vào `_nextReconnectDelay` lúc activate và `_schedule_reconnect` dùng biến nội bộ này.
- `VERIFIED-TEST` — reproduction bằng dependency thật nhận các public delay `1s, 2s, 5s, 10s, 20s, 30s, 0` nhưng vẫn lên lịch bảy timer `1s`; control đặt internal delay về `0` không tạo timer.
- `VERIFIED-TEST` — diagnostic test tạm với `WebSocketManager` và STOMP thật thất bại vì sau khi hết retry vẫn còn một timer; diagnostic thứ hai xác nhận timer đó chuyển UI từ `offline` về `reconnecting`. File diagnostic đã được xóa.
- `VERIFIED-TEST` — `npm test -- --run src/services/websocket/WebSocketManager.test.js` pass 3/3, chứng minh test hiện hành chưa bắt scheduler thật vì chỉ dùng client mock.
- `VERIFIED-SOURCE` — `wechatbackend/src/main/resources/application.properties:15` đặt access-token TTL mặc định `PT15M`; `JwtTokenService.validateAccessToken` từ chối token hết hạn và `WebSocketAuthChannelInterceptor.authenticateConnect` dùng validation này.
- `VERIFIED-SOURCE` — REST có `refreshAccessToken()` single-flight trong `wechatfrontend/src/services/api/client.js`, nhưng `WebSocketManager` chỉ gọi `getAccessToken()` đồng bộ trước CONNECT.
- `VERIFIED-SOURCE` — `AuthResponse.expiresIn` đã tồn tại; `tokenStorage.setSession()` hiện chưa lưu expiry.
- `VERIFIED-SOURCE` — Redis Pub/Sub là transient; `useChatPageController` tải lại messages đang mở khi state trở về `connected`, nhưng `realtimeStore` chưa dùng callback `onConnected` để đồng bộ conversation list và notifications.
- `VERIFIED-SOURCE` — backend giới hạn WebSocket CONNECT mặc định 20 lần/phút; retry lỗi mỗi giây có thể tự khuếch đại thành rate-limit failure.

### Test đã chạy trong SCOUT/DEBUGGING

- `PASS` — `npm test -- --run src/services/websocket/WebSocketManager.test.js src/features/auth/authStore.test.js`: 5/5.
- `PASS` — `mvn -q -Dtest=JwtTokenServiceTest,WebSocketAuthChannelInterceptorTest test`.
- `PASS` — `mvn -q -Dtest=WebSocketAuthChannelInterceptorTest,ProductionConfigTest test`.
- `PASS` — reproduction timer và state sequence nêu trên.
- `BLOCKED` — live Compose/browser: Compose thiếu các biến bắt buộc và phiên hiện tại không có quyền Docker socket; không đọc hoặc sửa `.env`.

### Ràng buộc worktree

Các thay đổi có trước và phải được giữ nguyên: `.agents/projects/wechat-clone/WORKFLOW.md`, `.gitignore`, `docs/deployment.md`, `docs/testing.md`, `docker/dev.env.example`, `docs/plans/2026/10/20/dev-compose-redis-minio.md`, và nội dung dưới `docs/plans/2026/10/24/`.

## 4. Thiết kế đề xuất và quyết định quan trọng

### 4.1 Một scheduler do ứng dụng sở hữu

`WebSocketManager` sẽ cấu hình STOMP với `reconnectDelay: 0`. Manager tự sở hữu:

- `reconnectTimer`;
- `reconnectAttempt`;
- `generation` hoặc session identifier;
- client hiện hành;
- promise/flag cho manual restart đang chạy.

Mỗi connection attempt tạo một STOMP `Client` mới. Callback đóng trên local `client` và kiểm tra client/generation còn hiện hành trước khi cập nhật state, subscription hoặc timer. Khi socket đóng ngoài ý muốn, manager giải phóng client cũ rồi lên lịch attempt kế tiếp theo đúng `BACKOFF_DELAYS`. Khi hết mảng delay, manager chuyển `offline` và không lập lịch nữa.

Lựa chọn này được ưu tiên hơn việc thay đổi private `_nextReconnectDelay` của thư viện vì không phụ thuộc implementation detail. Dùng built-in exponential reconnect cũng bị loại vì không giữ đúng chuỗi `1, 2, 5, 10, 20, 30` và không cung cấp giới hạn attempt rõ ràng.

### 4.2 State transition

State machine dự kiến:

```text
connect/retry → connecting
socket close + còn budget → reconnecting → attempt mới
CONNECT success → connected
refresh/token failure hoặc hết budget → offline
disconnect/logout/unmount → offline
```

`onStompError` không được tự tạo chuyển đổi `offline → reconnecting`. Nó chỉ ghi nhận failure cho attempt hiện tại và chủ động đóng/deactivate client nếu cần; state cuối do cùng một close/retry path quyết định.

### 4.3 Token freshness chỉ là quyết định phía client

`tokenStorage` lưu thêm thời điểm expiry trong memory từ `AuthResponse.expiresIn`. Không lưu access token hoặc expiry mới vào persistent storage. Một helper trong API client trả access token hiện tại nếu còn trên một khoảng an toàn nhỏ; nếu thiếu hoặc gần hết hạn thì dùng `refreshAccessToken()` hiện có.

WebSocketManager lấy token trước khi tạo/activate STOMP client thay vì dùng async `beforeConnect`. Điều này tránh rejected promise không được STOMP xử lý và bảo đảm mỗi attempt có token đã chuẩn bị xong. Backend vẫn là nguồn quyết định cuối cùng về token validity.

Nếu refresh thất bại, frontend clear session, phát `wechat:session-expired` theo behavior hiện có, hủy timer/client và chuyển `offline`; không tiếp tục spam CONNECT bằng token cũ.

### 4.4 Manual retry và stale callbacks

Manual retry sẽ:

1. bỏ qua hoặc tái sử dụng cùng restart promise nếu retry đang chạy;
2. tăng generation và hủy timer;
3. deactivate client hiện hành;
4. kiểm tra `desired`, options và generation sau `await`;
5. reset attempt rồi bắt đầu đúng một client mới.

Disconnect làm mất hiệu lực generation trước khi deactivate để callback muộn không thể thao tác client mới.

### 4.5 Resync sau reconnect

`onConnected` nhận metadata nội bộ như `{ reconnected: boolean }`. Chỉ khi `reconnected=true`:

- `useChatPageController` tiếp tục tải messages đang mở qua transition `connected` hiện có;
- `realtimeStore` yêu cầu `chatStore` tải lại conversation list bằng query gần nhất;
- `realtimeStore` yêu cầu `notificationStore` tải lại list và unread count;
- lỗi của từng resync được cô lập bằng `Promise.allSettled` hoặc các promise được catch riêng để một endpoint hỏng không làm rớt WebSocket vừa kết nối.

`chatStore` lưu query gần nhất trong memory để reconnect không biến kết quả tìm kiếm hiện tại thành danh sách không lọc.

## 5. File và component bị ảnh hưởng

| File/component | Trạng thái | Mục đích thay đổi |
|---|---|---|
| `wechatfrontend/src/services/websocket/WebSocketManager.js` | Hiện có | Thay auto-reconnect bằng manager-owned scheduler; state machine, generation guard, retry/disconnect an toàn, token preparation và reconnect metadata. |
| `wechatfrontend/src/services/websocket/WebSocketManager.test.js` | Hiện có | Regression tests cho exact backoff, stop condition, state sequence, stale callback, duplicate retry, token failure và subscription restore. |
| `wechatfrontend/src/services/auth/tokenStorage.js` | Hiện có | Theo dõi expiry trong memory và cung cấp kiểm tra token còn đủ hạn. |
| `wechatfrontend/src/services/auth/tokenStorage.test.js` | `NEW FILE PROPOSED` | Test expiry, clock boundary, set/clear session và refresh threshold. |
| `wechatfrontend/src/services/api/client.js` | Hiện có | Thêm helper lấy token usable, tái sử dụng single-flight refresh và thống nhất session-expired failure path. |
| `wechatfrontend/src/services/api/client.test.js` | `NEW FILE PROPOSED` | Test token còn hạn, gần hết hạn, concurrent refresh và refresh failure mà không lộ token. |
| `wechatfrontend/src/features/realtime/realtimeStore.js` | Hiện có | Truyền token provider mới và resync conversations/notifications sau reconnect. |
| `wechatfrontend/src/features/realtime/realtimeStore.test.js` | Hiện có nhưng rỗng | Test wiring connect/retry/disconnect, reconnect-only resync và failure isolation. |
| `wechatfrontend/src/features/conversations/chatStore.js` | Hiện có | Lưu query gần nhất để resync giữ đúng filter. |
| `wechatfrontend/src/features/conversations/chatStore.test.js` | Hiện có | Test query persistence và resync dùng query hiện hành. |
| `wechatfrontend/src/features/notifications/notificationStore.test.js` | Hiện có | Bổ sung test reload list/unread khi reconnect. |
| `wechatfrontend/src/features/conversations/useChatPageController.js` | Kiểm tra, dự kiến không đổi | Xác minh active conversation vẫn reload khi state trở lại `connected`. Chỉ sửa nếu test chứng minh flow hiện tại không chạy. |

Không dự kiến thay đổi file backend, migration, Compose, gateway, monitoring hoặc storage.

## 6. Các bước triển khai theo dependency

### P1 — Thêm regression tests tái hiện lỗi

- **Objective:** Chuyển reproduction debugging thành test bền vững và thất bại trên code hiện tại.
- **Files/symbols:** `WebSocketManager.test.js`; `tokenStorage.test.js` mới; `client.test.js` mới; `realtimeStore.test.js` hiện rỗng.
- **Existing or new:** Cả hiện có và file test mới.
- **Change intent:** Dùng fake timers và client factory quan sát được; kiểm tra exact timer sequence, không còn timer sau budget, `offline` không tự về `reconnecting`, duplicate retry chỉ có một client, stale callback bị bỏ qua, token refresh path và reconnect resync.
- **Depends on:** Không.
- **Can run in parallel with:** Không; đây là baseline bắt buộc trước production change.
- **Acceptance criteria covered:** AC-1 đến AC-6.
- **Verification:** Chạy riêng từng test mới và ghi `FAIL` đúng assertion trước khi sửa.
- **Risks/rollback:** Test không được dựa vào private field của STOMP; nếu test chỉ pass/fail theo implementation detail thì phải thiết kế lại trước P2.

### P2 — Bổ sung token expiry và usable-token provider

- **Objective:** Bảo đảm mỗi STOMP attempt dùng token còn hạn mà không refresh dư thừa.
- **Files/symbols:** `tokenStorage.js:setSession/clearSession`; helper expiry mới; `client.js:refreshAccessToken` và helper usable-token mới; test tương ứng.
- **Existing or new:** Sửa file hiện có và thêm hai test file.
- **Change intent:** Lưu expiry in-memory từ `expiresIn`, dùng safety window, tái sử dụng `refreshPromise`, clear/dispatch hết phiên một lần khi refresh thất bại.
- **Depends on:** P1.
- **Can run in parallel with:** Không, vì P3 dùng contract helper này.
- **Acceptance criteria covered:** AC-4.
- **Verification:** Unit tests với fake system time và deferred promise; auth store regression.
- **Risks/rollback:** Sai đơn vị giây/millisecond có thể refresh liên tục hoặc dùng token hết hạn; rollback riêng P2 trả token flow về hiện trạng nhưng P3 không được merge nếu mất provider.

### P3 — Viết lại reconnect lifecycle trong WebSocketManager

- **Objective:** Chỉ có một reconnect scheduler và một client generation hợp lệ.
- **Files/symbols:** `WebSocketManager.js:connect/createClient/retry/disconnect`; có thể đổi tên hoặc tách private methods nội bộ trong cùng file.
- **Existing or new:** Sửa file hiện có.
- **Change intent:** `reconnectDelay:0`; manager-owned timer theo `BACKOFF_DELAYS`; prepare token trước activate; local client callback + generation guard; bounded retry; restart promise; deterministic state transitions; subscription restore đúng một lần.
- **Depends on:** P1, P2.
- **Can run in parallel with:** Không.
- **Acceptance criteria covered:** AC-1 đến AC-5.
- **Verification:** Regression tests P1 phải chuyển từ `FAIL` sang `PASS`; kiểm tra fake timer count và exact delay order.
- **Risks/rollback:** Race async deactivate/token fetch; rollback toàn bộ P3 nếu có duplicate client hoặc callback muộn. Không sử dụng private STOMP fields.

### P4 — Resync dữ liệu bền sau reconnect

- **Objective:** Khôi phục phần state có thể bị mất trong thời gian Redis Pub/Sub gián đoạn.
- **Files/symbols:** `realtimeStore.js:connect`; `chatStore.js:loadConversations/reset`; test của realtime/chat/notification; xác minh `useChatPageController` connected effect.
- **Existing or new:** Sửa stores và tests hiện có.
- **Change intent:** Truyền `onConnected({reconnected})`; chỉ resync sau reconnect; giữ query hiện hành; reload notification list/unread; cô lập lỗi endpoint.
- **Depends on:** P3.
- **Can run in parallel with:** Không, vì contract callback do P3 cung cấp.
- **Acceptance criteria covered:** AC-6.
- **Verification:** Store tests; kiểm tra load đúng một lần trên reconnect và không duplicate trên initial connect.
- **Risks/rollback:** Request trùng với search debounce hoặc notification panel; request-id hiện có phải tiếp tục chặn stale conversation response. Có thể rollback P4 độc lập mà không làm mất fix state AC-1/AC-2.

### P5 — Focused integration và frontend quality gate

- **Objective:** Xác minh không có regression auth/chat/UI và build.
- **Files/symbols:** Toàn bộ file đổi ở P1–P4.
- **Existing or new:** Không thêm behavior mới ngoài sửa test nếu test setup sai; không nới assertion.
- **Change intent:** Chạy focused suite rồi full frontend unit suite, lint, build và diff check.
- **Depends on:** P2, P3, P4.
- **Can run in parallel with:** Một số command có thể chạy song song sau khi code ổn định.
- **Acceptance criteria covered:** AC-7, AC-8.
- **Verification:** Các command trong mục 8.
- **Risks/rollback:** Nếu test phát hiện thay đổi contract backend hoặc cần schema, dừng COOK và trở lại PLAN.

### P6 — Runtime/browser verification và review

- **Objective:** Quan sát status badge và dữ liệu resync trên runtime thật khi hạ/khôi phục mạng hoặc backend.
- **Files/symbols:** Không sửa source trong TEST/REVIEW; dùng browser, gateway `/ws`, logs an toàn và test evidence.
- **Existing or new:** N/A.
- **Change intent:** Xác minh state sequence, số connection attempt và resync; sau đó review độc lập diff cuối.
- **Depends on:** P5; runtime và tài khoản test.
- **Can run in parallel with:** Không; REVIEW nhận TEST report hoàn chỉnh.
- **Acceptance criteria covered:** AC-1 đến AC-8.
- **Verification:** Browser/network observation, focused authenticated test nếu được cấp prerequisites; `continuous-improvement-review` sau TEST.
- **Risks/rollback:** Runtime hiện `BLOCKED`; không được sửa `.env`, start/stop service hoặc gửi message nếu chưa có authorization riêng.

### Dependency table

| ID | Task | Depends on | Parallel group | Blocking reason |
|---|---|---|---|---|
| P1 | Regression tests | — | G1 | Baseline phải fail trước production change. |
| P2 | Token freshness | P1 | G2 | P3 phụ thuộc token provider đã test. |
| P3 | Reconnect lifecycle | P1, P2 | G3 | Là contract trung tâm của state/retry. |
| P4 | Reconnect resync | P3 | G4 | Cần metadata reconnect ổn định. |
| P5 | Quality gate | P2, P3, P4 | G5 | Chạy sau khi diff hoàn chỉnh. |
| P6 | Runtime + review | P5 | G6 | Cần code/test hoàn tất và runtime prerequisites. |

## 7. Specialist routing và approval gates

## Specialist routing

- **Affected layer/component:** Frontend STOMP lifecycle và connection state.
- **Evidence from SCOUT:** `wechatfrontend/src/services/websocket/WebSocketManager.js:createClient`, `retry`, `disconnect`; diagnostic timer/state reproduction.
- **Selected skill:** `realtime-engineering` cùng compact guide `wechat-realtime-contract`.
- **Purpose and stage:** COOK — triển khai bounded reconnect, state machine, generation guard và subscription restore.
- **Dependencies:** P1 → P2 → P3.
- **Verification skill and evidence:** `testing`, `frontend-quality-gate`; fake-timer regression và focused frontend suite.
- **Relevant skill considered but not selected:** `spring-boot-engineering`.
- **Reason for exclusion:** Backend STOMP endpoint và authorization không đổi.

## Specialist routing

- **Affected layer/component:** Frontend authentication/token integration.
- **Evidence from SCOUT:** `tokenStorage.js:setSession`; `client.js:refreshAccessToken`; `WebSocketManager.js` hiện chỉ đọc token đồng bộ; backend trả `expiresIn`.
- **Selected skill:** `frontend-engineering` cùng compact guide `wechat-frontend-integration`.
- **Purpose and stage:** COOK — expiry in-memory, usable-token helper và wiring trước CONNECT.
- **Dependencies:** P1 → P2 → P3.
- **Verification skill and evidence:** `frontend-integration-review`, `testing`; unit tests về expiry, single-flight refresh và session expiration.
- **Relevant skill considered but not selected:** `backend-security`, `api-contract-design`.
- **Reason for exclusion:** Không thay đổi JWT validation, refresh endpoint, DTO hoặc authorization; chỉ dùng contract `expiresIn` hiện có.

## Specialist routing

- **Affected layer/component:** Zustand resync cho conversations và notifications.
- **Evidence from SCOUT:** `realtimeStore.js:connect`; `useChatPageController.js` chỉ reload active messages; Redis Pub/Sub không replay.
- **Selected skill:** `frontend-engineering` cùng `wechat-frontend-integration`.
- **Purpose and stage:** COOK — reconnect-only resync, query preservation và failure isolation.
- **Dependencies:** P3 → P4.
- **Verification skill and evidence:** `testing`, `frontend-quality-gate`; realtime/chat/notification store tests.
- **Relevant skill considered but not selected:** `data-pipeline-engineering`, `database-migration`.
- **Reason for exclusion:** Resync dùng REST/data hiện có; không có pipeline hoặc schema change.

## Specialist routing

- **Affected layer/component:** Runtime/browser evidence.
- **Evidence from SCOUT:** Compose/runtime hiện bị chặn bởi env bắt buộc và Docker socket; gateway WebSocket config source hợp lệ nhưng chưa có live evidence.
- **Selected skill:** `wechat-runtime-verification` ở TEST nếu prerequisites được cung cấp.
- **Purpose and stage:** TEST — xác minh badge, retry cadence, reconnect và resync trên runtime thật.
- **Dependencies:** P5 và authorization/runtime credentials.
- **Verification skill and evidence:** Browser/network observation và runtime report PASS/FAIL/BLOCKED.
- **Relevant skill considered but not selected:** `deployment-operations`.
- **Reason for exclusion:** Kế hoạch không thay đổi Compose, gateway hoặc deployment.

### Các lớp được xem xét nhưng không bị ảnh hưởng

- **Backend REST/STOMP authorization:** Không đổi; chỉ chạy regression test nếu cần xác nhận contract.
- **Database/Flyway/transactions:** Không đổi schema, query hoặc transaction.
- **Redis bridge:** Không đổi publish/listener/dedup; chỉ bù loss bằng REST resync.
- **Attachment/storage:** Không nằm trên connection-state path.
- **Infrastructure/gateway:** Không đổi `/ws`, timeout hoặc origin configuration.

### Approval gates

1. **Gate PLAN:** Người dùng phải phê duyệt toàn bộ plan này trước COOK.
2. **Gate scope deviation:** Nếu P2–P4 đòi đổi backend API, schema, STOMP destination, Redis hoặc deployment, dừng và trở lại PLAN.
3. **Gate Git:** Không tạo/switch branch, commit, push, rebase hoặc merge nếu chưa được yêu cầu rõ ràng.
4. **Gate runtime:** Không đọc/sửa `.env`, không start/stop/restart service, không dùng credentials hoặc gửi application message nếu chưa có authorization riêng.
5. **Gate REVIEW:** Sau TEST phải có independent REVIEW; finding High/Critical quay lại COOK hoặc PLAN theo workflow.

## 8. Chiến lược testing và validation

### Verification matrix

| ID | Criterion | Verification method | Required environment | Expected evidence |
|---|---|---|---|---|
| AC-1 | Exact bounded backoff | Vitest fake timers, quan sát delay order và timer count | Node/Vitest local | Sáu timer đúng thứ tự; sau failure cuối timer count bằng 0. |
| AC-2 | State ổn định | Unit test callback sequence | Node/Vitest local | Không có `offline → reconnecting` nếu không gọi retry. |
| AC-3 | Một lifecycle owner | Client factory spy, fake timers, source review | Node/Vitest local | STOMP `reconnectDelay=0`; chỉ manager timer tạo attempt. |
| AC-4 | Token usable | Fake time, deferred refresh promise, refresh failure test | Node/Vitest local | Token còn hạn không refresh; gần hạn refresh một lần; failure expire session và dừng. |
| AC-5 | Không race | Rapid retry, disconnect-during-refresh, stale callback tests | Node/Vitest local | Một active client; callback cũ không đổi state/subscription. |
| AC-6 | Resync | Store tests và browser reconnect khi khả dụng | Unit local; runtime optional | Messages hiện hành, query hiện tại, conversations và notifications được reload đúng một lần. |
| AC-7 | Không đổi server contract | `git diff`, backend-focused regression nếu frontend assumptions thay đổi | Local Maven | Không có backend/migration/config diff; STOMP auth tests pass nếu chạy. |
| AC-8 | Quality gate | Focused suite, full related suite, lint, build, diff check; browser runtime | Local; runtime nếu có | Tất cả local checks PASS; browser PASS hoặc BLOCKED có lý do. |

### Planned commands — chưa chạy trong PLAN

```bash
cd wechatfrontend
npm test -- --run src/services/websocket/WebSocketManager.test.js src/services/auth/tokenStorage.test.js src/services/api/client.test.js src/features/realtime/realtimeStore.test.js src/features/conversations/chatStore.test.js src/features/notifications/notificationStore.test.js src/features/auth/authStore.test.js
npm test
npm run lint
npm run build
cd ..
git diff --check
```

Backend regression chỉ chạy nếu COOK chạm assumptions xác thực hoặc frontend integration review yêu cầu:

```bash
cd wechatbackend
mvn -q -Dtest=JwtTokenServiceTest,WebSocketAuthChannelInterceptorTest test
```

Runtime/browser test hiện `BLOCKED` cho đến khi có runtime Compose hợp lệ, quyền Docker và tài khoản test. Test bị skip hoặc blocked không được tính là pass.

## 9. Security, compatibility, data migration và vận hành

- **Security:** Access token vẫn chỉ ở memory; refresh token vẫn ở `sessionStorage` theo thiết kế hiện tại. Không log hoặc assert raw token trong output. Backend tiếp tục quyết định token validity và STOMP authorization.
- **Compatibility:** Không đổi REST/STOMP public contract. `onConnected` là callback nội bộ; mọi caller trong repo phải được cập nhật đồng bộ.
- **Data integrity:** Không ghi dữ liệu mới ngoài các REST reload hiện có; không migration/backfill.
- **Concurrency:** `refreshPromise`, generation guard và restart promise phải loại duplicate refresh/client. Tests phải bao phủ disconnect trong lúc token refresh đang pending.
- **Performance:** Tối đa sáu retry có backoff; resync chỉ chạy sau reconnect, không phải initial connect. `Promise.allSettled` tránh retry transport vì một REST endpoint lỗi.
- **Rate limit:** Exact backoff giảm CONNECT burst và tránh tự đẩy client vào giới hạn 20/phút.
- **Observability:** Không thêm backend metric trong scope nhỏ này. Frontend production debug vẫn không in token; TEST dùng state/timer spy. Nếu thiếu runtime visibility gây blocker, quay lại PLAN trước khi thêm telemetry.
- **Deployment:** Frontend-only artifact; không yêu cầu thứ tự migration/backend deploy.
- **Health/readiness:** Không thay đổi. Badge không được suy diễn thành backend readiness hoặc Internet connectivity toàn cục.

## 10. Rủi ro, phương án khác, giả định, câu hỏi và rollback

### Rủi ro

- Async token refresh hoàn thành sau logout có thể mở lại kết nối nếu thiếu generation guard.
- React StrictMode có thể gọi cleanup/setup sát nhau; manager phải vô hiệu hóa callback cũ trước khi await deactivate.
- Resync conversation query có thể bị search request mới hơn ghi đè; tiếp tục dùng `conversationRequestId` hiện có.
- Notification reload có thể trùng thao tác panel; state merge/replace phải giữ contract hiện có.
- Nếu server gửi STOMP ERROR nhưng không đóng socket, manager cần chủ động terminate attempt để đi vào một close path thống nhất.

### Phương án khác đã loại

1. **Gán private `_nextReconnectDelay`:** diff nhỏ nhưng phụ thuộc nội bộ STOMP và có thể vỡ khi nâng dependency.
2. **Dùng STOMP exponential reconnect:** tốt hơn retry 1s cố định nhưng không giữ exact business sequence và không tự giới hạn số attempt.
3. **Chỉ sửa badge debounce:** che symptom, không dừng connection storm hoặc token lỗi.
4. **Refresh token trên mọi reconnect:** đơn giản nhưng xoay refresh token quá thường xuyên và tăng tải/rate-limit không cần thiết.

### Giả định

- `ASSUMPTION` — `expiresIn` luôn là số giây dương trong login/refresh response; backend source hiện xác nhận điều này.
- `ASSUMPTION` — reload notification list khi reconnect là chấp nhận được về tải; hiện endpoint đã được dùng khi mở panel.
- `ASSUMPTION` — không yêu cầu replay typing indicators vì đây là transient state và store đã tự hết hạn sau 4,5 giây.

### Câu hỏi chưa giải quyết

- Không có câu hỏi business bắt buộc trước COOK. Safety window của token là quyết định kỹ thuật; đề xuất 30 giây và test tại boundary.
- Runtime verification vẫn phụ thuộc quyền và credentials; việc thiếu runtime không cho phép tự ý thay đổi Compose hoặc `.env`.

### Rollback

- Revert coherent frontend diff của task này; không có schema/data rollback.
- Nếu P4 gây tải hoặc UX regression, có thể rollback riêng resync while giữ P2/P3 connection fix.
- Nếu P2 token freshness gây session regression, dừng rollout và rollback P2/P3 cùng nhau vì P3 phụ thuộc provider; REST interceptor hiện hành vẫn là fallback cũ.
- Không xóa cache, database row, Redis data, volume hoặc storage object.

## 11. Definition of Done

- Regression test tái hiện lỗi cũ đã được ghi nhận fail trước fix và pass sau fix.
- AC-1 đến AC-7 đều có test/local evidence PASS.
- Không còn timer/client sau retry budget hoặc disconnect.
- Không có state transition `offline → reconnecting` ngoài manual retry/connect mới.
- Token gần hết hạn được refresh single-flight trước CONNECT; failure kết thúc an toàn.
- Callback client cũ không ảnh hưởng client mới.
- Reconnect resync messages/conversations/notifications đúng query và không duplicate initial load.
- Không có backend, migration, Compose, gateway hoặc storage diff.
- Focused tests, related suite, lint, build và `git diff --check` PASS.
- Runtime/browser criterion PASS hoặc được ghi `BLOCKED` trung thực với prerequisites cụ thể.
- REVIEW không còn finding Critical/High mở.
- Mọi file tạm/diagnostic đã được xóa; không có token hoặc secret trong output/diff.

## Manual handoff

**A — Approve plan:** phê duyệt toàn bộ bốn deliverable PLAN để bắt đầu COOK theo P1 → P6.

**B — Revise plan:** nêu phần cần đổi, đặc biệt scope token refresh hoặc resync.

**C — Return to analysis:** quay lại `scout`/`ba-analysis` nếu cần thêm yêu cầu hoặc runtime evidence trước implementation.

**D — Stop:** kết thúc tại plan, không sửa application source.

**Khuyến nghị:** A — phê duyệt plan và bắt đầu COOK; nguyên nhân đã được xác nhận, phạm vi không cần đổi backend/schema và có regression path đo được.
