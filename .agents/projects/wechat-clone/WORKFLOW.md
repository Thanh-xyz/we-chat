# WeChat Clone — Agent Workflow

Codex discovers project agents from `.codex/agents/*.toml` and repo skills from `.agents/skills/*/SKILL.md`. The Agency catalog under `.agents/engineering/`, `.agents/security/`, and `.agents/integrations/codex/` remains optional reference material; its generated definitions are not automatically active in this checkout.

## Operating principle

Dùng `agency-agents` như một nhóm chuyên gia có phân vai, nhưng giữ một luồng thực thi duy nhất và có bằng chứng. Mỗi task phải có một owner rõ ràng, một reviewer phù hợp và một Definition of Done đo được.

Không kích hoạt nhiều agent chỉ để tạo thêm ý kiến. Chọn agent theo loại rủi ro của task.

## Agent roster cho project này

| Need | Project agent | Skill |
|---|---|---|
| Hiểu repository và trace flow | built-in `explorer` or `wechat_project_lead` | `wechat-flow-trace` |
| Spring REST, JDBC, PostgreSQL, Flyway | `wechat_project_lead` | `wechat-spring-data-change` |
| WebSocket, Redis Pub/Sub, realtime | `wechat_project_lead` | `wechat-realtime-contract` |
| Storage upload/download, legacy migration boundary | `wechat_project_lead` | `wechat-storage-acceptance` |
| Frontend API and STOMP integration | `wechat_project_lead` | `wechat-frontend-integration` |
| Compose and runtime evidence | `wechat_project_lead` | `wechat-runtime-verification` |
| Independent scoped review | `wechat_code_reviewer` (read-only by instructions; inherits parent sandbox) | matching domain skill |

## Sandbox and agent permissions

Codex subagents inherit the parent session's sandbox and approval settings. Agent TOML instructions do not create a separate read-only boundary, and `sandbox_mode` is not a per-agent override.

- Scout and reviewer instructions prohibit edits, but filesystem permissions still come from the parent session.
- For filesystem-enforced read-only work, start the parent Codex session in read-only mode before spawning the agent. In the CLI, a separate session can be started with `codex exec --sandbox read-only -C <repo> "<task>"`.
- When the main session is writable and an independent read-only review is required, use a separate Codex session with the read-only sandbox. If that is unavailable, describe the role as instruction-only read-only and do not claim sandbox enforcement.

## Standard task lifecycle

### Gate 0 — Load context

Đọc:

1. `.agents/projects/wechat-clone/PROJECT-CONTEXT.md` when broader context is useful
2. current user task; historical prompts are reference material unless explicitly activated
3. các file được prompt yêu cầu
4. `git status` và các thay đổi đang có

Không overwrite hoặc revert thay đổi có sẵn của user.

### Gate 1 — Inspect the real application repository

Agent phải xác định:

- application repository nằm ở đâu;
- backend/frontend boundaries;
- startup/build/test commands;
- routes/controllers/services/repositories liên quan;
- Docker/Compose service names và replica model;
- các file test và docs đã được đọc.

Nếu đang ở `agency-agents` nhưng không có source WeChat, không được giả vờ đã kiểm tra implementation. Hãy tạo prompt/context hoặc báo cần application repository để thực thi.

### Gate 2 — Convert request into a task contract

Mỗi task phải ghi rõ:

```text
Task ID:
Objective:
In scope:
Out of scope:
Preconditions:
Files/components to inspect:
Acceptance criteria:
Evidence required:
Safety constraints:
```

Nếu endpoint, DTO, service name hoặc status chưa biết, agent phải tìm trong source trước khi dùng.

### Gate 3 — Baseline before mutation

Trước khi sửa code:

- chạy test/health check nhỏ nhất có thể;
- lưu kết quả pass/fail/skipped;
- xác định runtime state;
- kiểm tra `git status` và diff hiện tại;
- không đưa secret vào evidence.

### Gate 4 — Minimal implementation

Chỉ sửa khi có failure hoặc yêu cầu rõ ràng. Mỗi thay đổi phải có:

1. symptom;
2. root cause;
3. minimal fix;
4. regression test;
5. test rerun.

Không refactor hoặc đổi architecture chỉ vì agent thích một thiết kế khác.

### Gate 5 — Verification

Chạy test trực tiếp trước, rồi regression suite có liên quan. Phân loại kết quả:

- `PASS`: có output/evidence trực tiếp;
- `FAIL`: code/test chạy nhưng sai;
- `BLOCKED`: dependency hoặc runtime không thể dùng an toàn.

Một test bị skip không tự động là pass.

### Gate 6 — Handoff/report

Final report phải bắt đầu bằng verdict và sau đó nêu:

- những gì đã kiểm tra;
- những gì thực sự thay đổi;
- command/test output quan trọng;
- evidence cho mỗi acceptance criterion;
- limitation hoặc blocker;
- bước tiếp theo nhỏ nhất.

## R16 execution pattern

Khi task là R16, dùng thứ tự:

```text
Code audit
  → Compose baseline
  → identify replica A/B
  → User A upload
  → Supabase Storage + PostgreSQL verification
  → User A download via B
  → stop A, download via B
  → User B authorization denial
  → invalid-file validation
  → S3 storage failure behavior
  → frontend/backend regression
  → final evidence report
```

R16 chỉ đạt khi cross-instance bytes, authorization và instance independence đều có bằng chứng.

## Prompt quality gate

Trước khi dùng một prompt mới:

- có version và ngày cập nhật;
- có output format;
- có Definition of Done;
- có ít nhất một happy path, một edge case và một blocker/failure case;
- không dùng các từ mơ hồ như “kiểm tra kỹ”, “tối ưu tốt”, “đảm bảo production-ready” nếu không có tiêu chí đo được;
- không yêu cầu agent giả định endpoint, secret, runtime state hoặc test result.

## Handoff format

Dùng format ngắn sau giữa các agent:

```markdown
# Task Handoff

## Task
- ID:
- Owner:
- Reviewer:
- Scope:

## Evidence so far
- [PASS/FAIL/BLOCKED] criterion — evidence

## Files inspected/changed
- `path/to/file` — reason

## Remaining work
- concrete next action

## Risks/blockers
- concrete blocker or `none`
```
